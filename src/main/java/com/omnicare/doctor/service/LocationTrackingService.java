package com.omnicare.doctor.service;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.DoctorLocationUpdate;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.repository.DoctorLocationUpdateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LocationTrackingService {

    private static final Logger log = LoggerFactory.getLogger(LocationTrackingService.class);
    private static final double SIGNIFICANT_MOVEMENT_THRESHOLD_METERS = 200.0;
    private static final double GPS_ERROR_THRESHOLD_METERS = 500.0;
    private static final int ETA_RECALCULATION_MIN_INTERVAL_SECONDS = 30;
    private static final int SPEED_AVERAGE_WINDOW_MINUTES = 3;

    private final DoctorLocationUpdateRepository locationRepository;
    private final ConsultationRepository consultationRepository;
    private final RealtimeLocationBroadcaster realtimeBroadcaster;

    public LocationTrackingService(
            DoctorLocationUpdateRepository locationRepository,
            ConsultationRepository consultationRepository,
            RealtimeLocationBroadcaster realtimeBroadcaster) {
        this.locationRepository = locationRepository;
        this.consultationRepository = consultationRepository;
        this.realtimeBroadcaster = realtimeBroadcaster;
    }

    // Inner class for location update messages
    public record LocationUpdateMessage(
            String consultationId,
            String doctorId,
            String doctorName,
            Double latitude,
            Double longitude,
            Long minutesRemaining,
            Double distanceMeters,
            Double speed,
            boolean etaRecalculated,
            String reason,
            Instant timestamp) {
    }

    @Transactional
    public LocationUpdateResult processLocationUpdate(UUID consultationId, UUID doctorId,
            LocationUpdateRequest request) {
        // Validate consultation exists and doctor is assigned
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

        if (consultation.getDoctor() == null || !consultation.getDoctor().getId().equals(doctorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor not assigned to this consultation");
        }

        // Get last location for distance calculation
        Optional<DoctorLocationUpdate> lastLocationOpt = locationRepository
                .findFirstByConsultationIdOrderByTimestampDesc(consultationId);

        // Create new location update
        DoctorLocationUpdate update = new DoctorLocationUpdate();
        update.setConsultationId(consultationId);
        update.setDoctorId(doctorId);
        update.setLatitude(request.latitude());
        update.setLongitude(request.longitude());
        update.setAccuracy(request.accuracy());
        update.setSpeed(request.speed());
        update.setTimestamp(request.timestamp() != null ? request.timestamp() : Instant.now());

        // Calculate distance from previous location
        Double distanceMoved = null;
        if (lastLocationOpt.isPresent()) {
            DoctorLocationUpdate lastLocation = lastLocationOpt.get();
            distanceMoved = calculateDistance(
                    lastLocation.getLatitude(), lastLocation.getLongitude(),
                    request.latitude(), request.longitude());
            update.setDistanceFromPrevious(distanceMoved);

            // Filter out GPS errors (jumps > 500m)
            if (distanceMoved > GPS_ERROR_THRESHOLD_METERS) {
                log.warn("GPS error detected for consultation {}: jump of {}m from ({}, {}) to ({}, {})",
                        consultationId, distanceMoved,
                        lastLocation.getLatitude(), lastLocation.getLongitude(),
                        request.latitude(), request.longitude());
                // Store but don't recalculate ETA
                DoctorLocationUpdate saved = locationRepository.save(update);
                return new LocationUpdateResult(saved, false, null, null, "gps_error_filtered");
            }
        }

        // Determine if ETA should be recalculated
        boolean shouldRecalculateEta = shouldRecalculateEta(consultationId, distanceMoved);
        ETACalculationResult etaResult = null;

        if (shouldRecalculateEta) {
            etaResult = recalculateETA(consultation, update);
            update.setEtaRecalculated(true);
        }

        DoctorLocationUpdate saved = locationRepository.save(update);

        // Broadcast to patient via WebSocket
        broadcastLocationUpdate(consultation, saved, etaResult);

        return new LocationUpdateResult(
                saved,
                shouldRecalculateEta,
                etaResult != null ? etaResult.minutesRemaining() : null,
                etaResult != null ? etaResult.distanceMeters() : null,
                shouldRecalculateEta ? "significant_movement" : "minor_movement");
    }

    private boolean shouldRecalculateEta(UUID consultationId, Double distanceMoved) {
        if (distanceMoved == null || distanceMoved <= SIGNIFICANT_MOVEMENT_THRESHOLD_METERS) {
            return false;
        }

        // Check if enough time has passed since last ETA recalculation
        List<DoctorLocationUpdate> recentRecalculations = locationRepository
                .findEtaRecalculatedByConsultationId(consultationId);
        if (!recentRecalculations.isEmpty()) {
            Instant lastRecalculation = recentRecalculations.get(0).getTimestamp();
            long secondsSinceLastRecalculation = Duration.between(lastRecalculation, Instant.now()).getSeconds();
            if (secondsSinceLastRecalculation < ETA_RECALCULATION_MIN_INTERVAL_SECONDS) {
                return false;
            }
        }

        return true;
    }

    private ETACalculationResult recalculateETA(Consultation consultation, DoctorLocationUpdate currentLocation) {
        Double patientLat = consultation.getLatitude();
        Double patientLng = consultation.getLongitude();

        if (patientLat == null || patientLng == null) {
            return null;
        }

        // Calculate remaining distance to patient
        double remainingDistance = calculateDistance(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                patientLat, patientLng);

        // Calculate average speed from recent updates or use current speed
        Double averageSpeed = calculateAverageSpeed(consultation.getId());
        Double speed = averageSpeed != null ? averageSpeed : currentLocation.getSpeed();

        // Default speed if no data available (assume ~30 km/h = 8.33 m/s in urban area)
        if (speed == null || speed < 1.0) {
            speed = 8.33;
        }

        // Calculate ETA in minutes
        long minutesRemaining = Math.round(remainingDistance / speed / 60.0);
        if (minutesRemaining < 1) {
            minutesRemaining = 1;
        }

        return new ETACalculationResult(minutesRemaining, remainingDistance, speed);
    }

    private Double calculateAverageSpeed(UUID consultationId) {
        Instant since = Instant.now().minus(Duration.ofMinutes(SPEED_AVERAGE_WINDOW_MINUTES));
        return locationRepository.findAverageSpeedSince(consultationId, since);
    }

    private void broadcastLocationUpdate(Consultation consultation, DoctorLocationUpdate update,
            ETACalculationResult etaResult) {
        try {
            realtimeBroadcaster.broadcastToPatient(
                    consultation.getPatient().getId().toString(),
                    new LocationTrackingService.LocationUpdateMessage(
                            consultation.getId().toString(),
                            consultation.getDoctor().getId().toString(),
                            consultation.getDoctor().getProvider().getUser().getName(),
                            update.getLatitude(),
                            update.getLongitude(),
                            etaResult != null ? etaResult.minutesRemaining() : null,
                            etaResult != null ? etaResult.distanceMeters() : null,
                            update.getSpeed(),
                            etaResult != null,
                            etaResult != null ? "significant_movement" : "minor_movement",
                            update.getTimestamp()));
        } catch (Exception e) {
            log.error("Failed to broadcast location update for consultation {}", consultation.getId(), e);
        }
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    public List<DoctorLocationUpdate> getLocationHistory(UUID consultationId, int limit) {
        return locationRepository.findByConsultationIdOrderByTimestampDesc(consultationId)
                .stream()
                .limit(limit)
                .toList();
    }

    // Record classes
    public record LocationUpdateRequest(
            Double latitude,
            Double longitude,
            Double accuracy,
            Double speed,
            Instant timestamp) {
    }

    public record LocationUpdateResult(
            DoctorLocationUpdate update,
            boolean etaRecalculated,
            Long minutesRemaining,
            Double distanceMeters,
            String reason) {
    }

    private record ETACalculationResult(
            long minutesRemaining,
            double distanceMeters,
            double averageSpeed) {
    }
}
