package com.omnicare.auth;

import com.omnicare.doctor.*;
import com.omnicare.medication.Medication;
import com.omnicare.medication.MedicationRepository;
import com.omnicare.patient.Patient;
import com.omnicare.patient.PatientService;
import com.omnicare.prescription.Prescription;
import com.omnicare.prescription.PrescriptionItem;
import com.omnicare.prescription.PrescriptionRepository;
import com.omnicare.user.RegistrationStatus;
import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import com.omnicare.user.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/auth/dev")
public class DevProfessionalSeedController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final ConsultationRepository consultationRepository;
    private final DoctorDocumentRepository doctorDocumentRepository;
    private final PatientService patientService;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicationRepository medicationRepository;

    public DevProfessionalSeedController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DoctorRepository doctorRepository,
            ConsultationRepository consultationRepository,
            DoctorDocumentRepository doctorDocumentRepository,
            PatientService patientService,
            PrescriptionRepository prescriptionRepository,
            MedicationRepository medicationRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
        this.consultationRepository = consultationRepository;
        this.doctorDocumentRepository = doctorDocumentRepository;
        this.patientService = patientService;
        this.prescriptionRepository = prescriptionRepository;
        this.medicationRepository = medicationRepository;
    }

    public record SeedProfessionalRequest(
            String doctorEmail,
            String doctorName,
            String doctorPassword,
            String specialty,
            Integer yearsExperience,
            Integer totalReviews,
            Integer serviceRadiusKm,
            Integer consultationsCount,
            Integer documentsCount
    ) {
    }

    public record SeedProfessionalResponse(
            String doctorEmail,
            String patientEmail,
            int consultationsCreated,
            int documentsCreated
    ) {
    }

    @PostMapping("/seed-professional")
    @Transactional
    public SeedProfessionalResponse seed(@RequestBody SeedProfessionalRequest request) {
        if (request == null || request.doctorEmail() == null || request.doctorEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "doctorEmail is required");
        }

        String doctorEmail = request.doctorEmail().trim().toLowerCase();

        User doctorUser = userRepository.findByEmail(doctorEmail).orElseGet(() -> {
            String pwd = (request.doctorPassword() == null || request.doctorPassword().isBlank()) ? "Passw0rd!123" : request.doctorPassword();
            String name = (request.doctorName() == null || request.doctorName().isBlank()) ? doctorEmail : request.doctorName().trim();

            User u = new User(doctorEmail, name);
            u.setRole(UserRole.DOCTOR);
            u.setRegistrationStatus(RegistrationStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setPasswordHash(passwordEncoder.encode(pwd));
            return userRepository.save(u);
        });

        if (doctorUser.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existing user is not a DOCTOR");
        }

        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId()).orElseGet(() -> doctorRepository.save(new Doctor(doctorUser)));

        if (request.specialty() != null) {
            doctor.setSpecialty(request.specialty().trim());
        }
        if (request.yearsExperience() != null) {
            doctor.setExperienceYears(request.yearsExperience());
        }
        if (request.totalReviews() != null) {
            doctor.setTotalReviews(request.totalReviews());
        }
        if (request.serviceRadiusKm() != null) {
            doctor.setServiceRadiusKm(request.serviceRadiusKm());
        }

        doctorRepository.save(doctor);

        String stamp = String.valueOf(System.currentTimeMillis());
        String patientEmail = "seed.patient+" + stamp + "@dev.local";
        User patientUser = new User(patientEmail, "Sarah Ben Ahmed");
        patientUser.setRole(UserRole.PATIENT);
        patientUser.setRegistrationStatus(RegistrationStatus.ACTIVE);
        patientUser.setEmailVerified(true);
        patientUser.setPasswordHash(passwordEncoder.encode("Passw0rd!123"));
        userRepository.save(patientUser);

        Patient patient = patientService.ensureForUser(patientUser);

        int consultationsCount = request.consultationsCount() == null ? 5 : Math.max(0, Math.min(50, request.consultationsCount()));
        int documentsCount = request.documentsCount() == null ? 2 : Math.max(0, Math.min(10, request.documentsCount()));

        Random r = new Random();
        List<Consultation> createdConsultations = new ArrayList<>();

        for (int i = 0; i < consultationsCount; i++) {
            Consultation c = new Consultation(doctor);
            c.setPatient(patient);
            c.setSymptoms(i % 2 == 0 ? "Headache, fever" : "Sore throat, fatigue");
            c.setDiagnosis(i % 2 == 0 ? "Viral infection" : "Common cold");
            c.setTreatment(i % 2 == 0 ? "Rest + hydration" : "Vitamin C + rest");
            c.setClinicalNotes("Seeded consultation " + (i + 1));
            c.setStatus(ConsultationStatus.COMPLETED);
            c.setDurationMinutes(10 + r.nextInt(26));
            c.setPaymentMethod(i % 2 == 0 ? PaymentMethod.DIGITAL : PaymentMethod.CASH);

            BigDecimal fee = new BigDecimal(String.valueOf(1500 + r.nextInt(4000))).movePointLeft(2);
            c.setFee(fee);

            Instant when = Instant.now().minus(i, ChronoUnit.DAYS);
            c.setTimestamp(when);
            Consultation saved = consultationRepository.save(c);
            createdConsultations.add(saved);

            // Create a realistic prescription for most completed consultations.
            // Note: prescriptions are linked to patient_id and prescriber_user_id (not consultation_id).
            boolean shouldCreatePrescription = r.nextInt(100) < 75;
            if (shouldCreatePrescription) {
                Prescription p = new Prescription(patient, doctorUser, when);
                p.setNotes("Seeded prescription for consultation " + saved.getId());

                int itemsCount = 1 + r.nextInt(3);
                for (int j = 0; j < itemsCount; j++) {
                    Medication med = pickRandomMedication(r);
                    if (med == null) {
                        continue;
                    }

                    int frequencyTimes = 1 + r.nextInt(3);
                    int frequencyPeriodDays = 1;
                    int durationDays = 3 + r.nextInt(8);

                    PrescriptionItem item = new PrescriptionItem(med, frequencyTimes, frequencyPeriodDays, durationDays);
                    if (r.nextBoolean()) {
                        item.setDoseUnit("mg");
                    }
                    p.addItem(item);
                }

                prescriptionRepository.save(p);
            }
        }

        for (int i = 0; i < documentsCount; i++) {
            String title = i == 0 ? "Medical Diploma" : "Malpractice Insurance";
            DoctorDocument doc = new DoctorDocument(doctor, title);
            doc.setFileUrl("https://example.local/" + title.replace(" ", "-").toLowerCase() + ".pdf");
            doc.setStatus(i == 0 ? DoctorDocumentStatus.APPROVED : DoctorDocumentStatus.PENDING);
            doctorDocumentRepository.save(doc);
        }

        return new SeedProfessionalResponse(doctorEmail, patientEmail, createdConsultations.size(), documentsCount);
    }

    private Medication pickRandomMedication(Random r) {
        long total = medicationRepository.count();
        if (total <= 0) {
            return null;
        }
        int idx = (int) Math.min(Integer.MAX_VALUE, r.nextLong(total));
        return medicationRepository.findAll(PageRequest.of(idx, 1)).stream().findFirst().orElse(null);
    }
}
