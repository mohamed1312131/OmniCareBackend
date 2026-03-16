package com.omnicare.prescription.controller;

import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.prescription.model.Prescription;
import com.omnicare.prescription.model.PrescriptionItem;
import com.omnicare.prescription.model.PrescriptionStatus;
import com.omnicare.prescription.service.PrescriptionService;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.service.ProviderService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final UserRepository userRepository;
    private final ProviderService providerService;

    public PrescriptionController(PrescriptionService prescriptionService, UserRepository userRepository, ProviderService providerService) {
        this.prescriptionService = prescriptionService;
        this.userRepository = userRepository;
        this.providerService = providerService;
    }

    public record ItemResponse(
            UUID id,
            UUID medicationId,
            String medicationName,
            BigDecimal doseAmount,
            String doseUnit,
            int frequencyTimes,
            int frequencyPeriodDays,
            int durationDays,
            LocalDate startDate,
            String instructions
    ) {
        static ItemResponse from(PrescriptionItem i) {
            return new ItemResponse(
                    i.getId(),
                    i.getMedication().getId(),
                    i.getMedication().getName(),
                    i.getDoseAmount(),
                    i.getDoseUnit(),
                    i.getFrequencyTimes(),
                    i.getFrequencyPeriodDays(),
                    i.getDurationDays(),
                    i.getStartDate(),
                    i.getInstructions()
            );
        }
    }

    public record PrescriptionResponse(
            UUID id,
            UUID patientId,
            UUID prescriberUserId,
            Instant issuedAt,
            PrescriptionStatus status,
            String notes,
            List<ItemResponse> items
    ) {
        static PrescriptionResponse from(Prescription p) {
            UUID prescriberId = p.getPrescriberUser() == null ? null : p.getPrescriberUser().getId();
            return new PrescriptionResponse(
                    p.getId(),
                    p.getPatient().getId(),
                    prescriberId,
                    p.getIssuedAt(),
                    p.getStatus(),
                    p.getNotes(),
                    (p.getItems() == null ? List.of() : p.getItems().stream().map(ItemResponse::from).toList())
            );
        }
    }

    @PostMapping
    public PrescriptionResponse create(Authentication authentication, @RequestBody PrescriptionService.CreateRequest request) {
        User actor = requireUser(authentication);
        requireDoctorOrPsychiatristProvider(actor);
        Prescription created = prescriptionService.createAsDoctor(actor.getId(), request);
        return PrescriptionResponse.from(created);
    }

    @PutMapping("/{id}")
    public PrescriptionResponse replace(Authentication authentication, @PathVariable("id") UUID id, @RequestBody PrescriptionService.CreateRequest request) {
        User actor = requireUser(authentication);
        requireDoctorOrPsychiatristProvider(actor);
        Prescription updated = prescriptionService.replaceAsDoctor(id, actor.getId(), request);
        return PrescriptionResponse.from(updated);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> list(Authentication authentication, @RequestParam("patientId") UUID patientId) {
        User actor = requireUser(authentication);
        if (actor.getRole() == UserRole.DOCTOR) {
            return prescriptionService.listForPatientAsDoctor(patientId).stream().map(PrescriptionResponse::from).toList();
        }
        if (actor.getRole() == UserRole.PATIENT) {
            return prescriptionService.listForPatientAsOwner(actor.getId(), patientId).stream().map(PrescriptionResponse::from).toList();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
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

    private void requireDoctorOrPsychiatristProvider(User user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!ProviderService.isProfessionalRole(user.getRole()) || user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(user);
        ProviderType type = provider.getType();
        if (type != ProviderType.DOCTOR && type != ProviderType.PSYCHIATRIST) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Prescriber role required");
        }
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
