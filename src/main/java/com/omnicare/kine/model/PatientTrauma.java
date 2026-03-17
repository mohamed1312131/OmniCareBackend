package com.omnicare.kine.model;

import com.omnicare.body.model.BodyPartCatalog;
import com.omnicare.patient.model.ChronicConditionCatalog;
import com.omnicare.patient.model.Patient;
import com.omnicare.profile.model.User;
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
@Table(name = "patient_traumas")
public class PatientTrauma {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "body_part_id", nullable = false)
    private BodyPartCatalog bodyPart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chronic_condition_id")
    private ChronicConditionCatalog chronicCondition;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_of_injury")
    private LocalDate dateOfInjury;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TraumaStatus status = TraumaStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    protected PatientTrauma() {
    }

    public PatientTrauma(Patient patient, BodyPartCatalog bodyPart, String type) {
        this.patient = patient;
        this.bodyPart = bodyPart;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public BodyPartCatalog getBodyPart() {
        return bodyPart;
    }

    public ChronicConditionCatalog getChronicCondition() {
        return chronicCondition;
    }

    public void setChronicCondition(ChronicConditionCatalog chronicCondition) {
        this.chronicCondition = chronicCondition;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDateOfInjury() {
        return dateOfInjury;
    }

    public void setDateOfInjury(LocalDate dateOfInjury) {
        this.dateOfInjury = dateOfInjury;
    }

    public TraumaStatus getStatus() {
        return status;
    }

    public void setStatus(TraumaStatus status) {
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
