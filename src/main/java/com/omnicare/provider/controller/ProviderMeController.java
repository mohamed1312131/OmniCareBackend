package com.omnicare.provider.controller;

import com.omnicare.access.service.PatientAccessService;
import com.omnicare.doctor.controller.ConsultationController;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.patient.controller.PatientController;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
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

    @GetMapping("/consultations")
    @Transactional(readOnly = true)
    public List<ConsultationController.ConsultationFlowResponse> listMyConsultations(
            Authentication authentication,
            @RequestParam(value = "status", required = false) ConsultationStatus status
    ) {
        User actor = requireUser(authentication);
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);
        List<Consultation> rows = (status == null)
                ? consultationRepository.findAllByProviderIdOrderByTimestampDesc(provider.getId())
                : consultationRepository.findAllByProviderIdAndStatusOrderByTimestampDesc(provider.getId(), status);

        return rows.stream()
                .filter(c -> {
                    if (c == null || c.getPatient() == null || c.getPatient().getId() == null) {
                        return false;
                    }
                    patientAccessService.requireProviderAccess(actor, c.getPatient().getId(), PatientAccessService.Scope.CONSULTATIONS_READ);
                    return true;
                })
                .map(c -> ConsultationController.ConsultationFlowResponse.from(c, null))
                .toList();
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

        return patientIds.stream()
                .map(id -> patientRepository.findById(id).orElse(null))
                .filter(p -> p != null)
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
