package com.omnicare.reminder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "patient_reminder_times")
public class PatientReminderTime {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminder_id", nullable = false)
    private PatientReminder reminder;

    @Column(name = "time", nullable = false)
    private LocalTime time;

    protected PatientReminderTime() {
    }

    public PatientReminderTime(PatientReminder reminder, LocalTime time) {
        this.reminder = reminder;
        this.time = time;
    }

    public UUID getId() {
        return id;
    }

    public PatientReminder getReminder() {
        return reminder;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }
}
