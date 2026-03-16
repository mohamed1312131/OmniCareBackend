package com.omnicare.reminder.service;

import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.patient.repository.PatientMedicationRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.reminder.model.PatientReminder;
import com.omnicare.reminder.model.PatientReminderTime;
import com.omnicare.reminder.model.PatientReminderType;
import com.omnicare.reminder.repository.PatientReminderRepository;
import com.omnicare.reminder.repository.PatientReminderTimeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class PatientReminderService {

    private final PatientRepository patientRepository;
    private final PatientReminderRepository patientReminderRepository;
    private final PatientReminderTimeRepository patientReminderTimeRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final UserRepository userRepository;

    public PatientReminderService(
            PatientRepository patientRepository,
            PatientReminderRepository patientReminderRepository,
            PatientReminderTimeRepository patientReminderTimeRepository,
            PatientMedicationRepository patientMedicationRepository,
            UserRepository userRepository
    ) {
        this.patientRepository = patientRepository;
        this.patientReminderRepository = patientReminderRepository;
        this.patientReminderTimeRepository = patientReminderTimeRepository;
        this.patientMedicationRepository = patientMedicationRepository;
        this.userRepository = userRepository;
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

    public List<PatientReminder> listForActor(UserRole actorRole, UUID patientId, UUID ownerUserIdOrNull) {
        Patient patient = loadAuthorizedPatient(patientId, actorRole, ownerUserIdOrNull);
        return patientReminderRepository.findAllByPatientIdOrderByCreatedAtDesc(patient.getId());
    }

    public List<PatientReminderTime> listTimes(UUID reminderId) {
        return patientReminderTimeRepository.findAllByReminderIdOrderByTimeAsc(reminderId);
    }

    @Transactional
    public PatientReminder create(UUID actorUserId, UserRole actorRole, String actorEmail, UUID patientId, UUID ownerUserIdOrNull, UpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        if (request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }

        Patient patient = loadAuthorizedPatient(patientId, actorRole, ownerUserIdOrNull);

        PatientReminder reminder = new PatientReminder(patient, request.type(), request.title().trim());
        applyFields(reminder, patient, request);
        reminder.setCreatedAt(Instant.now());

        if (actorEmail != null && !actorEmail.isBlank()) {
            User createdBy = userRepository.findByEmail(actorEmail).orElse(null);
            if (createdBy != null) {
                reminder.setCreatedByUser(createdBy);
            }
        }

        PatientReminder saved = patientReminderRepository.save(reminder);
        replaceTimes(saved, request.times());
        return saved;
    }

    @Transactional
    public PatientReminder update(UUID actorUserId, UserRole actorRole, UUID patientId, UUID ownerUserIdOrNull, UUID reminderId, UpsertRequest request) {
        if (reminderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reminderId is required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }

        Patient patient = loadAuthorizedPatient(patientId, actorRole, ownerUserIdOrNull);
        PatientReminder reminder = patientReminderRepository.findByIdAndPatientId(reminderId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));

        applyFields(reminder, patient, request);
        PatientReminder saved = patientReminderRepository.save(reminder);
        replaceTimes(saved, request.times());
        return saved;
    }

    @Transactional
    public void delete(UUID actorUserId, UserRole actorRole, UUID patientId, UUID ownerUserIdOrNull, UUID reminderId) {
        if (reminderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reminderId is required");
        }

        Patient patient = loadAuthorizedPatient(patientId, actorRole, ownerUserIdOrNull);
        PatientReminder reminder = patientReminderRepository.findByIdAndPatientId(reminderId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));

        patientReminderTimeRepository.deleteAllByReminderId(reminder.getId());
        patientReminderRepository.delete(reminder);
    }

    private Patient loadAuthorizedPatient(UUID patientId, UserRole actorRole, UUID ownerUserIdOrNull) {
        if (patientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }

        if (actorRole == UserRole.DOCTOR || actorRole == UserRole.ADMIN) {
            return patientRepository.findById(patientId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        }

        if (ownerUserIdOrNull == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return patientRepository.findByIdAndOwnerUserId(patientId, ownerUserIdOrNull)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    private void applyFields(PatientReminder reminder, Patient patient, UpsertRequest request) {
        if (request.type() != null) {
            reminder.setType(request.type());
        }
        if (request.title() != null) {
            String t = request.title().trim();
            if (t.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title cannot be blank");
            }
            reminder.setTitle(t);
        }
        if (request.description() != null) {
            String d = request.description().trim();
            reminder.setDescription(d.isEmpty() ? null : d);
        }
        if (request.enabled() != null) {
            reminder.setEnabled(request.enabled());
        }
        if (request.startDate() != null) {
            reminder.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            reminder.setEndDate(request.endDate());
        }
        if (request.oneTime() != null) {
            reminder.setOneTime(request.oneTime());
        }
        if (request.scheduledDate() != null) {
            reminder.setScheduledDate(request.scheduledDate());
        }

        UUID pmId = request.patientMedicationId();
        if (pmId != null) {
            var pm = patientMedicationRepository.findByIdAndPatientId(pmId, patient.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientMedicationId does not belong to patient"));
            reminder.setPatientMedication(pm);
        } else {
            reminder.setPatientMedication(null);
        }

        if (request.type() == PatientReminderType.MEDICATION && pmId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientMedicationId is required for MEDICATION reminders");
        }
    }

    private void replaceTimes(PatientReminder reminder, List<LocalTime> times) {
        patientReminderTimeRepository.deleteAllByReminderId(reminder.getId());

        if (times == null || times.isEmpty()) {
            return;
        }

        for (LocalTime t : times) {
            if (t == null) {
                continue;
            }
            patientReminderTimeRepository.save(new PatientReminderTime(reminder, t));
        }
    }
}
