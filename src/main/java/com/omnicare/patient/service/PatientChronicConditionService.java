package com.omnicare.patient.service;

import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientChronicCondition;
import com.omnicare.patient.repository.PatientChronicConditionRepository;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PatientChronicConditionService {

    private final PatientRepository patientRepository;
    private final PatientChronicConditionRepository patientChronicConditionRepository;
    private final UserRepository userRepository;

    public PatientChronicConditionService(PatientRepository patientRepository, PatientChronicConditionRepository patientChronicConditionRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.patientChronicConditionRepository = patientChronicConditionRepository;
        this.userRepository = userRepository;
    }

    public record CreateRequest(String name, String notes) {
    }

    public List<PatientChronicCondition> listForOwner(UUID ownerUserId, UUID patientId) {
        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        return patientChronicConditionRepository.findAllByPatientIdOrderByRecordedAtDesc(patient.getId());
    }

    @Transactional
    public PatientChronicCondition addForOwner(UUID ownerUserId, UUID patientId, String createdByEmail, CreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }

        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        String name = request.name().trim();
        if (patientChronicConditionRepository.existsByPatientIdAndNameIgnoreCase(patient.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Condition already exists");
        }

        PatientChronicCondition cc = new PatientChronicCondition(patient, name);
        if (request.notes() != null && !request.notes().isBlank()) {
            cc.setNotes(request.notes().trim());
        }
        cc.setRecordedAt(Instant.now());

        if (createdByEmail != null && !createdByEmail.isBlank()) {
            User createdBy = userRepository.findByEmail(createdByEmail).orElse(null);
            if (createdBy != null) {
                cc.setCreatedByUser(createdBy);
            }
        }

        return patientChronicConditionRepository.save(cc);
    }

    @Transactional
    public void deleteForOwner(UUID ownerUserId, UUID patientId, UUID conditionId) {
        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        PatientChronicCondition cc = patientChronicConditionRepository.findByIdAndPatientId(conditionId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condition not found"));

        patientChronicConditionRepository.delete(cc);
    }
}
