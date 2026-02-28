package com.omnicare.patient.model;

import com.omnicare.profile.model.User;
import com.omnicare.patient.PatientAllergySeverity;
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
@Table(name = "patient_allergies")
public class PatientAllergy {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "substance", nullable = false)
    private String substance;

    @Column(name = "reaction")
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private PatientAllergySeverity severity = PatientAllergySeverity.UNKNOWN;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    protected PatientAllergy() {
    }

    public PatientAllergy(Patient patient, String substance) {
        this.patient = patient;
        this.substance = substance;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getSubstance() {
        return substance;
    }

    public void setSubstance(String substance) {
        this.substance = substance;
    }

    public String getReaction() {
        return reaction;
    }

    public void setReaction(String reaction) {
        this.reaction = reaction;
    }

    public PatientAllergySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(PatientAllergySeverity severity) {
        this.severity = severity;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }
}
