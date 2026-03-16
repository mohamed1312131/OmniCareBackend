package com.omnicare.access.model;

import com.omnicare.patient.model.Patient;
import com.omnicare.provider.model.Provider;
import com.omnicare.profile.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_provider_access")
public class PatientProviderAccess {

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
    @JoinColumn(name = "granted_by_user_id")
    private User grantedByUser;

    @Column(name = "can_read_passport", nullable = false)
    private boolean canReadPassport = true;

    @Column(name = "can_read_body_graph", nullable = false)
    private boolean canReadBodyGraph = true;

    @Column(name = "can_read_consultations", nullable = false)
    private boolean canReadConsultations = true;

    @Column(name = "can_write_consultations", nullable = false)
    private boolean canWriteConsultations = true;

    @Column(name = "can_read_reminders", nullable = false)
    private boolean canReadReminders = true;

    @Column(name = "can_write_reminders", nullable = false)
    private boolean canWriteReminders = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_user_id")
    private User revokedByUser;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_user_id")
    private User reportedByUser;

    protected PatientProviderAccess() {
    }

    public PatientProviderAccess(Patient patient, Provider provider, User grantedByUser) {
        this.patient = patient;
        this.provider = provider;
        this.grantedByUser = grantedByUser;
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

    public User getGrantedByUser() {
        return grantedByUser;
    }

    public boolean isCanReadPassport() {
        return canReadPassport;
    }

    public void setCanReadPassport(boolean canReadPassport) {
        this.canReadPassport = canReadPassport;
    }

    public boolean isCanReadBodyGraph() {
        return canReadBodyGraph;
    }

    public void setCanReadBodyGraph(boolean canReadBodyGraph) {
        this.canReadBodyGraph = canReadBodyGraph;
    }

    public boolean isCanReadConsultations() {
        return canReadConsultations;
    }

    public void setCanReadConsultations(boolean canReadConsultations) {
        this.canReadConsultations = canReadConsultations;
    }

    public boolean isCanWriteConsultations() {
        return canWriteConsultations;
    }

    public void setCanWriteConsultations(boolean canWriteConsultations) {
        this.canWriteConsultations = canWriteConsultations;
    }

    public boolean isCanReadReminders() {
        return canReadReminders;
    }

    public void setCanReadReminders(boolean canReadReminders) {
        this.canReadReminders = canReadReminders;
    }

    public boolean isCanWriteReminders() {
        return canWriteReminders;
    }

    public void setCanWriteReminders(boolean canWriteReminders) {
        this.canWriteReminders = canWriteReminders;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revoke(Instant at, User revokedBy) {
        this.revokedAt = at;
        this.revokedByUser = revokedBy;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public void report(Instant at, User reportedBy) {
        this.reportedAt = at;
        this.reportedByUser = reportedBy;
    }
}
