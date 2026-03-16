package com.omnicare.auth.controller;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.model.DoctorDocument;
import com.omnicare.doctor.model.DoctorDocumentStatus;
import com.omnicare.doctor.model.PaymentMethod;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.repository.DoctorDocumentRepository;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.medication.Medication;
import com.omnicare.medication.MedicationRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.service.PatientService;
import com.omnicare.prescription.model.Prescription;
import com.omnicare.prescription.model.PrescriptionItem;
import com.omnicare.prescription.repository.PrescriptionRepository;
import com.omnicare.prescription.service.PrescriptionService;
import com.omnicare.profile.model.RegistrationStatus;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.repository.ProviderRepository;
import com.omnicare.provider.service.ProviderService;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.UUID;

@RestController
@RequestMapping({"/auth/dev", "/api/auth/dev"})
public class DevProfessionalSeedController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final ConsultationRepository consultationRepository;
    private final DoctorDocumentRepository doctorDocumentRepository;
    private final PatientService patientService;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicationRepository medicationRepository;
    private final PrescriptionService prescriptionService;
    private final ProviderService providerService;
    private final ProviderRepository providerRepository;

    public DevProfessionalSeedController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DoctorRepository doctorRepository,
            ConsultationRepository consultationRepository,
            DoctorDocumentRepository doctorDocumentRepository,
            PatientService patientService,
            PrescriptionRepository prescriptionRepository,
            MedicationRepository medicationRepository,
            PrescriptionService prescriptionService,
            ProviderService providerService,
            ProviderRepository providerRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
        this.consultationRepository = consultationRepository;
        this.doctorDocumentRepository = doctorDocumentRepository;
        this.patientService = patientService;
        this.prescriptionRepository = prescriptionRepository;
        this.medicationRepository = medicationRepository;
        this.prescriptionService = prescriptionService;
        this.providerService = providerService;
        this.providerRepository = providerRepository;
    }

    public record EnsureFakeDoctorResponse(UUID doctorId, String email, String password) {
    }

    @PostMapping("/ensure-fake-doctor")
    @Transactional
    public EnsureFakeDoctorResponse ensureFakeDoctor() {
        String email = "fake.doctor@dev.local";
        String password = "Passw0rd!123";

        User doctorUser = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "Fake Doctor");
            u.setRole(UserRole.DOCTOR);
            u.setRegistrationStatus(RegistrationStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setPasswordHash(passwordEncoder.encode(password));
            return userRepository.save(u);
        });

        if (doctorUser.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existing user is not a DOCTOR");
        }

        Provider provider = providerService.ensureForProfessionalUser(doctorUser);
        providerRepository.save(provider);

        Doctor doctor = doctorRepository.findByProviderId(provider.getId()).orElseGet(() -> doctorRepository.save(new Doctor(provider)));
        if (doctor.getSpecialty() == null || doctor.getSpecialty().isBlank()) {
            doctor.setSpecialty("General Practitioner");
            doctorRepository.save(doctor);
        }

        return new EnsureFakeDoctorResponse(doctor.getId(), doctorUser.getEmail(), password);
    }

    public record AutoCompleteConsultationRequest(
            String diagnosis,
            String treatment,
            String clinicalNotes
    ) {
    }

    public record AutoCompleteConsultationResponse(
            UUID consultationId,
            UUID prescriptionId,
            int itemsCount
    ) {
    }

    @PostMapping("/consultations/{id}/auto-complete")
    @Transactional
    public AutoCompleteConsultationResponse autoCompleteConsultation(@PathVariable("id") UUID consultationId, @RequestBody(required = false) AutoCompleteConsultationRequest request) {
        if (consultationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consultationId is required");
        }

        // Ensure we have a dev doctor user to act as prescriber.
        EnsureFakeDoctorResponse fake = ensureFakeDoctor();
        User doctorUser = userRepository.findByEmail(fake.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fake doctor user not found"));

        Consultation c = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));

        if (c.getDoctor() == null) {
            Doctor doctor = doctorRepository.findById(fake.doctorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fake doctor not found"));
            c.setDoctor(doctor);
        }

        c.setStatus(ConsultationStatus.COMPLETED);
        String diagnosis = request == null ? null : request.diagnosis();
        String treatment = request == null ? null : request.treatment();
        String clinicalNotes = request == null ? null : request.clinicalNotes();

        if (diagnosis != null && !diagnosis.isBlank()) {
            c.setDiagnosis(diagnosis.trim());
        } else if (c.getDiagnosis() == null || c.getDiagnosis().isBlank()) {
            c.setDiagnosis("Follow-up required");
        }

        if (treatment != null && !treatment.isBlank()) {
            c.setTreatment(treatment.trim());
        } else if (c.getTreatment() == null || c.getTreatment().isBlank()) {
            c.setTreatment("Rest + hydration");
        }

        if (clinicalNotes != null && !clinicalNotes.isBlank()) {
            c.setClinicalNotes(clinicalNotes.trim());
        } else if (c.getClinicalNotes() == null || c.getClinicalNotes().isBlank()) {
            c.setClinicalNotes("Auto-completed by dev endpoint");
        }

        consultationRepository.save(c);
        consultationRepository.flush();

        Random r = new Random();
        int itemsCount = 1 + r.nextInt(3);
        List<PrescriptionService.CreateItemRequest> items = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            Medication med = pickRandomMedication(r);
            if (med == null || med.getId() == null) {
                continue;
            }

            int frequencyTimes = 1 + r.nextInt(3);
            int frequencyPeriodDays = 1;
            int durationDays = 3 + r.nextInt(8);

            items.add(new PrescriptionService.CreateItemRequest(
                    med.getId(),
                    null,
                    null,
                    frequencyTimes,
                    frequencyPeriodDays,
                    durationDays,
                    null,
                    null
            ));
        }

        PrescriptionService.CreateRequest presRequest = new PrescriptionService.CreateRequest(
                null,
                Instant.now(),
                "Auto-generated ordonnance for consultation " + c.getId(),
                items
        );

        try {
            Prescription created = prescriptionService.createOrReplaceForConsultationAsDoctor(c.getId(), doctorUser.getId(), presRequest);
            prescriptionRepository.flush();
            return new AutoCompleteConsultationResponse(c.getId(), created.getId(), created.getItems() == null ? 0 : created.getItems().size());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = ex.getClass().getSimpleName();
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Auto-complete failed: " + msg);
        }
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

        Provider provider = providerService.ensureForProfessionalUser(doctorUser);
        providerRepository.save(provider);

        Doctor doctor = doctorRepository.findByProviderId(provider.getId()).orElseGet(() -> doctorRepository.save(new Doctor(provider)));

        if (request.specialty() != null) {
            doctor.setSpecialty(request.specialty().trim());
        }
        if (request.yearsExperience() != null) {
            doctor.setExperienceYears(request.yearsExperience());
        }
        if (request.totalReviews() != null) {
            provider.setTotalReviews(request.totalReviews());
        }
        if (request.serviceRadiusKm() != null) {
            provider.setServiceRadiusKm(request.serviceRadiusKm());
        }

        providerRepository.save(provider);
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
