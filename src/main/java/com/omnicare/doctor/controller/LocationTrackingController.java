package com.omnicare.doctor.controller;

import com.omnicare.doctor.service.LocationTrackingService;
import com.omnicare.doctor.service.DoctorService;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/consultations/{consultationId}/location")
public class LocationTrackingController {

    private static final Logger log = LoggerFactory.getLogger(LocationTrackingController.class);

    private final LocationTrackingService locationTrackingService;
    private final DoctorService doctorService;
    private final UserRepository userRepository;

    public LocationTrackingController(
            LocationTrackingService locationTrackingService,
            DoctorService doctorService,
            UserRepository userRepository) {
        this.locationTrackingService = locationTrackingService;
        this.doctorService = doctorService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<LocationUpdateResponse> updateLocation(
            Authentication authentication,
            @PathVariable("consultationId") UUID consultationId,
            @RequestBody LocationUpdateRequest request) {

        User actor = requireUser(authentication);

        // Only doctors can update their location
        if (actor.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor role required");
        }

        UUID doctorId = doctorService.ensureForDoctorUser(actor).getId();

        log.info("Location update received for consultation {} from doctor {}", consultationId, doctorId);

        LocationTrackingService.LocationUpdateResult result = locationTrackingService.processLocationUpdate(
                consultationId,
                doctorId,
                new LocationTrackingService.LocationUpdateRequest(
                        request.latitude(),
                        request.longitude(),
                        request.accuracy(),
                        request.speed(),
                        request.timestamp() != null ? request.timestamp() : Instant.now()));

        return ResponseEntity.ok(new LocationUpdateResponse(
                result.update().getId().toString(),
                result.etaRecalculated(),
                result.minutesRemaining(),
                result.distanceMeters(),
                result.reason(),
                result.update().getTimestamp()));
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<List<LocationHistoryResponse>> getLocationHistory(
            Authentication authentication,
            @PathVariable("consultationId") UUID consultationId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        User actor = requireUser(authentication);

        // Both doctors and patients can view location history
        if (actor.getRole() != UserRole.DOCTOR && actor.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor or Patient role required");
        }

        List<LocationHistoryResponse> history = locationTrackingService.getLocationHistory(consultationId, limit)
                .stream()
                .map(update -> new LocationHistoryResponse(
                        update.getLatitude(),
                        update.getLongitude(),
                        update.getAccuracy(),
                        update.getSpeed(),
                        update.getDistanceFromPrevious(),
                        update.getEtaRecalculated(),
                        update.getTimestamp()))
                .toList();

        return ResponseEntity.ok(history);
    }

    private User requireUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }

    // Record classes for requests and responses
    public record LocationUpdateRequest(
            Double latitude,
            Double longitude,
            Double accuracy,
            Double speed,
            Instant timestamp) {
    }

    public record LocationUpdateResponse(
            String updateId,
            boolean etaRecalculated,
            Long minutesRemaining,
            Double distanceMeters,
            String reason,
            Instant timestamp) {
    }

    public record LocationHistoryResponse(
            Double latitude,
            Double longitude,
            Double accuracy,
            Double speed,
            Double distanceFromPrevious,
            Boolean etaRecalculated,
            Instant timestamp) {
    }
}
