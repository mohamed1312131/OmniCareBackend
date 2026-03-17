package com.omnicare.body.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "body_parts_catalog")
public class BodyPartCatalog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "key", nullable = false, unique = true)
    private String key;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected BodyPartCatalog() {
    }

    public BodyPartCatalog(String key) {
        this.key = key;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
