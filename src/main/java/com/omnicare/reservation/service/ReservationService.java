package com.omnicare.reservation.service;

import com.omnicare.access.service.PatientAccessService;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.service.ConsultationFinancialService;
import com.omnicare.doctor.service.ConsultationRealtimeNotificationService;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.service.ProviderService;
import com.omnicare.reservation.model.AvailabilityIntent;
import com.omnicare.reservation.model.ProposedSlot;
import com.omnicare.reservation.model.ReservationRequest;
import com.omnicare.reservation.model.ReservationRequestStatus;
import com.omnicare.reservation.model.TimeWindow;
import com.omnicare.reservation.repository.AvailabilityIntentRepository;
import com.omnicare.reservation.repository.ProposedSlotRepository;
import com.omnicare.reservation.repository.ReservationRequestRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRequestRepository reservationRequestRepository;
    private final AvailabilityIntentRepository availabilityIntentRepository;
    private final ProposedSlotRepository proposedSlotRepository;
    private final PatientRepository patientRepository;
    private final ProviderService providerService;
    private final DoctorRepository doctorRepository;
    private final ConsultationRepository consultationRepository;
    private final ConsultationFinancialService consultationFinancialService;
    private final PatientAccessService patientAccessService;
    private final ConsultationRealtimeNotificationService consultationRealtimeNotificationService;
    private final ReservationRealtimeNotificationService reservationRealtimeNotificationService;

    public ReservationService(
            ReservationRequestRepository reservationRequestRepository,
            AvailabilityIntentRepository availabilityIntentRepository,
            ProposedSlotRepository proposedSlotRepository,
            PatientRepository patientRepository,
            ProviderService providerService,
            DoctorRepository doctorRepository,
            ConsultationRepository consultationRepository,
            ConsultationFinancialService consultationFinancialService,
            PatientAccessService patientAccessService,
            ConsultationRealtimeNotificationService consultationRealtimeNotificationService,
            ReservationRealtimeNotificationService reservationRealtimeNotificationService) {
        this.reservationRequestRepository = reservationRequestRepository;
        this.availabilityIntentRepository = availabilityIntentRepository;
        this.proposedSlotRepository = proposedSlotRepository;
        this.patientRepository = patientRepository;
        this.providerService = providerService;
        this.doctorRepository = doctorRepository;
        this.consultationRepository = consultationRepository;
        this.consultationFinancialService = consultationFinancialService;
        this.patientAccessService = patientAccessService;
        this.consultationRealtimeNotificationService = consultationRealtimeNotificationService;
        this.reservationRealtimeNotificationService = reservationRealtimeNotificationService;
    }

    public record AvailabilityIntentCreate(
            DayOfWeek dayOfWeek,
            LocalDate specificDate,
            TimeWindow timeWindow,
            LocalTime exactTime) {
    }

    public record CreateRequest(
            UUID patientId,
            UUID providerId,
            String visitType,
            Boolean isEmergency,
            LocalDate searchStartDate,
            LocalDate searchEndDate,
            String reason,
            List<AvailabilityIntentCreate> intents) {
    }

    public record ProposeSlotsRequest(List<LocalDateTime> slots) {
    }

    public record AcceptSlotRequest(UUID slotId) {
    }

    public record AcceptanceResult(ReservationRequest request, Consultation consultation) {
    }

    @Transactional
    public ReservationRequest create(User actor, CreateRequest request) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (actor.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient role required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        if (request.providerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }
        if (request.searchStartDate() == null || request.searchEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "searchStartDate and searchEndDate are required");
        }
        if (request.searchEndDate().isBefore(request.searchStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "searchEndDate must be >= searchStartDate");
        }

        List<AvailabilityIntentCreate> cleanedIntents = request.intents() == null
                ? List.of()
                : request.intents().stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (cleanedIntents.size() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly 3 distinct intents are required");
        }

        Patient patient;
        if (request.patientId() != null) {
            patient = patientRepository.findByIdAndOwnerUserId(request.patientId(), actor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        } else {
            patient = patientRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        }

        Provider provider = providerService.requireById(request.providerId());

        ReservationRequest row = new ReservationRequest(patient, provider, request.searchStartDate(),
                request.searchEndDate());
        if (request.reason() != null) {
            String trimmed = request.reason().trim();
            row.setReason(trimmed.isEmpty() ? null : trimmed);
        }

        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        Instant expiresAt = now.plus(Duration.ofDays(7));
        row.setExpiresAt(expiresAt);
        row.setStatus(ReservationRequestStatus.PENDING);

        ReservationRequest saved = reservationRequestRepository.save(row);

        for (AvailabilityIntentCreate i : cleanedIntents) {
            AvailabilityIntent intent = new AvailabilityIntent(saved,
                    i.timeWindow() == null ? TimeWindow.ANYTIME : i.timeWindow());
            intent.setDayOfWeek(i.dayOfWeek());
            intent.setSpecificDate(i.specificDate());
            intent.setExactTime(i.exactTime());

            boolean hasGeneral = intent.getDayOfWeek() != null;
            boolean hasSpecific = intent.getSpecificDate() != null;
            if (hasGeneral == hasSpecific) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each intent must specify exactly one of dayOfWeek or specificDate");
            }
            if (intent.getSpecificDate() != null
                    && (intent.getSpecificDate().isBefore(request.searchStartDate())
                            || intent.getSpecificDate().isAfter(request.searchEndDate()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Intent specificDate must be within the search range");
            }
            availabilityIntentRepository.save(intent);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ReservationRequest> listForActor(User actor, ReservationRequestStatus status) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        if (actor.getRole() == UserRole.PATIENT) {
            Patient patient = patientRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
            List<ReservationRequest> list = reservationRequestRepository
                    .findAllByPatientIdOrderByCreatedAtDesc(patient.getId());
            if (status != null) {
                return list.stream().filter(r -> r != null && r.getStatus() == status).toList();
            }
            return list;
        }

        if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            Provider provider = providerService.ensureForProfessionalUser(actor);
            if (status != null) {
                return reservationRequestRepository.findAllByProviderIdAndStatusOrderByCreatedAtDesc(provider.getId(),
                        status);
            }
            return reservationRequestRepository.findAllByProviderIdOrderByCreatedAtDesc(provider.getId());
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @Transactional
    public ReservationRequest proposeSlots(User actor, UUID requestId, ProposeSlotsRequest request) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }
        if (request == null || request.slots() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slots is required");
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);
        ReservationRequest rr = reservationRequestRepository.findByIdAndProviderId(requestId, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ReservationRequest not found"));

        touchExpiry(rr);
        if (rr.getStatus() == ReservationRequestStatus.EXPIRED
                || rr.getStatus() == ReservationRequestStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ReservationRequest is not active");
        }
        if (rr.getStatus() != ReservationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ReservationRequest must be PENDING to propose slots");
        }

        List<LocalDateTime> cleaned = request.slots().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (cleaned.size() != 1 && cleaned.size() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Exactly 1 or 3 distinct slots are required");
        }

        proposedSlotRepository.deleteAllByRequestId(rr.getId());
        if (cleaned.size() == 1 && matchesAnyExactIntent(rr, cleaned.get(0))) {
            ProposedSlot exactSlot = proposedSlotRepository.save(new ProposedSlot(rr, cleaned.get(0)));
            return confirmReservation(rr, exactSlot, rr.getPatient() == null ? null : rr.getPatient().getOwnerUser(),
                    false, true).request();
        }
        for (LocalDateTime dt : cleaned) {
            proposedSlotRepository.save(new ProposedSlot(rr, dt));
        }

        rr.setStatus(ReservationRequestStatus.PROPOSED);
        Instant now = Instant.now();
        rr.setUpdatedAt(now);
        rr.setProposalExpiresAt(now.plus(Duration.ofHours(48)));
        return reservationRequestRepository.save(rr);
    }

    @Transactional
    public AcceptanceResult acceptSlot(User actor, UUID requestId, AcceptSlotRequest request) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (actor.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient role required");
        }
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }
        if (request == null || request.slotId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotId is required");
        }

        Patient patient = patientRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        ReservationRequest rr = reservationRequestRepository.findByIdAndPatientId(requestId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ReservationRequest not found"));

        touchExpiry(rr);
        if (rr.getStatus() != ReservationRequestStatus.PROPOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ReservationRequest must be PROPOSED to accept a slot");
        }

        ProposedSlot slot = proposedSlotRepository.findByIdAndRequestId(request.slotId(), rr.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid slotId"));

        return confirmReservation(rr, slot, actor, true, false);
    }

    @Transactional
    public ReservationRequest cancelAsPatient(User actor, UUID requestId) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (actor.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient role required");
        }
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }

        Patient patient = patientRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        ReservationRequest rr = reservationRequestRepository.findByIdAndPatientId(requestId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ReservationRequest not found"));

        touchExpiry(rr);
        if (rr.getStatus() == ReservationRequestStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ReservationRequest is already confirmed");
        }
        rr.setStatus(ReservationRequestStatus.CANCELLED);
        rr.setUpdatedAt(Instant.now());
        return reservationRequestRepository.save(rr);
    }

    private boolean matchesAnyExactIntent(ReservationRequest rr, LocalDateTime slotDateTime) {
        if (rr == null || rr.getId() == null || slotDateTime == null) {
            return false;
        }
        return listIntents(rr.getId()).stream()
                .filter(Objects::nonNull)
                .anyMatch(intent -> intent.getSpecificDate() != null
                        && intent.getExactTime() != null
                        && LocalDateTime.of(intent.getSpecificDate(), intent.getExactTime()).equals(slotDateTime));
    }

    private AcceptanceResult confirmReservation(ReservationRequest rr, ProposedSlot slot, User accessGrantActor,
            boolean publishDoctorConsultationNotification, boolean publishPatientReservationNotification) {
        if (rr == null || rr.getPatient() == null || rr.getProvider() == null || slot == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ReservationRequest confirmation data is incomplete");
        }

        Instant now = Instant.now();
        slot.setAcceptedAt(now);
        proposedSlotRepository.save(slot);

        rr.setStatus(ReservationRequestStatus.CONFIRMED);
        rr.setUpdatedAt(now);
        rr.setProposalExpiresAt(null);
        reservationRequestRepository.save(rr);

        User grantActor = accessGrantActor != null ? accessGrantActor : rr.getPatient().getOwnerUser();
        if (grantActor != null) {
            patientAccessService.grantAccessFromPatientToProvider(grantActor, rr.getPatient().getId(),
                    rr.getProvider().getId());
        }

        Consultation saved = createConsultationForReservation(rr, rr.getPatient(), slot);
        if (publishDoctorConsultationNotification) {
            consultationRealtimeNotificationService.publishPendingConsultationSaved(saved);
        }
        if (publishPatientReservationNotification) {
            reservationRealtimeNotificationService.publishReservationConfirmed(rr, slot, saved);
        }
        return new AcceptanceResult(rr, saved);
    }

    private Consultation createConsultationForReservation(ReservationRequest rr, Patient patient, ProposedSlot slot) {
        Consultation c = new Consultation();
        Provider provider = rr.getProvider();
        if (provider != null
                && (provider.getType() == ProviderType.DOCTOR || provider.getType() == ProviderType.PSYCHIATRIST)) {
            Doctor doctor = doctorRepository.findByProviderId(provider.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Doctor profile not found for provider"));
            c.setDoctor(doctor);
        }
        c.setProvider(provider);
        c.setPatient(patient);
        c.setStatus(ConsultationStatus.PENDING);

        String symptoms = rr.getReason();
        c.setSymptoms(symptoms == null || symptoms.isBlank() ? "Reservation" : symptoms.trim());

        ZoneId zone = ZoneId.systemDefault();
        Instant ts = slot.getDateTime().atZone(zone).toInstant();
        c.setTimestamp(ts);

        consultationFinancialService.apply(c);

        return consultationRepository.save(c);
    }

    private void touchExpiry(ReservationRequest rr) {
        if (rr == null || rr.getId() == null) {
            return;
        }

        Instant now = Instant.now();
        if (rr.getStatus() == ReservationRequestStatus.CONFIRMED || rr.getStatus() == ReservationRequestStatus.CANCELLED
                || rr.getStatus() == ReservationRequestStatus.EXPIRED) {
            return;
        }

        if (rr.getExpiresAt() != null && now.isAfter(rr.getExpiresAt())) {
            rr.setStatus(ReservationRequestStatus.EXPIRED);
            rr.setUpdatedAt(now);
            reservationRequestRepository.save(rr);
            return;
        }

        if (rr.getStatus() == ReservationRequestStatus.PROPOSED && rr.getProposalExpiresAt() != null
                && now.isAfter(rr.getProposalExpiresAt())) {
            rr.setStatus(ReservationRequestStatus.EXPIRED);
            rr.setUpdatedAt(now);
            reservationRequestRepository.save(rr);
        }
    }

    @Transactional(readOnly = true)
    public List<AvailabilityIntent> listIntents(UUID requestId) {
        if (requestId == null) {
            return List.of();
        }
        return availabilityIntentRepository.findAllByRequestIdOrderByIdAsc(requestId);
    }

    @Transactional(readOnly = true)
    public List<ProposedSlot> listSlots(UUID requestId) {
        if (requestId == null) {
            return List.of();
        }
        return proposedSlotRepository.findAllByRequestIdOrderByDateTimeAsc(requestId);
    }
}
