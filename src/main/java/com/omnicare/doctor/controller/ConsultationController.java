package com.omnicare.doctor.controller;

import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientAllergy;
import com.omnicare.patient.repository.PatientAllergyRepository;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.patient.service.PatientService;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationCancellationReason;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.model.ConsultationLocationType;
import com.omnicare.doctor.service.DoctorService;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.service.ProviderService;
import com.omnicare.access.service.PatientAccessService;
import com.omnicare.access.service.PatientAccessService.Scope;
import com.omnicare.audit.model.AuditEntityType;
import com.omnicare.audit.model.AuditLogAction;
import com.omnicare.audit.service.AuditLogService;
import com.omnicare.prescription.service.PrescriptionService;
import com.omnicare.prescription.model.Prescription;
import com.omnicare.prescription.model.PrescriptionItem;
import com.omnicare.doctor.service.ConsultationFinancialService;
import com.omnicare.doctor.model.ConsultationMedicalAct;
import com.omnicare.doctor.repository.ConsultationMedicalActRepository;
import com.omnicare.medicalact.model.MedicalActCatalog;
import com.omnicare.medicalact.repository.MedicalActCatalogRepository;
import com.omnicare.body.repository.BodyPartCatalogRepository;
import com.omnicare.kine.model.TreatmentPlan;
import com.omnicare.kine.repository.TreatmentPlanRepository;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    private static final Logger log = LoggerFactory.getLogger(ConsultationController.class);

    private final UserRepository userRepository;
    private final DoctorService doctorService;
    private final ProviderService providerService;
    private final DoctorRepository doctorRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientService patientService;
    private final PatientRepository patientRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final PrescriptionService prescriptionService;
    private final PatientAccessService patientAccessService;
    private final AuditLogService auditLogService;
    private final ConsultationFinancialService consultationFinancialService;
    private final MedicalActCatalogRepository medicalActCatalogRepository;
    private final ConsultationMedicalActRepository consultationMedicalActRepository;
    private final BodyPartCatalogRepository bodyPartCatalogRepository;
    private final TreatmentPlanRepository treatmentPlanRepository;

    public ConsultationController(
            UserRepository userRepository,
            DoctorService doctorService,
            ProviderService providerService,
            DoctorRepository doctorRepository,
            ConsultationRepository consultationRepository,
            PatientService patientService,
            PatientRepository patientRepository,
            PatientAllergyRepository patientAllergyRepository,
            PrescriptionService prescriptionService,
            PatientAccessService patientAccessService,
            AuditLogService auditLogService,
            ConsultationFinancialService consultationFinancialService,
            MedicalActCatalogRepository medicalActCatalogRepository,
            ConsultationMedicalActRepository consultationMedicalActRepository,
            BodyPartCatalogRepository bodyPartCatalogRepository,
            TreatmentPlanRepository treatmentPlanRepository
    ) {
        this.userRepository = userRepository;
        this.doctorService = doctorService;
        this.providerService = providerService;
        this.doctorRepository = doctorRepository;
        this.consultationRepository = consultationRepository;
        this.patientService = patientService;
        this.patientRepository = patientRepository;
        this.patientAllergyRepository = patientAllergyRepository;
        this.prescriptionService = prescriptionService;
        this.patientAccessService = patientAccessService;
        this.auditLogService = auditLogService;
        this.consultationFinancialService = consultationFinancialService;
        this.medicalActCatalogRepository = medicalActCatalogRepository;
        this.consultationMedicalActRepository = consultationMedicalActRepository;
        this.bodyPartCatalogRepository = bodyPartCatalogRepository;
        this.treatmentPlanRepository = treatmentPlanRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ConsultationFlowResponse> list(Authentication authentication, @RequestParam(value = "status", required = false) ConsultationStatus status) {
        User actor = requireUser(authentication);

        if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            Provider provider = providerService.ensureForProfessionalUser(actor);
            List<Consultation> rows = (status == null)
                    ? consultationRepository.findAllByProviderIdOrderByTimestampDesc(provider.getId())
                    : consultationRepository.findAllByProviderIdAndStatusOrderByTimestampDesc(provider.getId(), status);
            return rows.stream()
                    .filter(c -> {
                        if (c == null || c.getPatient() == null || c.getPatient().getId() == null) {
                            return false;
                        }
                        patientAccessService.requireProviderAccess(actor, c.getPatient().getId(), Scope.CONSULTATIONS_READ);
                        return true;
                    })
                    .peek(consultationFinancialService::apply)
                    .map(c -> ConsultationFlowResponse.from(c, computeAllergyWarning(c)))
                    .toList();
        }

        if (actor.getRole() == UserRole.PATIENT) {
            final List<UUID> ownedPatientIds = patientRepository.findAllByOwnerUserId(actor.getId()).stream()
                    .filter(Objects::nonNull)
                    .map(Patient::getId)
                    .filter(Objects::nonNull)
                    .toList();

            if (ownedPatientIds.isEmpty()) {
                return List.of();
            }

            List<Consultation> rows = ownedPatientIds.stream()
                    .flatMap(pid -> consultationRepository.findAllByPatientIdOrderByTimestampDesc(pid).stream())
                    .toList();
            if (status != null) {
                rows = rows.stream().filter(c -> status == c.getStatus()).toList();
            }
            return rows.stream()
                    .peek(consultationFinancialService::apply)
                    .sorted((a, b) -> {
                        Instant ta = a == null ? null : a.getTimestamp();
                        Instant tb = b == null ? null : b.getTimestamp();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta);
                    })
                    .map(c -> ConsultationFlowResponse.from(c, computeAllergyWarning(c)))
                    .toList();
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public record CreateConsultationRequest(
            UUID doctorId,
            UUID providerId,
            UUID patientId,
            String symptoms,
            Integer painLevel,
            List<String> affectedAreas,
            ConsultationLocationType locationType,
            UUID treatmentPlanId,
            String streetAddress,
            String apartmentSuite,
            String city,
            Double latitude,
            Double longitude,
            BigDecimal basePrice,
            BigDecimal fee,
            List<UUID> medicalActIds,
            String otherMedicalActText
    ) {
    }

    public record PatchConsultationRequest(
            ConsultationStatus status,
            ConsultationCancellationReason cancellationReason,
            String diagnosis,
            String treatment,
            java.math.BigDecimal fee
    ) {
    }

    public record ConsultationFlowResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("doctor_id") UUID doctorId,
            @JsonProperty("doctor_name") String doctorName,
            @JsonProperty("doctor_specialty") String doctorSpecialty,
            @JsonProperty("provider_id") UUID providerId,
            @JsonProperty("provider_type") String providerType,
            @JsonProperty("patient_id") UUID patientId,
            @JsonProperty("patient_name") String patientName,
            @JsonProperty("patient_age") Integer patientAge,
            @JsonProperty("patient_gender") String patientGender,
            @JsonProperty("height") Double height,
            @JsonProperty("weight") Double weight,
            @JsonProperty("blood_group") String bloodGroup,
            @JsonProperty("relationship") String relationship,
            @JsonProperty("symptoms") String symptoms,
            @JsonProperty("diagnosis") String diagnosis,
            @JsonProperty("treatment") String treatment,
            @JsonProperty("status") ConsultationStatus status,
            @JsonProperty("cancellation_reason") ConsultationCancellationReason cancellationReason,
            @JsonProperty("cancelled_at") Instant cancelledAt,
            @JsonProperty("cancelled_by_user_id") UUID cancelledByUserId,
            @JsonProperty("pain_level") Integer painLevel,
            @JsonProperty("affected_areas") List<String> affectedAreas,
            @JsonProperty("street_address") String streetAddress,
            @JsonProperty("apartment_suite") String apartmentSuite,
            @JsonProperty("city") String city,
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("longitude") Double longitude,
            @JsonProperty("fee") BigDecimal fee,
            @JsonProperty("net_amount") BigDecimal netAmount,
            @JsonProperty("omnicare_fee") BigDecimal omnicareFee,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("allergy_warning") String allergyWarning
    ) {
        public static ConsultationFlowResponse from(Consultation c, String allergyWarning) {
            List<String> affectedAreas = c.getAffectedAreas() == null ? List.of() : List.copyOf(c.getAffectedAreas());

            String doctorName = null;
            String doctorSpecialty = null;
            if (c.getDoctor() != null) {
                doctorSpecialty = c.getDoctor().getSpecialty();
                if (c.getDoctor().getProvider() != null && c.getDoctor().getProvider().getUser() != null) {
                    doctorName = c.getDoctor().getProvider().getUser().getName();
                }
            }

            UUID providerId = null;
            String providerType = null;
            if (c.getProvider() != null) {
                providerId = c.getProvider().getId();
                if (c.getProvider().getType() != null) {
                    providerType = c.getProvider().getType().name();
                }
            }

            PatientDetails patientDetails = PatientDetails.from(c.getPatient());

            ConsultationCancellationReason cancellationReason = null;
            Instant cancelledAt = null;
            UUID cancelledByUserId = null;
            if (c != null) {
                cancellationReason = c.getCancellationReason();
                cancelledAt = c.getCancelledAt();
                if (c.getCancelledByUser() != null) {
                    cancelledByUserId = c.getCancelledByUser().getId();
                }
            }

            return new ConsultationFlowResponse(
                    c.getId(),
                    c.getDoctor() == null ? null : c.getDoctor().getId(),
                    doctorName,
                    doctorSpecialty,
                    providerId,
                    providerType,
                    patientDetails.patientId(),
                    patientDetails.patientName(),
                    patientDetails.patientAge(),
                    patientDetails.patientGender(),
                    patientDetails.height(),
                    patientDetails.weight(),
                    patientDetails.bloodGroup(),
                    patientDetails.relationship(),
                    c.getSymptoms(),
                    c.getDiagnosis(),
                    c.getTreatment(),
                    c.getStatus(),
                    cancellationReason,
                    cancelledAt,
                    cancelledByUserId,
                    c.getPainLevel(),
                    affectedAreas,
                    c.getStreetAddress(),
                    c.getApartmentSuite(),
                    c.getCity(),
                    c.getLatitude(),
                    c.getLongitude(),
                    c.getFee(),
                    c.getNetAmount(),
                    c.getOmnicareFee(),
                    c.getTimestamp(),
                    allergyWarning
            );
        }

        private record PatientDetails(
                UUID patientId,
                String patientName,
                Integer patientAge,
                String patientGender,
                Double height,
                Double weight,
                String bloodGroup,
                String relationship
        ) {
            static PatientDetails from(Patient p) {
                if (p == null) {
                    return new PatientDetails(null, null, null, null, null, null, null, null);
                }

                UUID id = p.getId();
                String name = null;
                LocalDate dob = null;
                String gender = null;
                String blood = null;
                Map<String, Object> medicalInfo = null;
                String relationship = null;

                if (p.getUser() != null) {
                    name = p.getUser().getName();
                    dob = p.getUser().getDateOfBirth();
                    gender = p.getUser().getGender();
                    blood = p.getUser().getBloodGroup() == null ? null : p.getUser().getBloodGroup().name();
                    medicalInfo = p.getUser().getMedicalInfo();
                    relationship = "SELF";
                } else if (p.getFamilyMember() != null) {
                    name = p.getFamilyMember().getFullName();
                    dob = p.getFamilyMember().getBirthDate();
                    gender = p.getFamilyMember().getGender();
                    blood = p.getFamilyMember().getBloodGroup() == null ? null : p.getFamilyMember().getBloodGroup().name();
                    medicalInfo = p.getFamilyMember().getMedicalInfo();
                    relationship = p.getFamilyMember().getRelationship();
                } else if (p.getOwnerUser() != null) {
                    name = p.getOwnerUser().getName();
                }

                Integer age = computeAge(dob);
                Double height = readDoubleFromMedicalInfo(medicalInfo, "height");
                Double weight = readDoubleFromMedicalInfo(medicalInfo, "weight");

                return new PatientDetails(id, name, age, gender, height, weight, blood, relationship);
            }

            private static Integer computeAge(LocalDate dob) {
                if (dob == null) return null;
                return Period.between(dob, LocalDate.now()).getYears();
            }

            private static Double readDoubleFromMedicalInfo(Map<String, Object> medicalInfo, String key) {
                if (medicalInfo == null || key == null) return null;
                Object raw = medicalInfo.get(key);
                if (raw == null) return null;
                if (raw instanceof Number n) {
                    return n.doubleValue();
                }
                if (raw instanceof String s) {
                    try {
                        String trimmed = s.trim();
                        return trimmed.isEmpty() ? null : Double.parseDouble(trimmed);
                    } catch (Exception ignored) {
                        return null;
                    }
                }
                return null;
            }
        }
    }

    public record ConsultationPrescriptionItemResponse(
            UUID id,
            UUID medicationId,
            String medicationName,
            java.math.BigDecimal doseAmount,
            String doseUnit,
            int frequencyTimes,
            int frequencyPeriodDays,
            int durationDays,
            java.time.LocalDate startDate,
            String instructions
    ) {
        static ConsultationPrescriptionItemResponse from(PrescriptionItem i) {
            return new ConsultationPrescriptionItemResponse(
                    i.getId(),
                    i.getMedication() == null ? null : i.getMedication().getId(),
                    i.getMedication() == null ? null : i.getMedication().getName(),
                    i.getDoseAmount(),
                    i.getDoseUnit(),
                    i.getFrequencyTimes(),
                    i.getFrequencyPeriodDays(),
                    i.getDurationDays(),
                    i.getStartDate(),
                    i.getInstructions()
            );
        }
    }

    public record ConsultationPrescriptionResponse(
            UUID id,
            UUID consultationId,
            UUID patientId,
            UUID prescriberUserId,
            Instant issuedAt,
            com.omnicare.prescription.model.PrescriptionStatus status,
            String notes,
            List<ConsultationPrescriptionItemResponse> items
    ) {
        static ConsultationPrescriptionResponse from(Prescription p) {
            UUID prescriberId = p.getPrescriberUser() == null ? null : p.getPrescriberUser().getId();
            UUID consultationId = p.getConsultation() == null ? null : p.getConsultation().getId();
            return new ConsultationPrescriptionResponse(
                    p.getId(),
                    consultationId,
                    p.getPatient() == null ? null : p.getPatient().getId(),
                    prescriberId,
                    p.getIssuedAt(),
                    p.getStatus(),
                    p.getNotes(),
                    (p.getItems() == null ? List.of() : p.getItems().stream().map(ConsultationPrescriptionItemResponse::from).toList())
            );
        }
    }

    @PostMapping
    @Transactional
    public ConsultationFlowResponse create(Authentication authentication, @RequestBody CreateConsultationRequest request) {
        User actor = requireUser(authentication);

        final boolean isDoctorActor = actor.getRole() == UserRole.DOCTOR;
        final boolean isProfessionalActor = ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN;
        final boolean isPatientActor = actor.getRole() == UserRole.PATIENT;
        if (!isProfessionalActor && !isPatientActor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        if (request.symptoms() == null || request.symptoms().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symptoms is required");
        }

        if (request.painLevel() != null && (request.painLevel() < 1 || request.painLevel() > 10)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "painLevel must be between 1 and 10");
        }

        Provider provider;
        Doctor doctor = null;

        if (isProfessionalActor) {
            Provider actorProvider = providerService.ensureForProfessionalUser(actor);
            if (request.providerId() != null && !request.providerId().equals(actorProvider.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "providerId must match authenticated provider");
            }
            provider = actorProvider;
            if (isDoctorActor) {
                doctor = doctorService.ensureForDoctorUser(actor);
            }
        } else {
            // patient actor chooses a provider (preferred) or a doctor (legacy)
            if (request.providerId() != null) {
                provider = providerService.requireById(request.providerId());
            } else if (request.doctorId() != null) {
                Doctor chosen = doctorRepository.findById(request.doctorId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
                doctor = chosen;
                if (chosen.getProvider() == null || chosen.getProvider().getUser() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor provider user missing");
                }
                provider = providerService.ensureForProfessionalUser(chosen.getProvider().getUser());
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId or doctorId is required");
            }
        }

        Patient patient;
        if (isProfessionalActor) {
            if (request.patientId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
            }
            patient = patientRepository.findById(request.patientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

            patientAccessService.requireProviderAccess(actor, patient.getId(), Scope.CONSULTATIONS_WRITE);
        } else {
            if (request.patientId() != null) {
                patient = patientRepository.findByIdAndOwnerUserId(request.patientId(), actor.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
            } else {
                patient = patientService.ensureForUser(actor);
            }
        }

        Consultation c = new Consultation();
        c.setDoctor(doctor);
        c.setProvider(provider);
        c.setPatient(patient);
        c.setSymptoms(request.symptoms().trim());
        c.setStatus(ConsultationStatus.PENDING);

        if (provider.getType() == ProviderType.KINE) {
            c.setLocationType(request.locationType() == null ? ConsultationLocationType.CLINIC : request.locationType());
            if (request.treatmentPlanId() != null) {
                TreatmentPlan plan = treatmentPlanRepository.findById(request.treatmentPlanId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid treatmentPlanId"));
                if (plan.getPatient() == null || plan.getPatient().getId() == null || !plan.getPatient().getId().equals(patient.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "treatmentPlanId does not belong to patient");
                }
                if (plan.getProvider() == null || plan.getProvider().getId() == null || !plan.getProvider().getId().equals(provider.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "treatmentPlanId does not belong to provider");
                }
                c.setTreatmentPlan(plan);
            }
        } else {
            if (request.locationType() != null) {
                c.setLocationType(request.locationType());
            }
            if (request.treatmentPlanId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "treatmentPlanId is only supported for kine consultations");
            }
        }

        List<MedicalActCatalog> selectedActs = List.of();
        if (provider.getType() == ProviderType.NURSE) {
            if (request.medicalActIds() == null || request.medicalActIds().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalActIds is required for nurse consultations");
            }

            List<UUID> cleanedIds = request.medicalActIds().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (cleanedIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalActIds is required for nurse consultations");
            }

            selectedActs = medicalActCatalogRepository.findAllById(cleanedIds);
            if (selectedActs.size() != cleanedIds.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more medicalActIds are invalid");
            }

            boolean hasOther = selectedActs.stream().anyMatch(a -> a != null && a.getCode() != null && a.getCode().equalsIgnoreCase("NURSE_OTHER_COMPLEX_CARE"));
            String otherText = request.otherMedicalActText();
            if (hasOther) {
                if (otherText == null || otherText.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otherMedicalActText is required when selecting Other / Complex Care");
                }
                c.setOtherMedicalActText(otherText.trim());
            } else {
                if (otherText != null && !otherText.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otherMedicalActText is only allowed when selecting Other / Complex Care");
                }
            }

            BigDecimal computedFee = BigDecimal.ZERO;
            for (MedicalActCatalog act : selectedActs) {
                if (act == null || act.getBasePrice() == null) {
                    continue;
                }
                computedFee = computedFee.add(act.getBasePrice());
            }
            c.setFee(computedFee);
        } else {
            if (request.basePrice() != null) {
                c.setFee(request.basePrice());
            } else if (request.fee() != null) {
                c.setFee(request.fee());
            }
        }

        c.setPainLevel(request.painLevel());

        if (request.affectedAreas() != null) {
            List<String> cleaned = request.affectedAreas().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            if (!cleaned.isEmpty()) {
                long known = bodyPartCatalogRepository.countByKeyIgnoreCaseIn(cleaned);
                if (known != cleaned.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more affectedAreas keys are invalid");
                }
            }
            c.setAffectedAreas(cleaned);
        }

        if (request.streetAddress() != null) {
            String trimmed = request.streetAddress().trim();
            c.setStreetAddress(trimmed.isEmpty() ? null : trimmed);
        }
        if (request.apartmentSuite() != null) {
            String trimmed = request.apartmentSuite().trim();
            c.setApartmentSuite(trimmed.isEmpty() ? null : trimmed);
        }
        if (request.city() != null) {
            String trimmed = request.city().trim();
            c.setCity(trimmed.isEmpty() ? null : trimmed);
        }
        c.setLatitude(request.latitude());
        c.setLongitude(request.longitude());

        c.setTimestamp(Instant.now());

        consultationFinancialService.apply(c);

        // patient initiating consultation grants provider access (revocable by both parties)
        if (isPatientActor) {
            patientAccessService.grantAccessFromPatientToProvider(actor, patient.getId(), provider.getId());
        }

        Consultation saved = consultationRepository.save(c);

        if (provider.getType() == ProviderType.NURSE && selectedActs != null && !selectedActs.isEmpty()) {
            List<ConsultationMedicalAct> rows = new ArrayList<>();
            for (MedicalActCatalog act : selectedActs) {
                rows.add(new ConsultationMedicalAct(saved, act));
            }
            consultationMedicalActRepository.saveAll(rows);
        }
        return ConsultationFlowResponse.from(saved, null);
    }

    @PatchMapping("/{id}")
    @Transactional
    public ConsultationFlowResponse patch(Authentication authentication, @PathVariable("id") UUID id, @RequestBody PatchConsultationRequest request) {
        User actor = requireUser(authentication);

        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);
        final UUID actorId = actor == null ? null : actor.getId();
        final UUID providerId = provider == null ? null : provider.getId();

        Consultation c = consultationRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found");
        }

        if (c.getPatient() == null || c.getPatient().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultation patient missing");
        }
        final UUID patientId = c.getPatient().getId();
        log.info(
                "[ConsultationController.patch] actorId={} providerId={} consultationId={} patientId={}", 
                actorId,
                providerId,
                id,
                patientId
        );
        boolean bypassAccess = false;
        if (actor.getRole() == UserRole.DOCTOR && c.getDoctor() != null && c.getDoctor().getId() != null) {
            Doctor actorDoctor = doctorService.ensureForDoctorUser(actor);
            bypassAccess = actorDoctor != null && actorDoctor.getId() != null && actorDoctor.getId().equals(c.getDoctor().getId());
        }
        if (!bypassAccess) {
            patientAccessService.requireProviderAccess(actor, patientId, Scope.CONSULTATIONS_WRITE);
        }

        if (request != null) {
            if (request.status() != null) {
                c.setStatus(request.status());
            }
            if (request.fee() != null) {
                if (request.fee().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fee must be positive");
                }
                c.setFee(request.fee());
            }
            if (request.status() == ConsultationStatus.CANCELLED) {
                ConsultationCancellationReason finalReason = request.cancellationReason() == null
                        ? defaultCancellationReasonForActor(actor)
                        : request.cancellationReason();
                c.setCancellationReason(finalReason);
                c.setCancelledAt(Instant.now());
                c.setCancelledByUser(actor);
                auditLogService.log(actor, AuditEntityType.CONSULTATION, c.getId(), AuditLogAction.CANCEL, finalReason.name());
            }
            if (request.diagnosis() != null) {
                String trimmed = request.diagnosis().trim();
                c.setDiagnosis(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.treatment() != null) {
                String trimmed = request.treatment().trim();
                c.setTreatment(trimmed.isEmpty() ? null : trimmed);
            }
        }

        consultationFinancialService.apply(c);

        Consultation saved = consultationRepository.save(c);
        String warning = computeAllergyWarning(saved);
        return ConsultationFlowResponse.from(saved, warning);
    }

    private static ConsultationCancellationReason defaultCancellationReasonForActor(User actor) {
        if (actor == null || actor.getRole() == null) {
            return ConsultationCancellationReason.OTHER;
        }
        if (actor.getRole() == UserRole.PATIENT) {
            return ConsultationCancellationReason.PATIENT_CANCELLED;
        }
        if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            return ConsultationCancellationReason.PROVIDER_CANCELLED;
        }
        return ConsultationCancellationReason.OTHER;
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public ConsultationFlowResponse complete(Authentication authentication, @PathVariable("id") UUID id, @RequestBody PatchConsultationRequest request) {
        User actor = requireUser(authentication);

        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);
        final UUID actorId = actor == null ? null : actor.getId();
        final UUID providerId = provider == null ? null : provider.getId();

        Consultation c = consultationRepository.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

        if (c.getPatient() == null || c.getPatient().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultation patient missing");
        }
        final UUID patientId = c.getPatient().getId();
        log.info(
                "[ConsultationController.complete] actorId={} providerId={} consultationId={} patientId={}",
                actorId,
                providerId,
                id,
                patientId
        );
        boolean bypassAccess = false;
        if (actor.getRole() == UserRole.DOCTOR && c.getDoctor() != null && c.getDoctor().getId() != null) {
            Doctor actorDoctor = doctorService.ensureForDoctorUser(actor);
            bypassAccess = actorDoctor != null && actorDoctor.getId() != null && actorDoctor.getId().equals(c.getDoctor().getId());
        }
        if (!bypassAccess) {
            patientAccessService.requireProviderAccess(actor, patientId, Scope.CONSULTATIONS_WRITE);
        }

        if (request != null) {
            if (request.diagnosis() != null) {
                String trimmed = request.diagnosis().trim();
                c.setDiagnosis(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.treatment() != null) {
                String trimmed = request.treatment().trim();
                c.setTreatment(trimmed.isEmpty() ? null : trimmed);
            }
        }

        c.setStatus(ConsultationStatus.COMPLETED);

        Consultation saved = consultationRepository.save(c);
        String warning = computeAllergyWarning(saved);
        return ConsultationFlowResponse.from(saved, warning);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ConsultationFlowResponse get(Authentication authentication, @PathVariable("id") UUID id) {
        User actor = requireUser(authentication);

        Consultation c;
        if (actor.getRole() == UserRole.PATIENT) {
            Patient p = patientService.ensureForUser(actor);
            c = consultationRepository.findByIdAndPatientId(id, p.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
        } else if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            Provider provider = providerService.ensureForProfessionalUser(actor);
            c = consultationRepository.findByIdAndProviderId(id, provider.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

            if (c.getPatient() == null || c.getPatient().getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultation patient missing");
            }
            boolean bypassAccess = false;
            if (actor.getRole() == UserRole.DOCTOR && c.getDoctor() != null && c.getDoctor().getId() != null) {
                Doctor actorDoctor = doctorService.ensureForDoctorUser(actor);
                bypassAccess = actorDoctor != null && actorDoctor.getId() != null && actorDoctor.getId().equals(c.getDoctor().getId());
            }
            if (!bypassAccess) {
                patientAccessService.requireProviderAccess(actor, c.getPatient().getId(), Scope.CONSULTATIONS_READ);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        consultationFinancialService.apply(c);
        return ConsultationFlowResponse.from(c, computeAllergyWarning(c));
    }

    @GetMapping("/{id}/prescription")
    @Transactional(readOnly = true)
    public ConsultationPrescriptionResponse getPrescription(Authentication authentication, @PathVariable("id") UUID id) {
        User actor = requireUser(authentication);

        if (actor.getRole() == UserRole.PATIENT) {
            Patient p = patientService.ensureForUser(actor);
            consultationRepository.findByIdAndPatientId(id, p.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
        } else if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            Provider provider = providerService.ensureForProfessionalUser(actor);
            Consultation c = consultationRepository.findByIdAndProviderId(id, provider.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

            if (c.getPatient() == null || c.getPatient().getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultation patient missing");
            }
            boolean bypassAccess = false;
            if (actor.getRole() == UserRole.DOCTOR && c.getDoctor() != null && c.getDoctor().getId() != null) {
                Doctor actorDoctor = doctorService.ensureForDoctorUser(actor);
                bypassAccess = actorDoctor != null && actorDoctor.getId() != null && actorDoctor.getId().equals(c.getDoctor().getId());
            }
            if (!bypassAccess) {
                patientAccessService.requireProviderAccess(actor, c.getPatient().getId(), Scope.CONSULTATIONS_READ);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Prescription p = prescriptionService.getForConsultationAsActor(id);
        return ConsultationPrescriptionResponse.from(p);
    }

    @PutMapping("/{id}/prescription")
    @Transactional
    public ConsultationPrescriptionResponse putPrescription(Authentication authentication, @PathVariable("id") UUID id, @RequestBody PrescriptionService.CreateRequest request) {
        User actor = requireUser(authentication);
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);
        ProviderType type = provider.getType();
        if (type != ProviderType.DOCTOR && type != ProviderType.PSYCHIATRIST) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Prescriber role required");
        }

        Consultation c = consultationRepository.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
        if (c.getPatient() == null || c.getPatient().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultation patient missing");
        }
        boolean bypassAccess = false;
        if (actor.getRole() == UserRole.DOCTOR && c.getDoctor() != null && c.getDoctor().getId() != null) {
            Doctor actorDoctor = doctorService.ensureForDoctorUser(actor);
            bypassAccess = actorDoctor != null && actorDoctor.getId() != null && actorDoctor.getId().equals(c.getDoctor().getId());
        }
        if (!bypassAccess) {
            patientAccessService.requireProviderAccess(actor, c.getPatient().getId(), Scope.CONSULTATIONS_WRITE);
        }

        Prescription p = prescriptionService.createOrReplaceForConsultationAsDoctor(id, actor.getId(), request);
        return ConsultationPrescriptionResponse.from(p);
    }

    private String computeAllergyWarning(Consultation c) {
        if (c == null || c.getPatient() == null) {
            return null;
        }
        if (c.getTreatment() == null || c.getTreatment().isBlank()) {
            return null;
        }

        String treatment = c.getTreatment().toLowerCase(Locale.ROOT);
        boolean treatmentLooksLikePenicillin = treatment.contains("amoxic") || treatment.contains("penic");
        if (!treatmentLooksLikePenicillin) {
            return null;
        }

        List<PatientAllergy> allergies = patientAllergyRepository.findAllByPatientIdOrderByRecordedAtDesc(c.getPatient().getId());
        for (PatientAllergy a : allergies) {
            if (a == null || a.getSubstance() == null) {
                continue;
            }
            String sub = a.getSubstance().toLowerCase(Locale.ROOT);
            if (sub.contains("penic")) {
                return "Allergy warning: patient has a Penicillin allergy; treatment may be unsafe.";
            }
        }
        return null;
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

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static void requirePatient(User user) {
        if (user.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient role required");
        }
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
