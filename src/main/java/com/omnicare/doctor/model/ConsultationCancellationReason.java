package com.omnicare.doctor.model;

public enum ConsultationCancellationReason {
    PATIENT_REQUESTED,
    PROVIDER_UNAVAILABLE,
    SCHEDULING_CONFLICT,
    EMERGENCY,
    DUPLICATE_BOOKING,
    PATIENT_CANCELLED,
    PROVIDER_CANCELLED,
    DOCTOR_NO_SHOW,
    PATIENT_NO_SHOW,
    OTHER
}
