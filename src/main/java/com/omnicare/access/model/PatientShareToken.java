package com.omnicare.access.model;

import com.omnicare.patient.model.Patient;
import com.omnicare.profile.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_share_tokens")
public class PatientShareToken {

    @Id
    @Column(name = "token", nullable = false, updatable = false)
    private UUID token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PatientShareToken() {
    }

    public PatientShareToken(UUID token, Patient patient, User createdByUser, Instant expiresAt) {
        this.token = token;
        this.patient = patient;
        this.createdByUser = createdByUser;
        this.expiresAt = expiresAt;
    }

    public UUID getToken() {
        return token;
    }

    public Patient getPatient() {
        return patient;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isExpired(Instant now) {
        return now != null && expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }
}
