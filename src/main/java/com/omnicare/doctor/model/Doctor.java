package com.omnicare.doctor.model;

import com.omnicare.provider.model.Provider;
import com.omnicare.profile.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "doctor_details")
public class Doctor {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private Provider provider;

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "medical_license_number")
    private String medicalLicenseNumber;

    @Column(name = "service_radius")
    private Integer serviceRadius;

    @Column(name = "visit_price", precision = 10, scale = 2)
    private BigDecimal visitPrice;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "available_hours", length = 1000)
    private String availableHours;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    protected Doctor() {
    }

    public Doctor(Provider provider) {
        this.provider = provider;
    }

    public UUID getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public String getMedicalLicenseNumber() {
        return medicalLicenseNumber;
    }

    public void setMedicalLicenseNumber(String medicalLicenseNumber) {
        this.medicalLicenseNumber = medicalLicenseNumber;
    }

    public Integer getServiceRadius() {
        return serviceRadius;
    }

    public void setServiceRadius(Integer serviceRadius) {
        this.serviceRadius = serviceRadius;
    }

    public BigDecimal getVisitPrice() {
        return visitPrice;
    }

    public void setVisitPrice(BigDecimal visitPrice) {
        this.visitPrice = visitPrice;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvailableHours() {
        return availableHours;
    }

    public void setAvailableHours(String availableHours) {
        this.availableHours = availableHours;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }
}
