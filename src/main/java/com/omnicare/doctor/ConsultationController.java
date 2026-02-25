package com.omnicare.doctor;

import com.omnicare.patient.Patient;
import com.omnicare.patient.PatientAllergy;
import com.omnicare.patient.PatientAllergyRepository;
import com.omnicare.patient.PatientRepository;
import com.omnicare.patient.PatientService;
import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import com.omnicare.user.UserRole;
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

    public ConsultationController(
            UserRepository userRepository,
            DoctorService doctorService,
            DoctorRepository doctorRepository,
            ConsultationRepository consultationRepository,
            PatientService patientService,
            PatientRepository patientRepository,
            PatientAllergyRepository patientAllergyRepository
    ) {
        this.userRepository = userRepository;
        this.doctorService = doctorService;
        this.doctorRepository = doctorRepository;
        this.consultationRepository = consultationRepository;
        this.patientService = patientService;
        this.patientRepository = patientRepository;
        this.patientAllergyRepository = patientAllergyRepository;
    }

    public record CreateConsultationRequest(UUID doctorId, String symptoms, BigDecimal fee) {
    }

    public record PatchConsultationRequest(ConsultationStatus status, String diagnosis, String treatment) {
    }

    public record ConsultationFlowResponse(
            UUID id,
            UUID doctorId,
            UUID patientId,
            String symptoms,
            String diagnosis,
            String treatment,
            ConsultationStatus status,
            BigDecimal fee,
            BigDecimal netAmount,
            BigDecimal omnicareFee,
            Instant timestamp,
            String allergyWarning
    ) {
        static ConsultationFlowResponse from(Consultation c, String allergyWarning) {
            return new ConsultationFlowResponse(
                    c.getId(),
                    c.getDoctor() == null ? null : c.getDoctor().getId(),
                    c.getPatient() == null ? null : c.getPatient().getId(),
                    c.getSymptoms(),
                    c.getDiagnosis(),
                    c.getTreatment(),
                    c.getStatus(),
                    c.getFee(),
                    c.getNetAmount(),
                    c.getOmnicareFee(),
                    c.getTimestamp(),
                    allergyWarning
            );
        }
    }

    @PostMapping
    @Transactional
    public ConsultationFlowResponse create(Authentication authentication, @RequestBody CreateConsultationRequest request) {
        User actor = requireUser(authentication);
        requirePatient(actor);

        if (request == null || request.doctorId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "doctorId is required");
        }
        if (request.symptoms() == null || request.symptoms().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symptoms is required");
        }

        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        Patient patient = patientService.ensureForUser(actor);

        Consultation c = new Consultation(doctor);
        c.setPatient(patient);
        c.setPatientUser(actor);
        c.setSymptoms(request.symptoms().trim());
        c.setStatus(ConsultationStatus.PENDING);
        if (request.fee() != null) {
            c.setFee(request.fee());
        }
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

        Consultation c = consultationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

        if (actor.getRole() == UserRole.PATIENT) {
            Patient p = patientRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            if (c.getPatient() == null || !p.getId().equals(c.getPatient().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else if (actor.getRole() == UserRole.DOCTOR) {
            Doctor d = doctorRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            if (c.getDoctor() == null || !d.getId().equals(c.getDoctor().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return ConsultationFlowResponse.from(c, computeAllergyWarning(c));
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
