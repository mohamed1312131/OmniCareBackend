package com.omnicare.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID> {

    List<MedicalDocument> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
}
