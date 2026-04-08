package com.omnicare.reservation.controller;

import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.reservation.model.AvailabilityIntent;
import com.omnicare.reservation.model.ProposedSlot;
import com.omnicare.reservation.model.ReservationRequest;
import com.omnicare.reservation.model.ReservationRequestStatus;
import com.omnicare.reservation.service.ReservationService;
import com.omnicare.reservation.service.ReservationService.AcceptanceResult;
import com.omnicare.reservation.service.ReservationService.AcceptSlotRequest;
import com.omnicare.reservation.service.ReservationService.CreateRequest;
import com.omnicare.reservation.service.ReservationService.ProposeSlotsRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final UserRepository userRepository;
    private final ReservationService reservationService;

    public ReservationController(UserRepository userRepository, ReservationService reservationService) {
        this.userRepository = userRepository;
        this.reservationService = reservationService;
    }

    public record AvailabilityIntentRequest(
            java.time.DayOfWeek dayOfWeek,
            LocalDate specificDate,
            com.omnicare.reservation.model.TimeWindow timeWindow,
            LocalTime exactTime) {
    }

    public record CreateReservationRequest(
            UUID patientId,
            UUID providerId,
            String visitType,
            Boolean isEmergency,
            LocalDate searchStartDate,
            LocalDate searchEndDate,
            String reason,
            List<AvailabilityIntentRequest> intents) {
    }

    public record ProposeSlotsBody(List<LocalDateTime> slots) {
    }

    public record AcceptBody(UUID slotId) {
    }

    public record ProposedSlotResponse(UUID id, LocalDateTime dateTime, boolean accepted, Instant acceptedAt) {
        static ProposedSlotResponse from(ProposedSlot s) {
            return new ProposedSlotResponse(s.getId(), s.getDateTime(), s.isAccepted(), s.getAcceptedAt());
        }
    }

    public record AvailabilityIntentResponse(UUID id, java.time.DayOfWeek dayOfWeek, LocalDate specificDate,
            com.omnicare.reservation.model.TimeWindow timeWindow, LocalTime exactTime) {
        static AvailabilityIntentResponse from(AvailabilityIntent i) {
            return new AvailabilityIntentResponse(i.getId(), i.getDayOfWeek(), i.getSpecificDate(), i.getTimeWindow(),
                    i.getExactTime());
        }
    }

    public record ReservationResponse(
            UUID id,
            ReservationRequestStatus status,
            UUID patientId,
            UUID providerId,
            LocalDate searchStartDate,
            LocalDate searchEndDate,
            String reason,
            Instant expiresAt,
            Instant proposalExpiresAt,
            Instant createdAt,
            Instant updatedAt,
            List<AvailabilityIntentResponse> intents,
            List<ProposedSlotResponse> slots) {
        static ReservationResponse from(ReservationRequest r, List<AvailabilityIntent> intents,
                List<ProposedSlot> slots) {
            UUID patientId = r.getPatient() == null ? null : r.getPatient().getId();
            UUID providerId = r.getProvider() == null ? null : r.getProvider().getId();
            List<AvailabilityIntentResponse> i = intents == null ? List.of()
                    : intents.stream().map(AvailabilityIntentResponse::from).toList();
            List<ProposedSlotResponse> s = slots == null ? List.of()
                    : slots.stream().map(ProposedSlotResponse::from).toList();
            return new ReservationResponse(
                    r.getId(),
                    r.getStatus(),
                    patientId,
                    providerId,
                    r.getSearchStartDate(),
                    r.getSearchEndDate(),
                    r.getReason(),
                    r.getExpiresAt(),
                    r.getProposalExpiresAt(),
                    r.getCreatedAt(),
                    r.getUpdatedAt(),
                    i,
                    s);
        }
    }

    public record AcceptResponse(ReservationResponse reservation, UUID consultationId) {
    }

    @PostMapping("/requests")
    @Transactional
    public ReservationResponse create(Authentication authentication, @RequestBody CreateReservationRequest request) {
        User actor = requireUser(authentication);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }

        List<ReservationService.AvailabilityIntentCreate> intents = null;
        if (request.intents() != null) {
            intents = request.intents().stream().map(i -> new ReservationService.AvailabilityIntentCreate(
                    i == null ? null : i.dayOfWeek(),
                    i == null ? null : i.specificDate(),
                    i == null ? null : i.timeWindow(),
                    i == null ? null : i.exactTime())).toList();
        }

        ReservationRequest created = reservationService.create(actor, new CreateRequest(
                request.patientId(),
                request.providerId(),
                request.visitType(),
                request.isEmergency(),
                request.searchStartDate(),
                request.searchEndDate(),
                request.reason(),
                intents));

        return ReservationResponse.from(created, reservationService.listIntents(created.getId()),
                reservationService.listSlots(created.getId()));
    }

    @GetMapping("/requests")
    @Transactional(readOnly = true)
    public List<ReservationResponse> list(Authentication authentication,
            @RequestParam(value = "status", required = false) ReservationRequestStatus status) {
        User actor = requireUser(authentication);
        List<ReservationRequest> list = reservationService.listForActor(actor, status);
        return list.stream().map(r -> ReservationResponse.from(r, reservationService.listIntents(r.getId()),
                reservationService.listSlots(r.getId()))).toList();
    }

    @PostMapping("/requests/{requestId}/propose")
    @Transactional
    public ReservationResponse propose(Authentication authentication, @PathVariable UUID requestId,
            @RequestBody ProposeSlotsBody body) {
        User actor = requireUser(authentication);
        ReservationRequest rr = reservationService.proposeSlots(actor, requestId,
                new ProposeSlotsRequest(body == null ? null : body.slots()));
        return ReservationResponse.from(rr, reservationService.listIntents(rr.getId()),
                reservationService.listSlots(rr.getId()));
    }

    @PostMapping("/requests/{requestId}/accept")
    @Transactional
    public AcceptResponse accept(Authentication authentication, @PathVariable UUID requestId,
            @RequestBody AcceptBody body) {
        User actor = requireUser(authentication);
        AcceptanceResult result = reservationService.acceptSlot(actor, requestId,
                new AcceptSlotRequest(body == null ? null : body.slotId()));
        ReservationRequest rr = result.request();
        ReservationResponse response = ReservationResponse.from(rr, reservationService.listIntents(rr.getId()),
                reservationService.listSlots(rr.getId()));
        UUID consultationId = result.consultation() == null ? null : result.consultation().getId();
        return new AcceptResponse(response, consultationId);
    }

    @PostMapping("/requests/{requestId}/cancel")
    @Transactional
    public ReservationResponse cancel(Authentication authentication, @PathVariable UUID requestId) {
        User actor = requireUser(authentication);
        ReservationRequest rr = reservationService.cancelAsPatient(actor, requestId);
        return ReservationResponse.from(rr, reservationService.listIntents(rr.getId()),
                reservationService.listSlots(rr.getId()));
    }

    private User requireUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        User actor = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return actor;
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
