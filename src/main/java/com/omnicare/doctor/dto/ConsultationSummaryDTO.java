package com.omnicare.doctor.dto;

import com.omnicare.doctor.model.ConsultationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConsultationSummaryDTO(
        UUID id,
        String patientName,
        Instant timestamp,
        ConsultationStatus status,
        BigDecimal fee,
        String symptoms
) {
}
