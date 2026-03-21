package com.omnicare.provider.controller;

import com.omnicare.access.service.PatientAccessService;
import com.omnicare.doctor.dto.ConsultationSummaryDTO;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.patient.controller.PatientController;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.service.ProviderService;
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

import java.util.Objects;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/providers/me")
public class ProviderMeController {

    private final UserRepository userRepository;
    private final ProviderService providerService;
    private final ConsultationRepository consultationRepository;
    private final PatientAccessService patientAccessService;
    private final PatientRepository patientRepository;

    public ProviderMeController(
            UserRepository userRepository,
            ProviderService providerService,
            ConsultationRepository consultationRepository,
            PatientAccessService patientAccessService,
            PatientRepository patientRepository
    ) {
        this.userRepository = userRepository;
        this.providerService = providerService;
        this.consultationRepository = consultationRepository;
        this.patientAccessService = patientAccessService;
        this.patientRepository = patientRepository;
    }

    public record ProviderMeResponse(UUID providerId, ProviderType providerType, boolean isOnline) {
        static ProviderMeResponse from(Provider p) {
            if (p == null) {
                return new ProviderMeResponse(null, null, false);
            }
            return new ProviderMeResponse(p.getId(), p.getType(), p.isOnline());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ProviderMeResponse me(Authentication authentication) {
        User actor = requireUser(authentication);
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }
        Provider provider = providerService.ensureForProfessionalUser(actor);
        return ProviderMeResponse.from(provider);
    }

    @GetMapping("/consultations")
    @Transactional(readOnly = true)
    public List<ConsultationSummaryDTO> listMyConsultations(
            Authentication authentication,
            @RequestParam(value = "status", required = false) ConsultationStatus status,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radiusKm", required = false) Double radiusKm
    ) {
        User actor = requireUser(authentication);
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);

        final ConsultationStatus effectiveStatus = (status == null) ? ConsultationStatus.PENDING : status;

        final Double effectiveRadiusKm;
        if (radiusKm != null && radiusKm > 0) {
            effectiveRadiusKm = radiusKm;
        } else if (provider.getServiceRadiusKm() != null && provider.getServiceRadiusKm() > 0) {
            effectiveRadiusKm = provider.getServiceRadiusKm().doubleValue();
        } else {
            effectiveRadiusKm = null;
        }

        final boolean geoEnabled = (lat != null && lng != null && effectiveRadiusKm != null);
        if (geoEnabled) {
            final double centerLat = lat;
            final double centerLng = lng;
            final double rKm = effectiveRadiusKm;

            return consultationRepository
                    .findSummaryRowsByProviderIdAndStatusOrderByTimestampDesc(provider.getId(), effectiveStatus)
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(row -> row.latitude() != null && row.longitude() != null)
                    .filter(row -> distanceKm(centerLat, centerLng, row.latitude(), row.longitude()) <= rKm)
                    .map(ConsultationRepository.ConsultationSummaryRow::toDto)
                    .toList();
        }

        return consultationRepository.findSummariesByProviderIdAndStatusOrderByTimestampDesc(provider.getId(), effectiveStatus);
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0088;
        final double dLat = Math.toRadians(lat2 - lat1);
        final double dLon = Math.toRadians(lon2 - lon1);
        final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @GetMapping("/patients")
    @Transactional(readOnly = true)
    public List<PatientController.PatientSummary> listMyPatients(Authentication authentication) {
        User actor = requireUser(authentication);
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        List<UUID> patientIds = patientAccessService.listActivePatientIdsForProvider(actor);
        if (patientIds.isEmpty()) {
            return List.of();
        }

        return patientRepository.findAllByIdInWithIdentity(patientIds).stream()
                .map(PatientController.PatientSummary::from)
                .toList();
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
}
