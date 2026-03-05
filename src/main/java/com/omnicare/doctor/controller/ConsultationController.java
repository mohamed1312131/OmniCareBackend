package com.omnicare.doctor.controller;

import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientAllergy;
import com.omnicare.patient.repository.PatientAllergyRepository;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.patient.service.PatientService;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.service.DoctorService;
import com.omnicare.prescription.service.PrescriptionService;
import com.omnicare.prescription.model.Prescription;
import com.omnicare.prescription.model.PrescriptionItem;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    private final UserRepository userRepository;
    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientService patientService;
    private final PatientRepository patientRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final PrescriptionService prescriptionService;

    public ConsultationController(
            UserRepository userRepository,
            DoctorService doctorService,
            DoctorRepository doctorRepository,
            ConsultationRepository consultationRepository,
            PatientService patientService,
            PatientRepository patientRepository,
            PatientAllergyRepository patientAllergyRepository,
            PrescriptionService prescriptionService
    ) {
        this.userRepository = userRepository;
        this.doctorService = doctorService;
        this.doctorRepository = doctorRepository;
        this.consultationRepository = consultationRepository;
        this.patientService = patientService;
        this.patientRepository = patientRepository;
        this.patientAllergyRepository = patientAllergyRepository;
        this.prescriptionService = prescriptionService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ConsultationFlowResponse> list(Authentication authentication, @RequestParam(value = "status", required = false) ConsultationStatus status) {
        User actor = requireUser(authentication);

        if (actor.getRole() == UserRole.DOCTOR) {
            Doctor doctor = doctorService.ensureForDoctorUser(actor);
            List<Consultation> rows = (status == null)
                    ? consultationRepository.findAllByDoctorIdOrderByTimestampDesc(doctor.getId())
                    : consultationRepository.findAllByDoctorIdAndStatusOrderByTimestampDesc(doctor.getId(), status);
            return rows.stream().map(c -> ConsultationFlowResponse.from(c, computeAllergyWarning(c))).toList();
        }

        if (actor.getRole() == UserRole.PATIENT) {
            Patient patient = patientService.ensureForUser(actor);
            List<Consultation> rows = consultationRepository.findAllByPatientIdOrderByTimestampDesc(patient.getId());
            if (status != null) {
                rows = rows.stream().filter(c -> status == c.getStatus()).toList();
            }
            return rows.stream().map(c -> ConsultationFlowResponse.from(c, computeAllergyWarning(c))).toList();
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public record CreateConsultationRequest(
            UUID doctorId,
            UUID patientId,
            String symptoms,
            Integer painLevel,
            List<String> affectedAreas,
            String streetAddress,
            String apartmentSuite,
            String city,
            Double latitude,
            Double longitude,
            BigDecimal basePrice,
            BigDecimal fee
    ) {
    }

    public record PatchConsultationRequest(ConsultationStatus status, String diagnosis, String treatment) {
    }

    public record ConsultationFlowResponse(
            UUID id,
            UUID doctorId,
            String doctorName,
            String doctorSpecialty,
            UUID patientId,
            String patientName,
            String symptoms,
            String diagnosis,
            String treatment,
            ConsultationStatus status,
            Integer painLevel,
            List<String> affectedAreas,
            String streetAddress,
            String apartmentSuite,
            String city,
            Double latitude,
            Double longitude,
            BigDecimal fee,
            BigDecimal netAmount,
            BigDecimal omnicareFee,
            Instant timestamp,
            String allergyWarning
    ) {
        static ConsultationFlowResponse from(Consultation c, String allergyWarning) {
            List<String> affectedAreas = c.getAffectedAreas() == null ? List.of() : List.copyOf(c.getAffectedAreas());

            String doctorName = null;
            String doctorSpecialty = null;
            if (c.getDoctor() != null) {
                doctorSpecialty = c.getDoctor().getSpecialty();
                if (c.getDoctor().getUser() != null) {
                    doctorName = c.getDoctor().getUser().getName();
                }
            }

            String patientName = null;
            if (c.getPatient() != null) {
                if (c.getPatient().getUser() != null) {
                    patientName = c.getPatient().getUser().getName();
                } else if (c.getPatient().getOwnerUser() != null) {
                    patientName = c.getPatient().getOwnerUser().getName();
                }
            }

            return new ConsultationFlowResponse(
                    c.getId(),
                    c.getDoctor() == null ? null : c.getDoctor().getId(),
                    doctorName,
                    doctorSpecialty,
                    c.getPatient() == null ? null : c.getPatient().getId(),
                    patientName,
                    c.getSymptoms(),
                    c.getDiagnosis(),
                    c.getTreatment(),
                    c.getStatus(),
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
        final boolean isPatientActor = actor.getRole() == UserRole.PATIENT;
        if (!isDoctorActor && !isPatientActor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (request == null || request.doctorId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "doctorId is required");
        }
        if (request.symptoms() == null || request.symptoms().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symptoms is required");
        }

        if (request.painLevel() != null && (request.painLevel() < 1 || request.painLevel() > 10)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "painLevel must be between 1 and 10");
        }

        Doctor doctor;
        if (isDoctorActor) {
            Doctor actorDoctor = doctorService.ensureForDoctorUser(actor);
            if (request.doctorId() != null && !request.doctorId().equals(actorDoctor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "doctorId must match authenticated doctor");
            }
            doctor = actorDoctor;
        } else {
            doctor = doctorRepository.findById(request.doctorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        }

        Patient patient;
        if (isDoctorActor) {
            if (request.patientId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
            }
            patient = patientRepository.findById(request.patientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        } else {
            if (request.patientId() != null) {
                patient = patientRepository.findByIdAndOwnerUserId(request.patientId(), actor.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
            } else {
                patient = patientService.ensureForUser(actor);
            }
        }

        Consultation c = new Consultation(doctor);
        c.setPatient(patient);
        c.setSymptoms(request.symptoms().trim());
        c.setStatus(ConsultationStatus.PENDING);

        if (request.basePrice() != null) {
            c.setFee(request.basePrice());
        } else if (request.fee() != null) {
            c.setFee(request.fee());
        }

        c.setPainLevel(request.painLevel());

        if (request.affectedAreas() != null) {
            List<String> cleaned = request.affectedAreas().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
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

        Consultation saved = consultationRepository.save(c);
        return ConsultationFlowResponse.from(saved, null);
    }

    @PatchMapping("/{id}")
    @Transactional
    public ConsultationFlowResponse patch(Authentication authentication, @PathVariable("id") UUID id, @RequestBody PatchConsultationRequest request) {
        User actor = requireUser(authentication);
        requireDoctor(actor);

        Doctor doctor = doctorService.ensureForDoctorUser(actor);

        Consultation c = consultationRepository.findByIdAndDoctorId(id, doctor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

        if (request != null) {
            if (request.status() != null) {
                c.setStatus(request.status());
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

        Consultation saved = consultationRepository.save(c);
        String warning = computeAllergyWarning(saved);
        return ConsultationFlowResponse.from(saved, warning);
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public ConsultationFlowResponse complete(Authentication authentication, @PathVariable("id") UUID id, @RequestBody PatchConsultationRequest request) {
        User actor = requireUser(authentication);
        requireDoctor(actor);

        Doctor doctor = doctorService.ensureForDoctorUser(actor);

        Consultation c = consultationRepository.findByIdAndDoctorId(id, doctor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

        c.setStatus(ConsultationStatus.COMPLETED);
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
        } else if (actor.getRole() == UserRole.DOCTOR) {
            Doctor d = doctorService.ensureForDoctorUser(actor);
            c = consultationRepository.findByIdAndDoctorId(id, d.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

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
        } else if (actor.getRole() == UserRole.DOCTOR) {
            Doctor d = doctorService.ensureForDoctorUser(actor);
            consultationRepository.findByIdAndDoctorId(id, d.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
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
        requireDoctor(actor);

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

    private static void requireDoctor(User user) {
        if (user.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor role required");
        }
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
