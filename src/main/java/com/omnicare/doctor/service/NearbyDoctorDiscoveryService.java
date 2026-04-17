package com.omnicare.doctor.service;

import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class NearbyDoctorDiscoveryService {

    private final DoctorRepository doctorRepository;

    public NearbyDoctorDiscoveryService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    public record NearbyDoctorMatch(
            UUID doctorId,
            UUID providerId,
            UUID userId,
            String name,
            String specialty,
            Integer experienceYears,
            BigDecimal rating,
            Integer totalReviews,
            Integer serviceRadiusKm,
            boolean isOnline,
            Double providerLatitude,
            Double providerLongitude,
            Double distanceKm,
            String bio,
            java.math.BigDecimal visitPrice,
            String profilePhotoUrl) {
    }

    @Transactional(readOnly = true)
    public List<NearbyDoctorMatch> findNearbyDoctors(double patientLat, double patientLng, String specialty) {
        final String normalizedSpecialty = specialty == null ? null : specialty.trim().toLowerCase();

        return doctorRepository.findAllByProviderTypeAndProviderOnlineTrue(ProviderType.DOCTOR).stream()
                .filter(doctor -> matchesSpecialty(doctor, normalizedSpecialty))
                .map(doctor -> toMatch(doctor, patientLat, patientLng))
                .filter(match -> match != null && withinServiceRadius(match))
                .sorted(
                        Comparator.comparing(NearbyDoctorMatch::distanceKm, Comparator.nullsLast(Double::compareTo))
                                .thenComparing(NearbyDoctorMatch::rating,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private NearbyDoctorMatch toMatch(Doctor doctor, double patientLat, double patientLng) {
        if (doctor == null || doctor.getProvider() == null || doctor.getProvider().getUser() == null) {
            return null;
        }

        Provider provider = doctor.getProvider();
        if (!provider.isOnline() || provider.getServiceRadiusKm() == null || provider.getServiceRadiusKm() <= 0) {
            return null;
        }
        if (provider.getLatitude() == null || provider.getLongitude() == null) {
            return null;
        }

        double distanceKm = haversineKm(patientLat, patientLng, provider.getLatitude(), provider.getLongitude());
        return new NearbyDoctorMatch(
                doctor.getId(),
                provider.getId(),
                provider.getUser().getId(),
                provider.getUser().getName(),
                doctor.getSpecialty(),
                doctor.getExperienceYears(),
                provider.getRating(),
                provider.getTotalReviews(),
                provider.getServiceRadiusKm(),
                provider.isOnline(),
                provider.getLatitude(),
                provider.getLongitude(),
                BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                doctor.getBio(),
                doctor.getVisitPrice(),
                doctor.getProfilePhotoUrl());
    }

    private static boolean withinServiceRadius(NearbyDoctorMatch match) {
        if (match == null || match.distanceKm() == null || match.serviceRadiusKm() == null) {
            return false;
        }
        return match.distanceKm() <= match.serviceRadiusKm();
    }

    private static boolean matchesSpecialty(Doctor doctor, String normalizedSpecialty) {
        if (normalizedSpecialty == null || normalizedSpecialty.isBlank()) {
            return true;
        }
        String specialty = doctor == null ? null : doctor.getSpecialty();
        return specialty != null && specialty.toLowerCase().contains(normalizedSpecialty);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0088;
        final double dLat = Math.toRadians(lat2 - lat1);
        final double dLon = Math.toRadians(lon2 - lon1);
        final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
