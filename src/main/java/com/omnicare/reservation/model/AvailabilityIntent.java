package com.omnicare.reservation.model;

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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "availability_intents")
public class AvailabilityIntent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_request_id", nullable = false)
    private ReservationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;

    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_window", nullable = false)
    private TimeWindow timeWindow = TimeWindow.ANYTIME;

    @Column(name = "exact_time")
    private LocalTime exactTime;

    protected AvailabilityIntent() {
    }

    public AvailabilityIntent(ReservationRequest request, TimeWindow timeWindow) {
        this.request = request;
        this.timeWindow = timeWindow == null ? TimeWindow.ANYTIME : timeWindow;
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

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalDate getSpecificDate() {
        return specificDate;
    }

    public void setSpecificDate(LocalDate specificDate) {
        this.specificDate = specificDate;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(TimeWindow timeWindow) {
        if (timeWindow != null) {
            this.timeWindow = timeWindow;
        }
    }

    public LocalTime getExactTime() {
        return exactTime;
    }

    public void setExactTime(LocalTime exactTime) {
        this.exactTime = exactTime;
    }
}
