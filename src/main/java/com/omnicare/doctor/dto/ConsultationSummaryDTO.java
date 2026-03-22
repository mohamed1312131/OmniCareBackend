package com.omnicare.doctor.dto;

import com.omnicare.doctor.model.ConsultationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConsultationSummaryDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("patient_id") UUID patientId,
        @JsonProperty("patient_name") String patientName,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("status") ConsultationStatus status,
        @JsonProperty("fee") BigDecimal fee,
        @JsonProperty("net_amount") BigDecimal netAmount,
        @JsonProperty("omnicare_fee") BigDecimal omnicareFee,
        @JsonProperty("symptoms") String symptoms,
        @JsonProperty("diagnosis") String diagnosis,
        @JsonProperty("treatment") String treatment
) {
}
