package com.omnicare.prescription.service;

import com.omnicare.medication.Medication;
import com.omnicare.medication.MedicationRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.prescription.model.Prescription;
import com.omnicare.prescription.model.PrescriptionItem;
import com.omnicare.prescription.repository.PrescriptionRepository;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.audit.model.AuditEntityType;
import com.omnicare.audit.model.AuditLogAction;
import com.omnicare.audit.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    private final ConsultationRepository consultationRepository;
    private final AuditLogService auditLogService;

    public PrescriptionService(PrescriptionRepository prescriptionRepository, PatientRepository patientRepository, MedicationRepository medicationRepository, UserRepository userRepository, ConsultationRepository consultationRepository, AuditLogService auditLogService) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientRepository = patientRepository;
        this.medicationRepository = medicationRepository;
        this.userRepository = userRepository;
        this.consultationRepository = consultationRepository;
        this.auditLogService = auditLogService;
    }

    public record CreateItemRequest(
            UUID medicationId,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequencyTimes,
            Integer frequencyPeriodDays,
            Integer durationDays,
            LocalDate startDate,
            String instructions
    ) {
    }

    public record CreateRequest(
            UUID patientId,
            Instant issuedAt,
            String notes,
            List<CreateItemRequest> items
    ) {
    }

    @Transactional
    public Prescription createAsDoctor(UUID doctorUserId, CreateRequest request) {
        if (request == null || request.patientId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }

        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        User doctor = userRepository.findById(doctorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        Instant issuedAt = request.issuedAt() == null ? Instant.now() : request.issuedAt();
        Prescription p = new Prescription(patient, doctor, issuedAt);
        p.setNotes(request.notes());

        List<CreateItemRequest> items = request.items();
        if (items != null) {
            for (CreateItemRequest it : items) {
                p.addItem(toItemOrThrow(it));
            }
        }

        Prescription saved = prescriptionRepository.save(p);
        auditLogService.log(doctor, AuditEntityType.PRESCRIPTION, saved.getId(), AuditLogAction.CREATE, null);
        return saved;
    }

    @Transactional
    public Prescription createOrReplaceForConsultationAsDoctor(UUID consultationId, UUID doctorUserId, CreateRequest request) {
        if (consultationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consultationId is required");
        }

        Consultation c = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
        if (c.getPatient() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultation has no patient");
        }

        User doctor = userRepository.findById(doctorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        var existing = prescriptionRepository.findByConsultationId(consultationId);
        boolean isNew = existing.isEmpty();

        Prescription p = existing.orElseGet(() -> {
            Instant issuedAt = (request == null || request.issuedAt() == null) ? Instant.now() : request.issuedAt();
            Prescription created = new Prescription(c.getPatient(), doctor, issuedAt);
            created.setConsultation(c);
            return created;
        });

        if (request != null) {
            if (request.issuedAt() != null) {
                p.setIssuedAt(request.issuedAt());
            }
            if (request.notes() != null) {
                p.setNotes(request.notes());
            }
            if (request.items() != null) {
                p.clearItems();
                for (CreateItemRequest it : request.items()) {
                    p.addItem(toItemOrThrow(it));
                }
            }
        }

        p.setPrescriberUser(doctor);
        p.setConsultation(c);

        Prescription saved = prescriptionRepository.save(p);
        if (isNew) {
            auditLogService.log(doctor, AuditEntityType.PRESCRIPTION, saved.getId(), AuditLogAction.CREATE, null);
        } else {
            auditLogService.log(doctor, AuditEntityType.PRESCRIPTION, saved.getId(), AuditLogAction.UPDATE, null);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Prescription getForConsultationAsActor(UUID consultationId) {
        if (consultationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consultationId is required");
        }
        return prescriptionRepository.findByConsultationId(consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription not found"));
    }

    @Transactional
    public Prescription replaceAsDoctor(UUID prescriptionId, UUID doctorUserId, CreateRequest request) {
        Prescription p = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription not found"));

        User doctor = userRepository.findById(doctorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        if (request != null) {
            if (request.issuedAt() != null) {
                p.setIssuedAt(request.issuedAt());
            }
            if (request.notes() != null) {
                p.setNotes(request.notes());
            }
            if (request.items() != null) {
                p.clearItems();
                for (CreateItemRequest it : request.items()) {
                    p.addItem(toItemOrThrow(it));
                }
            }
        }

        p.setPrescriberUser(doctor);
        Prescription saved = prescriptionRepository.save(p);
        auditLogService.log(doctor, AuditEntityType.PRESCRIPTION, saved.getId(), AuditLogAction.UPDATE, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Prescription> listForPatientAsDoctor(UUID patientId) {
        return prescriptionRepository.findAllByPatientIdOrderByIssuedAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public List<Prescription> listForPatientAsOwner(UUID ownerUserId, UUID patientId) {
        patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        return prescriptionRepository.findAllByPatientIdOrderByIssuedAtDesc(patientId);
    }

    private PrescriptionItem toItemOrThrow(CreateItemRequest it) {
        if (it == null || it.medicationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicationId is required");
        }
        int times = it.frequencyTimes() == null ? 0 : it.frequencyTimes();
        int periodDays = it.frequencyPeriodDays() == null ? 0 : it.frequencyPeriodDays();
        int durationDays = it.durationDays() == null ? 0 : it.durationDays();

        if (times <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frequencyTimes must be > 0");
        }
        if (periodDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frequencyPeriodDays must be > 0");
        }
        if (durationDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationDays must be > 0");
        }

        Medication med = medicationRepository.findById(it.medicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown medicationId"));

        PrescriptionItem item = new PrescriptionItem(med, times, periodDays, durationDays);

        if (it.doseAmount() != null) {
            item.setDoseAmount(it.doseAmount());
        }
        if (it.doseUnit() != null && !it.doseUnit().isBlank()) {
            item.setDoseUnit(it.doseUnit().trim());
        }
        if (it.startDate() != null) {
            item.setStartDate(it.startDate());
        }
        if (it.instructions() != null && !it.instructions().isBlank()) {
            item.setInstructions(it.instructions().trim());
        }

        return item;
    }
}
