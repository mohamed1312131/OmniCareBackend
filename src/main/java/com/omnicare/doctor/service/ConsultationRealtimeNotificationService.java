package com.omnicare.doctor.service;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class ConsultationRealtimeNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationRealtimeNotificationService.class);
    private static final String DOCTORS_NEARBY_TOPIC = "/topic/doctors/nearby";

    private final SimpMessagingTemplate messagingTemplate;

    public ConsultationRealtimeNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public record ConsultationCreatedNotification(
            UUID consultationId,
            String symptoms,
            String patientName,
            UUID patientId,
            UUID providerId,
            String providerType,
            UUID doctorId,
            String status) {
    }

    public void publishPendingConsultationSaved(Consultation consultation) {
        if (!shouldBroadcast(consultation)) {
            return;
        }

        ConsultationCreatedNotification payload = new ConsultationCreatedNotification(
                consultation.getId(),
                consultation.getSymptoms(),
                consultation.getPatient() == null || consultation.getPatient().getUser() == null
                        ? null
                        : consultation.getPatient().getUser().getName(),
                consultation.getPatient() == null ? null : consultation.getPatient().getId(),
                consultation.getProvider() == null ? null : consultation.getProvider().getId(),
                consultation.getProvider() == null || consultation.getProvider().getType() == null
                        ? null
                        : consultation.getProvider().getType().name(),
                consultation.getDoctor() == null ? null : consultation.getDoctor().getId(),
                consultation.getStatus() == null ? null : consultation.getStatus().name());

        Runnable publishAction = () -> {
            messagingTemplate.convertAndSend(DOCTORS_NEARBY_TOPIC, payload);
            log.info("[ConsultationRealtimeNotificationService] broadcast consultationId={} topic={}",
                    consultation.getId(), DOCTORS_NEARBY_TOPIC);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAction.run();
                }
            });
        } else {
            publishAction.run();
        }
    }

    private boolean shouldBroadcast(Consultation consultation) {
        if (consultation == null || consultation.getId() == null) {
            return false;
        }
        if (consultation.getStatus() != ConsultationStatus.PENDING) {
            return false;
        }
        Provider provider = consultation.getProvider();
        if (provider == null || provider.getType() != ProviderType.DOCTOR) {
            return false;
        }
        return consultation.getSymptoms() != null && !consultation.getSymptoms().isBlank();
    }
}
