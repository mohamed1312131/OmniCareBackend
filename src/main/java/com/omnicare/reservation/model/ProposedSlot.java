package com.omnicare.reservation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "proposed_slots")
public class ProposedSlot {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_request_id", nullable = false)
    private ReservationRequest request;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected ProposedSlot() {
    }

    public ProposedSlot(ReservationRequest request, LocalDateTime dateTime) {
        this.request = request;
        this.dateTime = dateTime;
    }

    public UUID getId() {
        return id;
    }

    public ReservationRequest getRequest() {
        return request;
    }

    public void setRequest(ReservationRequest request) {
        this.request = request;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
