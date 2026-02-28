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

    public PrescriptionService(PrescriptionRepository prescriptionRepository, PatientRepository patientRepository, MedicationRepository medicationRepository, UserRepository userRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientRepository = patientRepository;
        this.medicationRepository = medicationRepository;
        this.userRepository = userRepository;
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

        return prescriptionRepository.save(p);
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
        return prescriptionRepository.save(p);
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
