package com.omnicare.doctor.model;

import com.omnicare.patient.model.Patient;
import com.omnicare.provider.model.Provider;
import com.omnicare.profile.model.User;
import com.omnicare.kine.model.TreatmentPlan;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "consultations")
public class Consultation {

    public static final BigDecimal OMNICARE_FEE_RATE = new BigDecimal("0.15");

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "symptoms", columnDefinition = "text")
    private String symptoms;

    @Column(name = "other_medical_act_text", columnDefinition = "text")
    private String otherMedicalActText;

    @Column(name = "diagnosis", columnDefinition = "text")
    private String diagnosis;

    @Column(name = "treatment", columnDefinition = "text")
    private String treatment;

    @Column(name = "clinical_notes", columnDefinition = "text")
    private String clinicalNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConsultationStatus status = ConsultationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    private ConsultationCancellationReason cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledByUser;

    @Column(name = "fee", precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(name = "displacement_fee_amount", precision = 19, scale = 2)
    private BigDecimal displacementFeeAmount;

    @Column(name = "platform_fee_percentage_applied", precision = 5, scale = 2)
    private BigDecimal platformFeePercentageApplied;

    @Column(name = "platform_fee_amount", precision = 19, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "net_amount", precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type")
    private ConsultationLocationType locationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_plan_id")
    private TreatmentPlan treatmentPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "pain_level")
    private Integer painLevel;

    @ElementCollection
    @CollectionTable(name = "consultation_affected_areas", joinColumns = @JoinColumn(name = "consultation_id"))
    @Column(name = "area", nullable = false)
    private List<String> affectedAreas = new ArrayList<>();

    @Column(name = "street_address")
    private String streetAddress;

    @Column(name = "apartment_suite")
    private String apartmentSuite;

    @Column(name = "city")
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    public Consultation() {
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

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getOtherMedicalActText() {
        return otherMedicalActText;
    }

    public void setOtherMedicalActText(String otherMedicalActText) {
        this.otherMedicalActText = otherMedicalActText;
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

    public ConsultationCancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(ConsultationCancellationReason cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public User getCancelledByUser() {
        return cancelledByUser;
    }

    public void setCancelledByUser(User cancelledByUser) {
        this.cancelledByUser = cancelledByUser;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public BigDecimal getDisplacementFeeAmount() {
        return displacementFeeAmount;
    }

    public void setDisplacementFeeAmount(BigDecimal displacementFeeAmount) {
        this.displacementFeeAmount = displacementFeeAmount;
    }

    public ConsultationLocationType getLocationType() {
        return locationType;
    }

    public void setLocationType(ConsultationLocationType locationType) {
        this.locationType = locationType;
    }

    public TreatmentPlan getTreatmentPlan() {
        return treatmentPlan;
    }

    public void setTreatmentPlan(TreatmentPlan treatmentPlan) {
        this.treatmentPlan = treatmentPlan;
    }

    public BigDecimal getPlatformFeePercentageApplied() {
        return platformFeePercentageApplied;
    }

    public void setPlatformFeePercentageApplied(BigDecimal platformFeePercentageApplied) {
        this.platformFeePercentageApplied = platformFeePercentageApplied;
    }

    public BigDecimal getPlatformFeeAmount() {
        return platformFeeAmount;
    }

    public void setPlatformFeeAmount(BigDecimal platformFeeAmount) {
        this.platformFeeAmount = platformFeeAmount;
    }

    public BigDecimal getNetAmountStored() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
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

    public Integer getPainLevel() {
        return painLevel;
    }

    public void setPainLevel(Integer painLevel) {
        this.painLevel = painLevel;
    }

    public List<String> getAffectedAreas() {
        return affectedAreas;
    }

    public void setAffectedAreas(List<String> affectedAreas) {
        this.affectedAreas = affectedAreas == null ? new ArrayList<>() : new ArrayList<>(affectedAreas);
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getApartmentSuite() {
        return apartmentSuite;
    }

    public void setApartmentSuite(String apartmentSuite) {
        this.apartmentSuite = apartmentSuite;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
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
        if (platformFeeAmount != null) {
            return platformFeeAmount;
        }
        if (fee == null) {
            return BigDecimal.ZERO;
        }
        return fee.multiply(OMNICARE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getNetAmount() {
        if (netAmount != null) {
            return netAmount;
        }
        if (fee == null) {
            return BigDecimal.ZERO;
        }
        return fee.subtract(getOmnicareFee()).setScale(2, RoundingMode.HALF_UP);
    }
}
