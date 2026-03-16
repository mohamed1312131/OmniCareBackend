package com.omnicare.patient.service;

import com.omnicare.medication.Medication;
import com.omnicare.medication.MedicationRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientMedication;
import com.omnicare.patient.repository.PatientMedicationRepository;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PatientMedicationService {

    private final PatientRepository patientRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;

    public PatientMedicationService(PatientRepository patientRepository, PatientMedicationRepository patientMedicationRepository, MedicationRepository medicationRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.patientMedicationRepository = patientMedicationRepository;
        this.medicationRepository = medicationRepository;
        this.userRepository = userRepository;
    }

    public record CreateRequest(UUID medicationId, Integer timesPerDay, String frequency, Integer durationDays, LocalDate startDate, String notes) {
    }

    public List<PatientMedication> listForOwner(UUID ownerUserId, UUID patientId) {
        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        return patientMedicationRepository.findAllByPatientIdOrderByRecordedAtDesc(patient.getId());
    }

    @Transactional
    public PatientMedication addForOwner(UUID ownerUserId, UUID patientId, String createdByEmail, CreateRequest request) {
        if (request == null || request.medicationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicationId is required");
        }

        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patientMedicationRepository.existsByPatientIdAndMedicationId(patient.getId(), request.medicationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Medication already exists");
        }

        Medication medication = medicationRepository.findById(request.medicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown medicationId"));

        PatientMedication pm = new PatientMedication(patient, medication);
        if (request.timesPerDay() != null) {
            pm.setTimesPerDay(request.timesPerDay());
        }
        if (request.frequency() != null && !request.frequency().isBlank()) {
            pm.setFrequency(request.frequency().trim());
        }
        if (request.durationDays() != null) {
            pm.setDurationDays(request.durationDays());
        }
        if (request.startDate() != null) {
            pm.setStartDate(request.startDate());
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            pm.setNotes(request.notes().trim());
        }
        pm.setRecordedAt(Instant.now());

        if (createdByEmail != null && !createdByEmail.isBlank()) {
            User createdBy = userRepository.findByEmail(createdByEmail).orElse(null);
            if (createdBy != null) {
                pm.setCreatedByUser(createdBy);
            }
        }

        return patientMedicationRepository.save(pm);
    }

    @Transactional
    public void deleteForOwner(UUID ownerUserId, UUID patientId, UUID patientMedicationId) {
        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        PatientMedication pm = patientMedicationRepository.findByIdAndPatientId(patientMedicationId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));

        patientMedicationRepository.delete(pm);
    }
}
