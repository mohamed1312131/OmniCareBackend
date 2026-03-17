package com.omnicare.doctor.model;

import com.omnicare.medicalact.model.MedicalActCatalog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "consultation_medical_acts")
public class ConsultationMedicalAct {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_act_id", nullable = false)
    private MedicalActCatalog catalogAct;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "overridden_price")
    private BigDecimal overriddenPrice;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    protected ConsultationMedicalAct() {
    }

    public ConsultationMedicalAct(Consultation consultation, MedicalActCatalog catalogAct) {
        this.consultation = consultation;
        this.catalogAct = catalogAct;
        this.quantity = 1;
    }

    public UUID getId() {
        return id;
    }

    public Consultation getConsultation() {
        return consultation;
    }

    public MedicalActCatalog getCatalogAct() {
        return catalogAct;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getOverriddenPrice() {
        return overriddenPrice;
    }

    public void setOverriddenPrice(BigDecimal overriddenPrice) {
        this.overriddenPrice = overriddenPrice;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
