package com.omnicare.reservation.model;

import com.omnicare.patient.model.Patient;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reservation_requests")
public class ReservationRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationRequestStatus status = ReservationRequestStatus.PENDING;

    @Column(name = "search_start_date", nullable = false)
    private LocalDate searchStartDate;

    @Column(name = "search_end_date", nullable = false)
    private LocalDate searchEndDate;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "proposal_expires_at")
    private Instant proposalExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ReservationRequest() {
    }

    public ReservationRequest(Patient patient, Provider provider, LocalDate searchStartDate, LocalDate searchEndDate) {
        this.patient = patient;
        this.provider = provider;
        this.searchStartDate = searchStartDate;
        this.searchEndDate = searchEndDate;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public ReservationRequestStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationRequestStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public LocalDate getSearchStartDate() {
        return searchStartDate;
    }

    public void setSearchStartDate(LocalDate searchStartDate) {
        this.searchStartDate = searchStartDate;
    }

    public LocalDate getSearchEndDate() {
        return searchEndDate;
    }

    public void setSearchEndDate(LocalDate searchEndDate) {
        this.searchEndDate = searchEndDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getProposalExpiresAt() {
        return proposalExpiresAt;
    }

    public void setProposalExpiresAt(Instant proposalExpiresAt) {
        this.proposalExpiresAt = proposalExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
