package com.omnicare.passport;

import com.omnicare.medication.MedicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MedicalInfoValidator {

    private final MedicationRepository medicationRepository;

    public MedicalInfoValidator(MedicationRepository medicationRepository) {
        this.medicationRepository = medicationRepository;
    }

    public void validateOrThrow(Map<String, Object> medicalInfo) {
        if (medicalInfo == null) {
            return;
        }

        Object chronic = medicalInfo.get("chronicConditions");
        if (chronic != null) {
            if (!(chronic instanceof List<?> list)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalInfo.chronicConditions must be a list of strings");
            }
            for (Object item : list) {
                if (!(item instanceof String s) || s.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalInfo.chronicConditions must be a list of strings");
                }
            }
        }

        Object meds = medicalInfo.get("currentMedications");
        if (meds != null) {
            if (!(meds instanceof List<?> list)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalInfo.currentMedications must be a list of objects");
            }

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalInfo.currentMedications must be a list of objects");
                }

                Object medicationIdRaw = map.get("medicationId");
                if (medicationIdRaw == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each currentMedication requires medicationId");
                }

                UUID medicationId;
                try {
                    medicationId = UUID.fromString(String.valueOf(medicationIdRaw));
                } catch (Exception ex) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentMedication.medicationId must be a UUID");
                }

                if (!medicationRepository.existsById(medicationId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown medicationId: " + medicationId);
                }
            }
        }
    }
}
