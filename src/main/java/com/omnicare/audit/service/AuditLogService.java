package com.omnicare.audit.service;

import com.omnicare.audit.model.AuditEntityType;
import com.omnicare.audit.model.AuditLog;
import com.omnicare.audit.model.AuditLogAction;
import com.omnicare.audit.repository.AuditLogRepository;
import com.omnicare.profile.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(User actor, AuditEntityType entityType, UUID entityId, AuditLogAction action, String message) {
        if (entityType == null || entityId == null || action == null) {
            return;
        }
        AuditLog row = new AuditLog(entityType, entityId, action);
        row.setActorUser(actor);
        if (message != null && !message.isBlank()) {
            row.setMessage(message.trim());
        }
        auditLogRepository.save(row);
    }
}
