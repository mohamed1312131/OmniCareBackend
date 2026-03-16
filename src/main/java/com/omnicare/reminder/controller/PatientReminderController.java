package com.omnicare.reminder.controller;

import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.reminder.model.PatientReminder;
import com.omnicare.reminder.model.PatientReminderTime;
import com.omnicare.reminder.model.PatientReminderType;
import com.omnicare.reminder.service.PatientReminderService;
import com.omnicare.access.service.PatientAccessService;
import com.omnicare.access.service.PatientAccessService.Scope;
import com.omnicare.provider.service.ProviderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients/{patientId}/reminders")
public class PatientReminderController {

    private final UserRepository userRepository;
    private final PatientReminderService patientReminderService;
    private final PatientAccessService patientAccessService;

    public PatientReminderController(UserRepository userRepository, PatientReminderService patientReminderService, PatientAccessService patientAccessService) {
        this.userRepository = userRepository;
        this.patientReminderService = patientReminderService;
        this.patientAccessService = patientAccessService;
    }

    public record ReminderResponse(
            UUID id,
            PatientReminderType type,
            String title,
            String description,
            boolean enabled,
            LocalDate startDate,
            LocalDate endDate,
            boolean oneTime,
            LocalDate scheduledDate,
            UUID patientMedicationId,
            List<LocalTime> times,
            UUID createdByUserId
    ) {
        static ReminderResponse from(PatientReminder r, List<PatientReminderTime> times) {
            UUID createdById = r.getCreatedByUser() != null ? r.getCreatedByUser().getId() : null;
            UUID pmId = r.getPatientMedication() != null ? r.getPatientMedication().getId() : null;
            List<LocalTime> list = times == null ? List.of() : times.stream().map(PatientReminderTime::getTime).toList();
            return new ReminderResponse(
                    r.getId(),
                    r.getType(),
                    r.getTitle(),
                    r.getDescription(),
                    r.isEnabled(),
                    r.getStartDate(),
                    r.getEndDate(),
                    r.isOneTime(),
                    r.getScheduledDate(),
                    pmId,
                    list,
                    createdById
            );
        }
    }

    public record UpsertRequest(
            PatientReminderType type,
            String title,
            String description,
            Boolean enabled,
            LocalDate startDate,
            LocalDate endDate,
            Boolean oneTime,
            LocalDate scheduledDate,
            UUID patientMedicationId,
            List<LocalTime> times
    ) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ReminderResponse> list(Authentication authentication, @PathVariable UUID patientId) {
        User actor = requireUser(authentication);

        if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            patientAccessService.requireProviderAccess(actor, patientId, Scope.REMINDERS_READ);
        }
        UUID ownerId = actor.getRole() == UserRole.PATIENT ? actor.getId() : null;
        List<PatientReminder> all = patientReminderService.listForActor(actor.getRole(), patientId, ownerId);

        return all.stream().map(r -> ReminderResponse.from(r, patientReminderService.listTimes(r.getId()))).toList();
    }

    @PostMapping
    public ReminderResponse create(Authentication authentication, @PathVariable UUID patientId, @RequestBody UpsertRequest request) {
        User actor = requireUser(authentication);

        if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            patientAccessService.requireProviderAccess(actor, patientId, Scope.REMINDERS_WRITE);
        }
        UUID ownerId = actor.getRole() == UserRole.PATIENT ? actor.getId() : null;

        PatientReminder saved = patientReminderService.create(actor.getId(), actor.getRole(), actor.getEmail(), patientId, ownerId,
                new PatientReminderService.UpsertRequest(request.type(), request.title(), request.description(), request.enabled(), request.startDate(), request.endDate(), request.oneTime(), request.scheduledDate(), request.patientMedicationId(), request.times()));

        return ReminderResponse.from(saved, patientReminderService.listTimes(saved.getId()));
    }

    @PutMapping("/{reminderId}")
    public ReminderResponse update(Authentication authentication, @PathVariable UUID patientId, @PathVariable UUID reminderId, @RequestBody UpsertRequest request) {
        User actor = requireUser(authentication);

        if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            patientAccessService.requireProviderAccess(actor, patientId, Scope.REMINDERS_WRITE);
        }
        UUID ownerId = actor.getRole() == UserRole.PATIENT ? actor.getId() : null;

        PatientReminder saved = patientReminderService.update(actor.getId(), actor.getRole(), patientId, ownerId, reminderId,
                new PatientReminderService.UpsertRequest(request.type(), request.title(), request.description(), request.enabled(), request.startDate(), request.endDate(), request.oneTime(), request.scheduledDate(), request.patientMedicationId(), request.times()));

        return ReminderResponse.from(saved, patientReminderService.listTimes(saved.getId()));
    }

    @DeleteMapping("/{reminderId}")
    public void delete(Authentication authentication, @PathVariable UUID patientId, @PathVariable UUID reminderId) {
        User actor = requireUser(authentication);

        if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            patientAccessService.requireProviderAccess(actor, patientId, Scope.REMINDERS_WRITE);
        }
        UUID ownerId = actor.getRole() == UserRole.PATIENT ? actor.getId() : null;
        patientReminderService.delete(actor.getId(), actor.getRole(), patientId, ownerId, reminderId);
    }

    private User requireUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email in authenticated principal");
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
