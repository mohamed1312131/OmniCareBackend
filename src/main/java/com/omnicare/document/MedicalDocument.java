package com.omnicare.document;

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
@Table(name = "medical_documents")
public class MedicalDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private MedicalDocumentType type;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_public_id")
    private String filePublicId;

    @Column(name = "file_provider")
    private String fileProvider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MedicalDocument() {
    }

    public MedicalDocument(User ownerUser, String title, MedicalDocumentType type) {
        this.ownerUser = ownerUser;
        this.title = title;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(User ownerUser) {
        this.ownerUser = ownerUser;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public MedicalDocumentType getType() {
        return type;
    }

    public void setType(MedicalDocumentType type) {
        this.type = type;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFilePublicId() {
        return filePublicId;
    }

    public void setFilePublicId(String filePublicId) {
        this.filePublicId = filePublicId;
    }

    public String getFileProvider() {
        return fileProvider;
    }

    public void setFileProvider(String fileProvider) {
        this.fileProvider = fileProvider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
