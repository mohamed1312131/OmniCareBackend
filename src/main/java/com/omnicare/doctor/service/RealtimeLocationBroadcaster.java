package com.omnicare.doctor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeLocationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RealtimeLocationBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeLocationBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastToPatient(String patientId, LocationTrackingService.LocationUpdateMessage message) {
        try {
            String destination = "/topic/patient/" + patientId + "/location-updates";
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Broadcasted location update to patient {}: {}", patientId, message);
        } catch (Exception e) {
            log.error("Failed to broadcast location update to patient {}", patientId, e);
        }
    }

    public void broadcastToConsultation(String consultationId, Object message) {
        try {
            String destination = "/topic/consultation/" + consultationId + "/updates";
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Broadcasted message to consultation {}: {}", consultationId, message);
        } catch (Exception e) {
            log.error("Failed to broadcast message to consultation {}", consultationId, e);
        }
    }
}
