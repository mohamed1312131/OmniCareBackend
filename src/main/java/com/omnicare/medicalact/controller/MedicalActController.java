package com.omnicare.medicalact.controller;

import com.omnicare.access.service.PatientAccessService;
import com.omnicare.medicalact.model.MedicalAct;
import com.omnicare.medicalact.model.MedicalActType;
import com.omnicare.medicalact.repository.MedicalActRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.prescription.model.Prescription;
import com.omnicare.prescription.repository.PrescriptionRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.service.ProviderService;
import com.omnicare.audit.model.AuditEntityType;
import com.omnicare.audit.model.AuditLogAction;
import com.omnicare.audit.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/medical-acts")
public class MedicalActController {

    private final MedicalActRepository medicalActRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;
    private final ProviderService providerService;
    private final PatientAccessService patientAccessService;
    private final AuditLogService auditLogService;

    public MedicalActController(
            MedicalActRepository medicalActRepository,
            PrescriptionRepository prescriptionRepository,
            UserRepository userRepository,
            ProviderService providerService,
            PatientAccessService patientAccessService,
            AuditLogService auditLogService
    ) {
        this.medicalActRepository = medicalActRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.userRepository = userRepository;
        this.providerService = providerService;
        this.patientAccessService = patientAccessService;
        this.auditLogService = auditLogService;
    }

    public record CreateMedicalActRequest(
            UUID prescriptionId,
            MedicalActType actType,
            String photoUrl,
            String notes
    ) {
    }

    public record MedicalActResponse(
            UUID id,
            UUID prescriptionId,
            UUID providerId,
            String providerType,
            MedicalActType actType,
            Instant performedAt,
            String photoUrl,
            String notes
    ) {
        public static MedicalActResponse from(MedicalAct a) {
            UUID providerId = a.getProvider() == null ? null : a.getProvider().getId();
            String providerType = (a.getProvider() != null && a.getProvider().getType() != null) ? a.getProvider().getType().name() : null;
            UUID prescriptionId = a.getPrescription() == null ? null : a.getPrescription().getId();
            return new MedicalActResponse(
                    a.getId(),
                    prescriptionId,
                    providerId,
                    providerType,
                    a.getActType(),
                    a.getPerformedAt(),
                    a.getPhotoUrl(),
                    a.getNotes()
            );
        }
    }

    @PostMapping
    @Transactional
    public MedicalActResponse create(Authentication authentication, @RequestBody CreateMedicalActRequest request) {
        User actor = requireUser(authentication);
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);
        ProviderType type = provider.getType();
        if (type != ProviderType.NURSE && type != ProviderType.KINE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nurse or Kine role required");
        }

        if (request == null || request.prescriptionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prescriptionId is required");
        }
        if (request.actType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actType is required");
        }

        if (request.actType() == MedicalActType.INJECTION) {
            if (request.photoUrl() == null || request.photoUrl().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photoUrl is required for injection");
            }
        }

        Prescription p = prescriptionRepository.findById(request.prescriptionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription not found"));

        Patient patient = p.getPatient();
        if (patient == null || patient.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prescription patient missing");
        }

        patientAccessService.requireProviderAccess(actor, patient.getId(), PatientAccessService.Scope.CONSULTATIONS_WRITE);

        MedicalAct act = new MedicalAct(p, provider, request.actType());
        if (request.photoUrl() != null && !request.photoUrl().isBlank()) {
            act.setPhotoUrl(request.photoUrl().trim());
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            act.setNotes(request.notes().trim());
        }
        act.setPerformedAt(Instant.now());

        MedicalAct saved = medicalActRepository.save(act);
        auditLogService.log(actor, AuditEntityType.MEDICAL_ACT, saved.getId(), AuditLogAction.CREATE, null);
        return MedicalActResponse.from(saved);
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
