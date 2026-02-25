package com.omnicare.doctor;

import com.omnicare.family.FamilyMember;
import com.omnicare.patient.Patient;
import com.omnicare.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consultations")
public class Consultation {

    public static final BigDecimal OMNICARE_FEE_RATE = new BigDecimal("0.15");

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_user_id")
    private User patientUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_family_member_id")
    private FamilyMember patientFamilyMember;

    @Column(name = "symptoms", columnDefinition = "text")
    private String symptoms;

    @Column(name = "diagnosis", columnDefinition = "text")
    private String diagnosis;

    @Column(name = "treatment", columnDefinition = "text")
    private String treatment;

    @Column(name = "clinical_notes", columnDefinition = "text")
    private String clinicalNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConsultationStatus status = ConsultationStatus.PENDING;

    @Column(name = "fee", precision = 19, scale = 2)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    protected Consultation() {
    }

    public Consultation(Doctor doctor) {
        this.doctor = doctor;
    }

    public UUID getId() {
        return id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public User getPatientUser() {
        return patientUser;
    }

    public void setPatientUser(User patientUser) {
        this.patientUser = patientUser;
    }

    public FamilyMember getPatientFamilyMember() {
        return patientFamilyMember;
    }

    public void setPatientFamilyMember(FamilyMember patientFamilyMember) {
        this.patientFamilyMember = patientFamilyMember;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public String getClinicalNotes() {
        return clinicalNotes;
    }

    public void setClinicalNotes(String clinicalNotes) {
        this.clinicalNotes = clinicalNotes;
    }

    public ConsultationStatus getStatus() {
        return status;
    }

    public void setStatus(ConsultationStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        if (timestamp != null) {
            this.timestamp = timestamp;
        }
    }

    public BigDecimal getOmnicareFee() {
        if (fee == null) {
            return BigDecimal.ZERO;
        }
        return fee.multiply(OMNICARE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getNetAmount() {
        if (fee == null) {
            return BigDecimal.ZERO;
        }
        return fee.subtract(getOmnicareFee()).setScale(2, RoundingMode.HALF_UP);
    }
}
