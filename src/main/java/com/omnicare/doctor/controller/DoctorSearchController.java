package com.omnicare.doctor.controller;

import com.omnicare.doctor.service.NearbyDoctorDiscoveryService;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
public class DoctorSearchController {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final NearbyDoctorDiscoveryService nearbyDoctorDiscoveryService;

    public DoctorSearchController(
            UserRepository userRepository,
            DoctorRepository doctorRepository,
            NearbyDoctorDiscoveryService nearbyDoctorDiscoveryService) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.nearbyDoctorDiscoveryService = nearbyDoctorDiscoveryService;
    }

    public record DoctorSearchResult(
            UUID doctorId,
            UUID userId,
            String name,
            String specialty,
            Integer experienceYears,
            BigDecimal rating,
            Integer serviceRadiusKm,
            boolean isOnline) {
        static DoctorSearchResult from(Doctor d) {
            if (d == null || d.getProvider() == null || d.getProvider().getUser() == null) {
                return new DoctorSearchResult(null, null, null, null, null, null, null, false);
            }
            return new DoctorSearchResult(
                    d.getId(),
                    d.getProvider().getUser().getId(),
                    d.getProvider().getUser().getName(),
                    d.getSpecialty(),
                    d.getExperienceYears(),
                    d.getProvider().getRating(),
                    d.getProvider().getServiceRadiusKm(),
                    d.getProvider().isOnline());
        }
    }

    public record NearbyDoctorResponse(
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
            Double latitude,
            Double longitude,
            Double distanceKm) {
        static NearbyDoctorResponse from(NearbyDoctorDiscoveryService.NearbyDoctorMatch match) {
            return new NearbyDoctorResponse(
                    match.doctorId(),
                    match.providerId(),
                    match.userId(),
                    match.name(),
                    match.specialty(),
                    match.experienceYears(),
                    match.rating(),
                    match.totalReviews(),
                    match.serviceRadiusKm(),
                    match.isOnline(),
                    match.providerLatitude(),
                    match.providerLongitude(),
                    match.distanceKm());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<DoctorSearchResult> list(
            Authentication authentication,
            @RequestParam(value = "specialty", required = false) String specialty) {
        requireAuthenticated(authentication);

        List<Doctor> doctors;
        if (specialty == null || specialty.isBlank()) {
            doctors = doctorRepository.findAll();
        } else {
            doctors = doctorRepository.findAllBySpecialtyIgnoreCaseContaining(specialty.trim());
        }

        return doctors.stream().map(DoctorSearchResult::from).toList();
    }

    @GetMapping("/nearby")
    @Transactional(readOnly = true)
    public List<NearbyDoctorResponse> nearby(
            Authentication authentication,
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng,
            @RequestParam(value = "specialty", required = false) String specialty) {
        requireAuthenticated(authentication);

        if (lat == null || lng == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat and lng are required");
        }

        return nearbyDoctorDiscoveryService.findNearbyDoctors(lat, lng, specialty).stream()
                .map(NearbyDoctorResponse::from)
                .toList();
    }

    private void requireAuthenticated(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
