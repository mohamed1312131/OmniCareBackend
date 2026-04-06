package com.omnicare.provider.model;

import com.omnicare.profile.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "providers")
public class Provider {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ProviderType type;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "service_radius_km")
    private Integer serviceRadiusKm;

    @Column(name = "total_reviews")
    private Integer totalReviews;

    @Column(name = "rating", nullable = false)
    private BigDecimal rating = new BigDecimal("5.0");

    @Column(name = "platform_fee_percentage", precision = 5, scale = 2)
    private BigDecimal platformFeePercentage;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    protected Provider() {
    }

    public Provider(User user, ProviderType type) {
        this.user = user;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ProviderType getType() {
        return type;
    }

    public void setType(ProviderType type) {
        this.type = type;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public Integer getServiceRadiusKm() {
        return serviceRadiusKm;
    }

    public void setServiceRadiusKm(Integer serviceRadiusKm) {
        this.serviceRadiusKm = serviceRadiusKm;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        if (rating != null) {
            this.rating = rating;
        }
    }

    public BigDecimal getPlatformFeePercentage() {
        return platformFeePercentage;
    }

    public void setPlatformFeePercentage(BigDecimal platformFeePercentage) {
        this.platformFeePercentage = platformFeePercentage;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
