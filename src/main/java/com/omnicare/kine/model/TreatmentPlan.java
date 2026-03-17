package com.omnicare.kine.model;

import com.omnicare.patient.model.Patient;
import com.omnicare.profile.model.User;
import com.omnicare.provider.model.Provider;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "treatment_plans")
public class TreatmentPlan {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trauma_id")
    private PatientTrauma trauma;

    @Column(name = "total_sessions_prescribed", nullable = false)
    private Integer totalSessionsPrescribed;

    @Column(name = "sessions_completed", nullable = false)
    private Integer sessionsCompleted = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TreatmentPlanStatus status = TreatmentPlanStatus.IMPROVING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    protected TreatmentPlan() {
    }

    public TreatmentPlan(Patient patient, Provider provider, Integer totalSessionsPrescribed) {
        this.patient = patient;
        this.provider = provider;
        this.totalSessionsPrescribed = totalSessionsPrescribed;
        this.sessionsCompleted = 0;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Provider getProvider() {
        return provider;
    }

    public PatientTrauma getTrauma() {
        return trauma;
    }

    public void setTrauma(PatientTrauma trauma) {
        this.trauma = trauma;
    }

    public Integer getTotalSessionsPrescribed() {
        return totalSessionsPrescribed;
    }

    public void setTotalSessionsPrescribed(Integer totalSessionsPrescribed) {
        this.totalSessionsPrescribed = totalSessionsPrescribed;
    }

    public Integer getSessionsCompleted() {
        return sessionsCompleted;
    }

    public void setSessionsCompleted(Integer sessionsCompleted) {
        this.sessionsCompleted = sessionsCompleted;
    }

    public TreatmentPlanStatus getStatus() {
        return status;
    }

    public void setStatus(TreatmentPlanStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        if (createdAt != null) {
            this.createdAt = createdAt;
        }
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }
}
