package com.omnicare.reservation.service;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.provider.model.Provider;
import com.omnicare.reservation.model.ProposedSlot;
import com.omnicare.reservation.model.ReservationRequest;
import com.omnicare.reservation.model.ReservationRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class ReservationRealtimeNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationRealtimeNotificationService.class);
    private static final String USER_RESERVATIONS_TOPIC_PREFIX = "/topic/users/";
    private static final String USER_RESERVATIONS_TOPIC_SUFFIX = "/reservations";

    private final SimpMessagingTemplate messagingTemplate;

    public ReservationRealtimeNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public record ReservationConfirmedNotification(
            String type,
            UUID reservationId,
            UUID consultationId,
            UUID patientId,
            UUID providerId,
            String providerName,
            String providerType,
            String scheduledAt,
            String status) {
    }

    public void publishReservationConfirmed(ReservationRequest request, ProposedSlot slot, Consultation consultation) {
        if (!shouldBroadcast(request, slot)) {
            return;
        }

        UUID ownerUserId = request.getPatient() == null || request.getPatient().getOwnerUser() == null
                ? null
                : request.getPatient().getOwnerUser().getId();
        if (ownerUserId == null) {
            return;
        }

        Provider provider = request.getProvider();
        ReservationConfirmedNotification payload = new ReservationConfirmedNotification(
                "reservation_confirmed",
                request.getId(),
                consultation == null ? null : consultation.getId(),
                request.getPatient() == null ? null : request.getPatient().getId(),
                provider == null ? null : provider.getId(),
                provider == null || provider.getUser() == null ? null : provider.getUser().getName(),
                provider == null || provider.getType() == null ? null : provider.getType().name(),
                slot == null || slot.getDateTime() == null ? null : slot.getDateTime().toString(),
                request.getStatus() == null ? null : request.getStatus().name());

        String topic = USER_RESERVATIONS_TOPIC_PREFIX + ownerUserId + USER_RESERVATIONS_TOPIC_SUFFIX;
        Runnable publishAction = () -> {
            messagingTemplate.convertAndSend(topic, payload);
            log.info("[ReservationRealtimeNotificationService] broadcast reservationId={} topic={}",
                    request.getId(), topic);
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

    private boolean shouldBroadcast(ReservationRequest request, ProposedSlot slot) {
        if (request == null || request.getId() == null || slot == null || slot.getDateTime() == null) {
            return false;
        }
        if (request.getStatus() != ReservationRequestStatus.CONFIRMED) {
            return false;
        }
        return request.getPatient() != null && request.getPatient().getOwnerUser() != null;
    }
}
