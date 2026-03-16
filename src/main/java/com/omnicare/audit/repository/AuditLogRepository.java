package com.omnicare.audit.repository;

import com.omnicare.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByEntityIdOrderByOccurredAtDesc(UUID entityId);
}
