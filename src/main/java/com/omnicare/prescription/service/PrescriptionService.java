package com.omnicare.prescription.service;

import com.omnicare.medication.Medication;
import com.omnicare.medication.MedicationRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientAllergyRepository;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.patient.service.PatientMedicationService;
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
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    private final ConsultationRepository consultationRepository;
    private final AuditLogService auditLogService;
    private final PatientMedicationService patientMedicationService;

    public PrescriptionService(PrescriptionRepository prescriptionRepository, PatientRepository patientRepository, PatientAllergyRepository patientAllergyRepository, MedicationRepository medicationRepository, UserRepository userRepository, ConsultationRepository consultationRepository, AuditLogService auditLogService, PatientMedicationService patientMedicationService) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientRepository = patientRepository;
        this.patientAllergyRepository = patientAllergyRepository;
        this.medicationRepository = medicationRepository;
        this.userRepository = userRepository;
        this.consultationRepository = consultationRepository;
        this.auditLogService = auditLogService;
        this.patientMedicationService = patientMedicationService;
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
            Set<String> allergySubstances = getNormalizedAllergySubstances(patient.getId());
            for (CreateItemRequest it : items) {
                p.addItem(toItemOrThrow(allergySubstances, it));
            }
        }

        Prescription saved = prescriptionRepository.save(p);
        auditLogService.log(doctor, AuditEntityType.PRESCRIPTION, saved.getId(), AuditLogAction.CREATE, null);
        patientMedicationService.syncFromPrescription(saved, doctor);
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
                Set<String> allergySubstances = getNormalizedAllergySubstances(c.getPatient().getId());
                p.clearItems();
                for (CreateItemRequest it : request.items()) {
                    p.addItem(toItemOrThrow(allergySubstances, it));
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
        patientMedicationService.syncFromPrescription(saved, doctor);
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
                Set<String> allergySubstances = getNormalizedAllergySubstances(p.getPatient().getId());
                p.clearItems();
                for (CreateItemRequest it : request.items()) {
                    p.addItem(toItemOrThrow(allergySubstances, it));
                }
            }
        }

        p.setPrescriberUser(doctor);
        Prescription saved = prescriptionRepository.save(p);
        auditLogService.log(doctor, AuditEntityType.PRESCRIPTION, saved.getId(), AuditLogAction.UPDATE, null);
        patientMedicationService.syncFromPrescription(saved, doctor);
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

    private PrescriptionItem toItemOrThrow(Set<String> normalizedAllergySubstances, CreateItemRequest it) {
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

        if (normalizedAllergySubstances != null && !normalizedAllergySubstances.isEmpty()) {
            Set<String> matches = findAllergyMatches(normalizedAllergySubstances, med);
            if (!matches.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Prescription blocked: patient has allergy to ingredient(s): " + String.join(", ", matches)
                );
            }
        }

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

    private static Set<String> findAllergyMatches(Set<String> normalizedAllergySubstances, Medication med) {
        Set<String> textTokens = new HashSet<>();
        textTokens.addAll(parseNormalizedSubstances(med == null ? null : med.getDci()));

        String normalizedName = normalizeSubstance(med == null ? null : med.getName());
        if (normalizedName != null && !normalizedName.isBlank()) {
            for (String token : normalizedName.split("\\s+")) {
                if (token != null && !token.isBlank()) {
                    textTokens.add(token);
                }
            }
        }

        if (textTokens.isEmpty() || normalizedAllergySubstances == null || normalizedAllergySubstances.isEmpty()) {
            return Set.of();
        }

        Set<String> matches = new HashSet<>();
        for (String allergy : normalizedAllergySubstances) {
            if (allergy == null || allergy.isBlank()) {
                continue;
            }

            for (String token : textTokens) {
                if (token == null || token.isBlank()) {
                    continue;
                }

                if (token.equals(allergy) || token.startsWith(allergy) || allergy.startsWith(token) || token.contains(allergy) || allergy.contains(token)) {
                    matches.add(allergy);
                    break;
                }
            }
        }
        return matches;
    }

    private Set<String> getNormalizedAllergySubstances(UUID patientId) {
        if (patientId == null) {
            return Set.of();
        }
        return patientAllergyRepository.findAllByPatientIdOrderByRecordedAtDesc(patientId).stream()
                .map(a -> normalizeSubstance(a.getSubstance()))
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String normalizeSubstance(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim().toLowerCase();
        if (s.isEmpty()) {
            return null;
        }
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{M}+", "");
        s = s.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    private static Set<String> parseNormalizedSubstances(String dci) {
        String normalized = normalizeSubstance(dci);
        if (normalized == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String token : normalized.split("\\s*(?:\\+|/|,|;|\\s+et\\s+)\\s*")) {
            String t = normalizeSubstance(token);
            if (t != null && !t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }
}
