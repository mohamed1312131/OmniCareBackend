package com.omnicare.patient.service;

import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientAllergy;
import com.omnicare.patient.PatientAllergySeverity;
import com.omnicare.patient.repository.PatientAllergyRepository;
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
public class PatientAllergyService {

    private final PatientRepository patientRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final UserRepository userRepository;

    public PatientAllergyService(PatientRepository patientRepository, PatientAllergyRepository patientAllergyRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.patientAllergyRepository = patientAllergyRepository;
        this.userRepository = userRepository;
    }

    public record CreateRequest(String substance, String reaction, PatientAllergySeverity severity) {
    }

    public List<PatientAllergy> listForOwner(UUID ownerUserId, UUID patientId) {
        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        return patientAllergyRepository.findAllByPatientIdOrderByRecordedAtDesc(patient.getId());
    }

    @Transactional
    public PatientAllergy addForOwner(UUID ownerUserId, UUID patientId, String createdByEmail, CreateRequest request) {
        if (request == null || request.substance() == null || request.substance().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "substance is required");
        }

        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        String substance = request.substance().trim();
        if (patientAllergyRepository.existsByPatientIdAndSubstanceIgnoreCase(patient.getId(), substance)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Allergy already exists");
        }

        PatientAllergy allergy = new PatientAllergy(patient, substance);
        if (request.reaction() != null && !request.reaction().isBlank()) {
            allergy.setReaction(request.reaction().trim());
        }
        allergy.setSeverity(request.severity() == null ? PatientAllergySeverity.UNKNOWN : request.severity());
        allergy.setRecordedAt(Instant.now());

        if (createdByEmail != null && !createdByEmail.isBlank()) {
            User createdBy = userRepository.findByEmail(createdByEmail).orElse(null);
            if (createdBy != null) {
                allergy.setCreatedByUser(createdBy);
            }
        }

        return patientAllergyRepository.save(allergy);
    }

    @Transactional
    public void deleteForOwner(UUID ownerUserId, UUID patientId, UUID allergyId) {
        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        PatientAllergy allergy = patientAllergyRepository.findByIdAndPatientId(allergyId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allergy not found"));

        patientAllergyRepository.delete(allergy);
    }
}
