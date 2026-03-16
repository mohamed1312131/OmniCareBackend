package com.omnicare.passport;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class MedicalInfoValidator {

    public MedicalInfoValidator() {
    }

    public void validateOrThrow(Map<String, Object> medicalInfo) {
        if (medicalInfo == null) {
            return;
        }

        if (!(medicalInfo instanceof Map<?, ?>)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalInfo must be an object");
        }
    }
}
