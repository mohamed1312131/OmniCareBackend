package com.omnicare.prescription;

import com.omnicare.medication.Medication;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "prescription_items")
public class PrescriptionItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "dose_amount")
    private BigDecimal doseAmount;

    @Column(name = "dose_unit")
    private String doseUnit;

    @Column(name = "frequency_times", nullable = false)
    private int frequencyTimes;

    @Column(name = "frequency_period_days", nullable = false)
    private int frequencyPeriodDays;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    protected PrescriptionItem() {
    }

    public PrescriptionItem(Medication medication, int frequencyTimes, int frequencyPeriodDays, int durationDays) {
        this.medication = medication;
        this.frequencyTimes = frequencyTimes;
        this.frequencyPeriodDays = frequencyPeriodDays;
        this.durationDays = durationDays;
    }

    public UUID getId() {
        return id;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public void setPrescription(Prescription prescription) {
        this.prescription = prescription;
    }

    public Medication getMedication() {
        return medication;
    }

    public BigDecimal getDoseAmount() {
        return doseAmount;
    }

    public void setDoseAmount(BigDecimal doseAmount) {
        this.doseAmount = doseAmount;
    }

    public String getDoseUnit() {
        return doseUnit;
    }

    public void setDoseUnit(String doseUnit) {
        this.doseUnit = doseUnit;
    }

    public int getFrequencyTimes() {
        return frequencyTimes;
    }

    public void setFrequencyTimes(int frequencyTimes) {
        this.frequencyTimes = frequencyTimes;
    }

    public int getFrequencyPeriodDays() {
        return frequencyPeriodDays;
    }

    public void setFrequencyPeriodDays(int frequencyPeriodDays) {
        this.frequencyPeriodDays = frequencyPeriodDays;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(int durationDays) {
        this.durationDays = durationDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
}
