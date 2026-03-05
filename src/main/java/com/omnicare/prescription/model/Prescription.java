package com.omnicare.prescription.model;

import com.omnicare.patient.model.Patient;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.profile.model.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_user_id")
    private User prescriberUser;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id", unique = true)
    private Consultation consultation;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PrescriptionStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionItem> items = new ArrayList<>();

    protected Prescription() {
    }

    public Prescription(Patient patient, User prescriberUser, Instant issuedAt) {
        this.patient = patient;
        this.prescriberUser = prescriberUser;
        this.issuedAt = issuedAt;
        this.status = PrescriptionStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public User getPrescriberUser() {
        return prescriberUser;
    }

    public void setPrescriberUser(User prescriberUser) {
        this.prescriberUser = prescriberUser;
    }

    public Consultation getConsultation() {
        return consultation;
    }

    public void setConsultation(Consultation consultation) {
        this.consultation = consultation;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public void setStatus(PrescriptionStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<PrescriptionItem> getItems() {
        return items;
    }

    public void clearItems() {
        if (this.items != null) {
            for (PrescriptionItem item : this.items) {
                if (item != null) {
                    item.setPrescription(null);
                }
            }
            this.items.clear();
        }
    }

    public void addItem(PrescriptionItem item) {
        this.items.add(item);
        item.setPrescription(this);
    }
}
