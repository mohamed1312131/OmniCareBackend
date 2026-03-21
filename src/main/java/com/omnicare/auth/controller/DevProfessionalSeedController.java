package com.omnicare.auth.controller;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationCancellationReason;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.model.DoctorDocument;
import com.omnicare.doctor.model.DoctorDocumentStatus;
import com.omnicare.doctor.model.PaymentMethod;
import com.omnicare.passport.BloodGroup;
import com.omnicare.family.FamilyMember;
import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.repository.DoctorDocumentRepository;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.doctor.service.ConsultationFinancialService;
import com.omnicare.medicalact.model.MedicalAct;
import com.omnicare.medicalact.model.MedicalActType;
import com.omnicare.medicalact.repository.MedicalActRepository;
import com.omnicare.medicalact.repository.MedicalActCatalogRepository;
import com.omnicare.medicalact.model.MedicalActCatalog;
import com.omnicare.doctor.model.ConsultationMedicalAct;
import com.omnicare.doctor.repository.ConsultationMedicalActRepository;
import com.omnicare.medication.Medication;
import com.omnicare.medication.MedicationRepository;
import com.omnicare.patient.PatientAllergySeverity;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientAllergy;
import com.omnicare.patient.model.PatientChronicCondition;
import com.omnicare.patient.model.PatientMedication;
import com.omnicare.patient.repository.PatientAllergyRepository;
import com.omnicare.patient.repository.PatientChronicConditionRepository;
import com.omnicare.patient.repository.PatientMedicationRepository;
import com.omnicare.patient.repository.PatientRepository;
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
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.repository.ProviderRepository;
import com.omnicare.provider.service.ProviderService;
import com.omnicare.access.service.PatientAccessService;
import com.omnicare.access.model.PatientShareToken;
import com.omnicare.access.repository.PatientProviderAccessRepository;
import com.omnicare.access.repository.PatientShareTokenRepository;
import com.omnicare.reminder.model.PatientReminder;
import com.omnicare.reminder.model.PatientReminderTime;
import com.omnicare.reminder.model.PatientReminderType;
import com.omnicare.reminder.repository.PatientReminderRepository;
import com.omnicare.reminder.repository.PatientReminderTimeRepository;
import com.omnicare.body.repository.BodyPartCatalogRepository;
import com.omnicare.body.model.BodyPartCatalog;
import com.omnicare.kine.model.PatientTrauma;
import com.omnicare.kine.model.TraumaStatus;
import com.omnicare.kine.model.TreatmentPlan;
import com.omnicare.kine.model.TreatmentPlanStatus;
import com.omnicare.kine.repository.PatientTraumaRepository;
import com.omnicare.kine.repository.TreatmentPlanRepository;
import com.omnicare.doctor.model.ConsultationLocationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping({"/auth/dev", "/api/auth/dev"})
public class DevProfessionalSeedController {

    private static final Logger log = LoggerFactory.getLogger(DevProfessionalSeedController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final ConsultationRepository consultationRepository;
    private final DoctorDocumentRepository doctorDocumentRepository;
    private final PatientService patientService;
    private final PatientRepository patientRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final PatientChronicConditionRepository patientChronicConditionRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final PatientAccessService patientAccessService;
    private final PatientProviderAccessRepository patientProviderAccessRepository;
    private final PatientShareTokenRepository patientShareTokenRepository;
    private final PatientReminderRepository patientReminderRepository;
    private final PatientReminderTimeRepository patientReminderTimeRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicalActRepository medicalActRepository;
    private final MedicalActCatalogRepository medicalActCatalogRepository;
    private final MedicationRepository medicationRepository;
    private final PrescriptionService prescriptionService;
    private final ProviderService providerService;
    private final ProviderRepository providerRepository;
    private final ConsultationFinancialService consultationFinancialService;
    private final ConsultationMedicalActRepository consultationMedicalActRepository;
    private final BodyPartCatalogRepository bodyPartCatalogRepository;
    private final PatientTraumaRepository patientTraumaRepository;
    private final TreatmentPlanRepository treatmentPlanRepository;

    public DevProfessionalSeedController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DoctorRepository doctorRepository,
            ConsultationRepository consultationRepository,
            DoctorDocumentRepository doctorDocumentRepository,
            PatientService patientService,
            PatientRepository patientRepository,
            FamilyMemberRepository familyMemberRepository,
            PatientAllergyRepository patientAllergyRepository,
            PatientChronicConditionRepository patientChronicConditionRepository,
            PatientMedicationRepository patientMedicationRepository,
            PatientAccessService patientAccessService,
            PatientProviderAccessRepository patientProviderAccessRepository,
            PatientShareTokenRepository patientShareTokenRepository,
            PatientReminderRepository patientReminderRepository,
            PatientReminderTimeRepository patientReminderTimeRepository,
            PrescriptionRepository prescriptionRepository,
            MedicalActRepository medicalActRepository,
            MedicalActCatalogRepository medicalActCatalogRepository,
            MedicationRepository medicationRepository,
            PrescriptionService prescriptionService,
            ProviderService providerService,
            ProviderRepository providerRepository,
            ConsultationFinancialService consultationFinancialService,
            ConsultationMedicalActRepository consultationMedicalActRepository,
            BodyPartCatalogRepository bodyPartCatalogRepository,
            PatientTraumaRepository patientTraumaRepository,
            TreatmentPlanRepository treatmentPlanRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
        this.consultationRepository = consultationRepository;
        this.doctorDocumentRepository = doctorDocumentRepository;
        this.patientService = patientService;
        this.patientRepository = patientRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.patientAllergyRepository = patientAllergyRepository;
        this.patientChronicConditionRepository = patientChronicConditionRepository;
        this.patientMedicationRepository = patientMedicationRepository;
        this.patientAccessService = patientAccessService;
        this.patientProviderAccessRepository = patientProviderAccessRepository;
        this.patientShareTokenRepository = patientShareTokenRepository;
        this.patientReminderRepository = patientReminderRepository;
        this.patientReminderTimeRepository = patientReminderTimeRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.medicalActRepository = medicalActRepository;
        this.medicalActCatalogRepository = medicalActCatalogRepository;
        this.medicationRepository = medicationRepository;
        this.prescriptionService = prescriptionService;
        this.providerService = providerService;
        this.providerRepository = providerRepository;
        this.consultationFinancialService = consultationFinancialService;
        this.consultationMedicalActRepository = consultationMedicalActRepository;
        this.bodyPartCatalogRepository = bodyPartCatalogRepository;
        this.patientTraumaRepository = patientTraumaRepository;
        this.treatmentPlanRepository = treatmentPlanRepository;
    }

    public record EnsureFakeDoctorResponse(UUID doctorId, String email, String password) {
    }

    public record EnsureFakePatientResponse(UUID patientId, String email, String password) {
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

    @PostMapping("/ensure-fake-patient")
    @Transactional
    public EnsureFakePatientResponse ensureFakePatient() {
        String email = "fake.patient@dev.local";
        String password = "Passw0rd!123";

        User patientUser = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "Fake Patient");
            u.setRole(UserRole.PATIENT);
            u.setRegistrationStatus(RegistrationStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setPasswordHash(passwordEncoder.encode(password));
            return userRepository.save(u);
        });

        if (patientUser.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existing user is not a PATIENT");
        }

        Patient patient = patientService.ensureForUser(patientUser);
        return new EnsureFakePatientResponse(patient.getId(), patientUser.getEmail(), password);
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

    public record MegaSeedRequest(
            Integer showcasePatients,
            Integer minYearsBack,
            Integer maxYearsBack,
            Integer minConsultationsPerPatient,
            Integer maxConsultationsPerPatient
    ) {
    }

    public record SeededAccount(String role, String name, String email, String password) {
    }

    public record SeededPatient(String displayName, String email, UUID ownerUserId, UUID patientId, List<UUID> familyPatientIds, List<String> allergies) {
    }

    public record MegaSeedResponse(
            List<SeededAccount> accounts,
            List<SeededPatient> patients,
            int consultationsCreated,
            int consultationsCancelled,
            int prescriptionsCreated,
            int prescriptionsBlockedByAllergy,
            int chronicConditionsCreated,
            int remindersCreated,
            int reminderTimesCreated,
            int shareTokensCreated,
            int accessRevocationsCreated,
            int medicalActsCreated
    ) {
    }

    @PostMapping("/mega-seed")
    public MegaSeedResponse megaSeed(@RequestBody(required = false) MegaSeedRequest request) {
        int showcasePatients = Math.max(1, Math.min(10, request == null || request.showcasePatients() == null ? 3 : request.showcasePatients()));
        int minYearsBack = Math.max(0, Math.min(10, request == null || request.minYearsBack() == null ? 1 : request.minYearsBack()));
        int maxYearsBack = Math.max(minYearsBack, Math.min(10, request == null || request.maxYearsBack() == null ? 3 : request.maxYearsBack()));
        int minConsults = Math.max(1, Math.min(300, request == null || request.minConsultationsPerPatient() == null ? 20 : request.minConsultationsPerPatient()));
        int maxConsults = Math.max(minConsults, Math.min(500, request == null || request.maxConsultationsPerPatient() == null ? 60 : request.maxConsultationsPerPatient()));

        String password = "Passw0rd!123";
        Random r = new Random();

        List<String> bodyPartKeys = bodyPartCatalogRepository.findAllByActiveTrueOrderByKeyAsc().stream()
                .map(BodyPartCatalog::getKey)
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        BodyPartCatalog lumbar = bodyPartCatalogRepository.findByKeyIgnoreCase("back-lumbar").orElse(null);

        List<SeededAccount> accounts = new ArrayList<>();
        List<SeededPatient> patients = new ArrayList<>();

        // Ensure a small pool of providers exists.
        User fakeDoctorUser = ensureFakeDoctorUser(password);
        Provider fakeDoctorProvider = providerService.ensureForProfessionalUser(fakeDoctorUser);
        Doctor fakeDoctor = doctorRepository.findByProviderId(fakeDoctorProvider.getId()).orElseGet(() -> doctorRepository.save(new Doctor(fakeDoctorProvider)));

        accounts.add(new SeededAccount("DOCTOR", fakeDoctorUser.getName(), fakeDoctorUser.getEmail(), password));

        User nurseUser = ensureProfessionalUser("seed.nurse." + System.currentTimeMillis() + "@dev.local", "Seed Nurse", UserRole.NURSE, password);
        accounts.add(new SeededAccount("NURSE", nurseUser.getName(), nurseUser.getEmail(), password));

        User kineUser = ensureProfessionalUser("seed.kine." + System.currentTimeMillis() + "@dev.local", "Seed Kine", UserRole.KINE, password);
        accounts.add(new SeededAccount("KINE", kineUser.getName(), kineUser.getEmail(), password));

        User psychUser = ensureProfessionalUser("seed.psy." + System.currentTimeMillis() + "@dev.local", "Seed Psychiatrist", UserRole.PSYCHIATRIST, password);
        accounts.add(new SeededAccount("PSYCHIATRIST", psychUser.getName(), psychUser.getEmail(), password));

        Provider nurseProvider = providerService.ensureForProfessionalUser(nurseUser);
        Provider kineProvider = providerService.ensureForProfessionalUser(kineUser);
        Provider psychProvider = providerService.ensureForProfessionalUser(psychUser);
        Provider doctorProvider = fakeDoctorProvider;

        List<Provider> providerPool = List.of(doctorProvider, nurseProvider, kineProvider, psychProvider);

        int consultationsCreated = 0;
        int consultationsCancelled = 0;
        int prescriptionsCreated = 0;
        int prescriptionsBlocked = 0;
        int chronicConditionsCreated = 0;
        int remindersCreated = 0;
        int reminderTimesCreated = 0;
        int shareTokensCreated = 0;
        int accessRevocationsCreated = 0;
        int medicalActsCreated = 0;

        // doctor pedigree
        if (fakeDoctor.getExperienceYears() == null) {
            fakeDoctor.setExperienceYears(2 + r.nextInt(34));
        }
        if (fakeDoctor.getMedicalLicenseNumber() == null || fakeDoctor.getMedicalLicenseNumber().isBlank()) {
            fakeDoctor.setMedicalLicenseNumber("TN-77-" + (10000 + r.nextInt(90000)));
        }
        doctorRepository.save(fakeDoctor);

        for (int i = 0; i < showcasePatients; i++) {
            String stamp = String.valueOf(System.currentTimeMillis());
            String patientEmail = "mega.patient." + (i + 1) + "+" + stamp + "@example.tn";
            String patientName = "Mega Patient " + (i + 1);

            User patientUser = ensurePatientUser(patientEmail, patientName, password);
            deepFillUserIdentity(r, patientUser);
            userRepository.save(patientUser);
            accounts.add(new SeededAccount("PATIENT", patientUser.getName(), patientUser.getEmail(), password));

            Patient rootPatient = patientService.ensureForUser(patientUser);
            rootPatient = patientRepository.findByUserId(patientUser.getId()).orElse(rootPatient);

            // Kine ecosystem: create a trauma + treatment plan series for the root patient
            TreatmentPlan kinePlan = null;
            if (kineProvider != null && lumbar != null) {
                PatientTrauma trauma = new PatientTrauma(rootPatient, lumbar, pick(r, new String[]{
                        "Lumbar Disc Herniation",
                        "Ankle Sprain",
                        "Rotator Cuff Injury",
                        "ACL Tear"
                }));
                trauma.setDescription("Seeded trauma for kine series");
                trauma.setDateOfInjury(LocalDate.now().minusDays(10 + r.nextInt(500)));
                trauma.setStatus(r.nextInt(100) < 80 ? TraumaStatus.ACTIVE : TraumaStatus.RECOVERED);
                trauma.setCreatedByUser(kineProvider.getUser());
                patientTraumaRepository.save(trauma);

                int totalSessions = 8 + r.nextInt(8);
                TreatmentPlan plan = new TreatmentPlan(rootPatient, kineProvider, totalSessions);
                plan.setTrauma(trauma);
                plan.setStatus(TreatmentPlanStatus.values()[r.nextInt(TreatmentPlanStatus.values().length)]);
                plan.setCreatedByUser(kineProvider.getUser());
                plan.setSessionsCompleted(Math.min(totalSessions, 1 + r.nextInt(totalSessions)));
                treatmentPlanRepository.save(plan);
                kinePlan = plan;

                // Generate a cohesive series of sessions linked to this plan
                int sessionsToCreate = plan.getSessionsCompleted() == null ? totalSessions : Math.max(1, Math.min(totalSessions, plan.getSessionsCompleted()));
                Instant seriesStart = Instant.now().minus(7L * sessionsToCreate, ChronoUnit.DAYS);
                for (int s = 0; s < sessionsToCreate; s++) {
                    Consultation kc = new Consultation();
                    kc.setProvider(kineProvider);
                    kc.setPatient(rootPatient);
                    kc.setStatus(ConsultationStatus.COMPLETED);
                    kc.setSymptoms("Physiotherapy session");
                    kc.setDiagnosis("Rehabilitation follow-up");
                    kc.setTreatment("Therapeutic exercise + manual therapy");
                    kc.setClinicalNotes("Seeded kine session " + (s + 1) + " of " + totalSessions);
                    kc.setDurationMinutes(25 + r.nextInt(25));
                    kc.setPaymentMethod(r.nextBoolean() ? PaymentMethod.DIGITAL : PaymentMethod.CASH);

                    boolean home = r.nextInt(100) < 35;
                    kc.setLocationType(home ? ConsultationLocationType.HOME : ConsultationLocationType.CLINIC);
                    kc.setTreatmentPlan(plan);
                    kc.setAffectedAreas(List.of("back-lumbar"));

                    // Base fee (acts will be added later); displacement fee is applied by ConsultationFinancialService
                    kc.setFee(new BigDecimal("23.00").add(new BigDecimal(String.valueOf(r.nextInt(500))).movePointLeft(2)));

                    Instant when = seriesStart.plus(7L * s, ChronoUnit.DAYS);
                    kc.setTimestamp(when);
                    consultationFinancialService.apply(kc);
                    consultationRepository.save(kc);
                    consultationsCreated++;
                }
            }

            List<String> allergyList = seedShowcaseAllergies(r, rootPatient);

            // chronic conditions for about half of root patients
            if (r.nextInt(100) < 55) {
                chronicConditionsCreated += seedChronicConditions(r, rootPatient, fakeDoctorUser);
            }

            // family members
            int familyCount = 1 + r.nextInt(3);
            List<UUID> familyPatientIds = new ArrayList<>();
            for (int fm = 0; fm < familyCount; fm++) {
                FamilyMember member = new FamilyMember(patientUser, "Family Member " + (fm + 1) + " of " + patientName, pick(r, new String[]{"SPOUSE", "CHILD", "PARENT"}));
                member.setBirthDate(LocalDate.now().minusYears(5 + r.nextInt(70)).minusDays(r.nextInt(365)));
                member.setGender(r.nextBoolean() ? "F" : "M");
                member.setBloodGroup(pickFamilyBloodGroup(r, patientUser.getBloodGroup()));
                familyMemberRepository.save(member);
                Patient familyPatient = patientService.ensureForFamilyMember(patientUser, member);
                familyPatientIds.add(familyPatient.getId());
                seedShowcaseAllergies(r, familyPatient);

                if (r.nextInt(100) < 45) {
                    chronicConditionsCreated += seedChronicConditions(r, familyPatient, fakeDoctorUser);
                }
            }

            patients.add(new SeededPatient(patientName, patientUser.getEmail(), patientUser.getId(), rootPatient.getId(), familyPatientIds, allergyList));

            // Grant access from this patient owner to all providers for both root + family patients
            for (Patient p : collectPatientsForOwner(patientUser, rootPatient.getId(), familyPatientIds)) {
                for (Provider prov : providerPool) {
                    try {
                        patientAccessService.grantAccessFromPatientToProvider(patientUser, p.getId(), prov.getId());
                    } catch (Exception ignored) {
                    }

                    if (r.nextInt(100) < 10) {
                        if (patientProviderAccessRepository.findActiveByPatientIdAndProviderId(p.getId(), prov.getId()).isPresent()) {
                            patientProviderAccessRepository.findActiveByPatientIdAndProviderId(p.getId(), prov.getId()).ifPresent(access -> {
                                access.revoke(Instant.now().minus(10 + r.nextInt(1000), ChronoUnit.DAYS), patientUser);
                                patientProviderAccessRepository.save(access);
                            });
                            accessRevocationsCreated++;
                        }
                    }
                }
            }

            // Share tokens: 5 per root patient (mix of expired/used/active)
            shareTokensCreated += seedShareTokens(r, rootPatient, patientUser);

            // Backdated history
            int yearsBack = minYearsBack + (maxYearsBack == minYearsBack ? 0 : r.nextInt((maxYearsBack - minYearsBack) + 1));
            Instant start = Instant.now().minus(yearsBack * 365L, ChronoUnit.DAYS);

            int consultCount = minConsults + (maxConsults == minConsults ? 0 : r.nextInt((maxConsults - minConsults) + 1));

            List<UUID> allPatientIds = new ArrayList<>();
            allPatientIds.add(rootPatient.getId());
            allPatientIds.addAll(familyPatientIds);

            for (int ci = 0; ci < consultCount; ci++) {
                UUID chosenPatientId = allPatientIds.get(r.nextInt(allPatientIds.size()));
                Patient chosenPatient = patientRepository.findById(chosenPatientId).orElse(null);
                if (chosenPatient == null) {
                    continue;
                }

                Provider prov = providerPool.get(r.nextInt(providerPool.size()));
                ProviderType type = prov.getType();

                Consultation c = new Consultation();
                c.setProvider(prov);
                if (type == ProviderType.DOCTOR) {
                    c.setDoctor(fakeDoctor);
                }
                c.setPatient(chosenPatient);

                if (type == ProviderType.KINE && kinePlan != null && chosenPatient.getId() != null && chosenPatient.getId().equals(rootPatient.getId())) {
                    c.setTreatmentPlan(kinePlan);
                    c.setLocationType(r.nextInt(100) < 30 ? ConsultationLocationType.HOME : ConsultationLocationType.CLINIC);
                }

                Instant when = randomInstantBetween(r, start, Instant.now());
                c.setTimestamp(when);

                c.setPainLevel(r.nextInt(11));
                c.setAffectedAreas(pickAffectedAreas(r, bodyPartKeys));
                c.setStreetAddress(pick(r, new String[]{"12 Avenue Habib Bourguiba", "44 Rue de Marseille", "9 Rue des Orangers"}));
                c.setCity(pick(r, new String[]{"Tunis", "Sfax", "Sousse"}));
                c.setLatitude(36.8 + (r.nextDouble() * 0.2));
                c.setLongitude(10.1 + (r.nextDouble() * 0.2));

                boolean shouldCancel = r.nextInt(100) < 20;
                if (shouldCancel) {
                    c.setStatus(ConsultationStatus.CANCELLED);
                    boolean cancelledByPatient = r.nextBoolean();
                    c.setCancelledAt(when.minus(2 + r.nextInt(240), ChronoUnit.MINUTES));
                    c.setCancelledByUser(cancelledByPatient ? patientUser : prov.getUser());
                    c.setCancellationReason(cancelledByPatient ? ConsultationCancellationReason.PATIENT_CANCELLED : ConsultationCancellationReason.PROVIDER_CANCELLED);
                    consultationsCancelled++;
                } else {
                    c.setStatus(ConsultationStatus.COMPLETED);
                    c.setSymptoms(pick(r, new String[]{"Headache and fatigue", "Sore throat", "Back pain", "Anxiety", "Fever"}));
                    c.setDiagnosis(pick(r, new String[]{"Viral infection", "Muscle strain", "Stress", "Seasonal allergy", "Follow-up required"}));
                    c.setTreatment(pick(r, new String[]{"Rest + hydration", "Paracetamol for 3 days", "Physiotherapy sessions", "Breathing exercises", "Vitamin C"}));
                    c.setClinicalNotes("Seeded historical consultation");
                    c.setDurationMinutes(10 + r.nextInt(31));
                    c.setPaymentMethod(r.nextBoolean() ? PaymentMethod.DIGITAL : PaymentMethod.CASH);

                    if (type == ProviderType.NURSE) {
                        List<MedicalActCatalog> nurseActs = medicalActCatalogRepository.findAllByProviderTypeAndActiveTrueOrderByNameAsc(ProviderType.NURSE);
                        if (!nurseActs.isEmpty()) {
                            int count = nurseActs.size() == 1 ? 1 : (r.nextInt(100) < 65 ? 1 : 2);
                            List<MedicalActCatalog> selected = new ArrayList<>();
                            for (int pick = 0; pick < count; pick++) {
                                MedicalActCatalog a = nurseActs.get(r.nextInt(nurseActs.size()));
                                if (selected.stream().noneMatch(x -> x.getId() != null && x.getId().equals(a.getId()))) {
                                    selected.add(a);
                                }
                            }
                            BigDecimal fee = BigDecimal.ZERO;
                            boolean hasOther = false;
                            for (MedicalActCatalog a : selected) {
                                if (a.getBasePrice() != null) {
                                    fee = fee.add(a.getBasePrice());
                                }
                                if (a.getCode() != null && a.getCode().equalsIgnoreCase("NURSE_OTHER_COMPLEX_CARE")) {
                                    hasOther = true;
                                }
                            }
                            c.setFee(fee);
                            if (hasOther) {
                                c.setOtherMedicalActText(pick(r, new String[]{"Other / complex home care", "Complex care (details in notes)", "Unlisted nursing procedure"}));
                            }
                        } else {
                            c.setFee(new BigDecimal("25.00").add(new BigDecimal(String.valueOf(r.nextInt(40))).movePointLeft(2)));
                        }
                    } else {
                        c.setFee(new BigDecimal("25.00").add(new BigDecimal(String.valueOf(r.nextInt(40))).movePointLeft(2)));
                    }
                }

                consultationFinancialService.apply(c);

                Consultation saved = consultationRepository.save(c);
                consultationsCreated++;

                if (saved.getStatus() == ConsultationStatus.COMPLETED && type == ProviderType.NURSE) {
                    List<MedicalActCatalog> nurseActs = medicalActCatalogRepository.findAllByProviderTypeAndActiveTrueOrderByNameAsc(ProviderType.NURSE);
                    if (!nurseActs.isEmpty()) {
                        int count = nurseActs.size() == 1 ? 1 : (r.nextInt(100) < 65 ? 1 : 2);
                        List<MedicalActCatalog> selected = new ArrayList<>();
                        for (int pick = 0; pick < count; pick++) {
                            MedicalActCatalog a = nurseActs.get(r.nextInt(nurseActs.size()));
                            if (selected.stream().noneMatch(x -> x.getId() != null && x.getId().equals(a.getId()))) {
                                selected.add(a);
                            }
                        }
                        List<ConsultationMedicalAct> joinRows = new ArrayList<>();
                        for (MedicalActCatalog a : selected) {
                            joinRows.add(new ConsultationMedicalAct(saved, a));
                        }
                        consultationMedicalActRepository.saveAll(joinRows);
                    }
                }

                // If completed and prescriber type, try to create prescription via service to enforce allergy blocking.
                boolean canPrescribe = saved.getStatus() == ConsultationStatus.COMPLETED && type == ProviderType.DOCTOR;
                if (canPrescribe && r.nextInt(100) < 70) {
                    User prescriber = prov.getUser();
                    if (prescriber == null || prescriber.getId() == null) {
                        continue;
                    }

                    int tries = 0;
                    boolean created = false;
                    while (!created && tries < 3) {
                        tries++;
                        Medication med = pickRandomMedication(r);
                        if (med == null || med.getId() == null) {
                            break;
                        }
                        List<PrescriptionService.CreateItemRequest> items = List.of(
                                new PrescriptionService.CreateItemRequest(
                                        med.getId(),
                                        new BigDecimal(pick(r, new String[]{"250", "500", "1000"})),
                                        pick(r, new String[]{"mg", "ml", "puff"}),
                                        1 + r.nextInt(3),
                                        1,
                                        3 + r.nextInt(12),
                                        LocalDate.ofInstant(saved.getTimestamp(), java.time.ZoneOffset.UTC),
                                        pick(r, new String[]{"After meals", "Before sleep", "With plenty of water"})
                                )
                        );

                        PrescriptionService.CreateRequest presReq = new PrescriptionService.CreateRequest(
                                null,
                                saved.getTimestamp(),
                                "Seeded prescription for consultation " + saved.getId(),
                                items
                        );

                        try {
                            Prescription p = prescriptionService.createOrReplaceForConsultationAsDoctor(saved.getId(), prescriber.getId(), presReq);
                            p.setIssuedAt(saved.getTimestamp());
                            prescriptionRepository.save(p);
                            prescriptionsCreated++;
                            created = true;

                            // Deep-fill patient medications + reminders
                            remindersCreated += deepFillMedsAndRemindersFromPrescription(r, p, prescriber, saved.getTimestamp());
                            reminderTimesCreated += lastCreatedReminderTimes;

                            // Create some medical acts performed by nurse/kine linked to this prescription
                            medicalActsCreated += seedMedicalActs(r, p, nurseProvider, kineProvider, saved.getTimestamp());
                        } catch (org.springframework.web.server.ResponseStatusException ex) {
                            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                                prescriptionsBlocked++;
                                continue;
                            }
                            break;
                        }
                    }
                }
            }
        }

        log.info("MEGA-SEED complete: showcasePatients={}, consultationsCreated={}, cancelled={}, prescriptionsCreated={}, prescriptionsBlockedByAllergy={}", showcasePatients, consultationsCreated, consultationsCancelled, prescriptionsCreated, prescriptionsBlocked);
        for (SeededAccount acc : accounts) {
            log.info("MEGA-SEED account: role={} email={} password={}", acc.role(), acc.email(), acc.password());
        }
        for (SeededPatient p : patients) {
            log.info("MEGA-SEED patient: name={} email={} patientId={} familyCount={} allergies={}", p.displayName(), p.email(), p.patientId(), p.familyPatientIds() == null ? 0 : p.familyPatientIds().size(), p.allergies());
        }

        return new MegaSeedResponse(
                accounts,
                patients,
                consultationsCreated,
                consultationsCancelled,
                prescriptionsCreated,
                prescriptionsBlocked,
                chronicConditionsCreated,
                remindersCreated,
                reminderTimesCreated,
                shareTokensCreated,
                accessRevocationsCreated,
                medicalActsCreated
        );
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
            consultationFinancialService.apply(c);
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

    private static Instant randomInstantBetween(Random r, Instant startInclusive, Instant endExclusive) {
        if (startInclusive == null || endExclusive == null) {
            return Instant.now();
        }
        long start = startInclusive.toEpochMilli();
        long end = endExclusive.toEpochMilli();
        if (end <= start) {
            return startInclusive;
        }
        long v = start + (long) (r.nextDouble() * (end - start));
        return Instant.ofEpochMilli(v);
    }

    private static String pick(Random r, String[] pool) {
        if (pool == null || pool.length == 0) {
            return null;
        }
        return pool[r.nextInt(pool.length)];
    }

    private List<Patient> collectPatientsForOwner(User owner, UUID rootPatientId, List<UUID> familyPatientIds) {
        List<Patient> out = new ArrayList<>();
        if (rootPatientId != null) {
            patientRepository.findByIdAndOwnerUserId(rootPatientId, owner.getId()).ifPresent(out::add);
        }
        if (familyPatientIds != null) {
            for (UUID id : familyPatientIds) {
                patientRepository.findByIdAndOwnerUserId(id, owner.getId()).ifPresent(out::add);
            }
        }
        return out;
    }

    private List<String> seedShowcaseAllergies(Random r, Patient patient) {
        String[] substances = new String[]{"Penicillin", "Ibuprofen", "Aspirin", "Amoxicillin", "Latex", "Peanuts"};
        String[] reactions = new String[]{"Anaphylaxis", "Mild rash", "Hives", "Nausea", "Swelling"};
        int count = 1 + r.nextInt(3);
        List<String> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String substance = substances[r.nextInt(substances.length)];
            if (patientAllergyRepository.existsByPatientIdAndSubstanceIgnoreCase(patient.getId(), substance)) {
                continue;
            }
            PatientAllergy allergy = new PatientAllergy(patient, substance);
            allergy.setSeverity(randomSeverity(r));
            allergy.setReaction(reactions[r.nextInt(reactions.length)]);
            // createdByUser intentionally left null for family member allergies; for user patients, set later if needed
            patientAllergyRepository.save(allergy);
            created.add(substance);
        }
        return created;
    }

    private int seedChronicConditions(Random r, Patient patient, User createdBy) {
        String[] conditions = new String[]{"Type 2 Diabetes", "Hypertension", "Asthma", "Hypothyroidism", "Migraine"};
        int count = 1 + r.nextInt(2);
        int created = 0;
        for (int i = 0; i < count; i++) {
            String name = conditions[r.nextInt(conditions.length)];
            if (patientChronicConditionRepository.existsByPatientIdAndNameIgnoreCase(patient.getId(), name)) {
                continue;
            }
            PatientChronicCondition cc = new PatientChronicCondition(patient, name);
            cc.setNotes("Seeded chronic condition");
            cc.setCreatedByUser(createdBy);
            cc.setRecordedAt(Instant.now().minus(30 + r.nextInt(2000), ChronoUnit.DAYS));
            patientChronicConditionRepository.save(cc);
            created++;
        }
        return created;
    }

    private int seedShareTokens(Random r, Patient patient, User createdByUser) {
        if (patient == null || patient.getId() == null) {
            return 0;
        }
        int created = 0;
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            UUID token = UUID.randomUUID();
            Instant expiresAt;
            if (i == 0) {
                expiresAt = now.minus(10, ChronoUnit.DAYS);
            } else if (i == 1) {
                expiresAt = now.minus(1, ChronoUnit.DAYS);
            } else if (i == 2) {
                expiresAt = now.plus(2, ChronoUnit.DAYS);
            } else {
                expiresAt = now.plus(30 + r.nextInt(90), ChronoUnit.DAYS);
            }
            PatientShareToken row = new PatientShareToken(token, patient, createdByUser, expiresAt);
            if (i == 1 || i == 3) {
                row.markUsed(now.minus(1 + r.nextInt(10), ChronoUnit.DAYS));
            }
            patientShareTokenRepository.save(row);
            created++;
        }
        return created;
    }

    private void deepFillUserIdentity(Random r, User u) {
        if (u == null) {
            return;
        }
        if (u.getFirstName() == null || u.getFirstName().isBlank()) {
            u.setFirstName(pick(r, new String[]{"Mohamed", "Ahmed", "Sarra", "Yasmine", "Houssem", "Amal"}));
        }
        if (u.getLastName() == null || u.getLastName().isBlank()) {
            u.setLastName(pick(r, new String[]{"Ben Ali", "Trabelsi", "Gharbi", "Khalfallah", "Jaziri", "Mansour"}));
        }
        if (u.getName() == null || u.getName().isBlank()) {
            u.setName(u.getFirstName() + " " + u.getLastName());
        }
        if (u.getGender() == null || u.getGender().isBlank()) {
            u.setGender(pick(r, new String[]{"M", "F", "O"}));
        }
        if (u.getDateOfBirth() == null) {
            u.setDateOfBirth(LocalDate.now().minusYears(18 + r.nextInt(73)).minusDays(r.nextInt(365)));
        }
        if (u.getBloodGroup() == null) {
            u.setBloodGroup(BloodGroup.values()[r.nextInt(BloodGroup.values().length)]);
        }
        if (u.getPhoneNumber() == null || u.getPhoneNumber().isBlank()) {
            u.setPhoneNumber("+216" + (20000000 + r.nextInt(79999999)));
        }
        if (u.getMedicalInfo() == null) {
            u.setMedicalInfo(Map.of("heightCm", 150 + r.nextInt(45), "weightKg", 50 + r.nextInt(60)));
        }
    }

    private static BloodGroup pickFamilyBloodGroup(Random r, BloodGroup base) {
        if (base == null || r.nextInt(100) < 75) {
            return base;
        }
        return BloodGroup.values()[r.nextInt(BloodGroup.values().length)];
    }

    private static List<String> pickAffectedAreas(Random r, List<String> pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        int n = 1 + r.nextInt(3);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String area = pool.get(r.nextInt(pool.size()));
            if (!out.contains(area)) {
                out.add(area);
            }
        }
        return out;
    }

    private int lastCreatedReminderTimes = 0;
    private int deepFillMedsAndRemindersFromPrescription(Random r, Prescription p, User createdBy, Instant issuedAt) {
        lastCreatedReminderTimes = 0;
        if (p == null || p.getPatient() == null || p.getPatient().getId() == null || p.getId() == null) {
            return 0;
        }

        // Ensure patient allergies have createdByUser for USER patients
        Patient patient = p.getPatient();
        for (PatientAllergy allergy : patientAllergyRepository.findAllByPatientIdOrderByRecordedAtDesc(patient.getId())) {
            if (allergy.getCreatedByUser() == null && r.nextInt(100) < 70) {
                allergy.setCreatedByUser(createdBy);
                patientAllergyRepository.save(allergy);
            }
        }

        int reminders = 0;
        LocalDate startDate = issuedAt == null ? LocalDate.now() : LocalDate.ofInstant(issuedAt, java.time.ZoneOffset.UTC);

        List<PatientMedication> meds = patientMedicationRepository.findAllByPatientIdAndSourcePrescriptionId(patient.getId(), p.getId());
        for (PatientMedication pm : meds) {
            if (pm.getStartDate() == null) {
                pm.setStartDate(startDate);
            }
            if (pm.getFrequency() == null || pm.getFrequency().isBlank()) {
                pm.setFrequency(pick(r, new String[]{"Daily", "Every 12 hours", "Every 8 hours"}));
            }
            if (pm.getTimesPerDay() == null) {
                pm.setTimesPerDay(1 + r.nextInt(3));
            }
            if (pm.getDurationDays() == null) {
                pm.setDurationDays(3 + r.nextInt(14));
            }
            if (pm.getCreatedByUser() == null) {
                pm.setCreatedByUser(createdBy);
            }
            pm.setRecordedAt(issuedAt == null ? pm.getRecordedAt() : issuedAt);
            patientMedicationRepository.save(pm);

            UUID medId = pm.getMedication() == null ? null : pm.getMedication().getId();
            String medName = medId == null
                    ? "medication"
                    : medicationRepository.findById(medId).map(Medication::getName).orElse("medication");
            PatientReminder rem = new PatientReminder(patient, PatientReminderType.MEDICATION, "Take " + medName);
            rem.setPatientMedication(pm);
            rem.setCreatedByUser(createdBy);
            rem.setStartDate(pm.getStartDate());
            rem.setEndDate(pm.getStartDate() == null ? null : pm.getStartDate().plusDays(pm.getDurationDays() == null ? 7 : pm.getDurationDays()));
            rem.setDescription("Seeded medication reminder");
            patientReminderRepository.save(rem);
            reminders++;

            int times = pm.getTimesPerDay() == null ? 1 : pm.getTimesPerDay();
            List<LocalTime> schedule = switch (times) {
                case 1 -> List.of(LocalTime.of(8, 0));
                case 2 -> List.of(LocalTime.of(8, 0), LocalTime.of(20, 0));
                default -> List.of(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0));
            };
            for (LocalTime t : schedule) {
                patientReminderTimeRepository.save(new PatientReminderTime(rem, t));
                lastCreatedReminderTimes++;
            }
        }
        return reminders;
    }

    private int seedMedicalActs(Random r, Prescription p, Provider nurseProvider, Provider kineProvider, Instant performedAt) {
        if (p == null || p.getId() == null) {
            return 0;
        }
        int created = 0;
        if (nurseProvider != null && r.nextInt(100) < 55) {
            MedicalAct a = new MedicalAct(p, nurseProvider, MedicalActType.INJECTION);
            a.setNotes("Seeded nurse act");
            a.setPerformedAt(performedAt == null ? Instant.now() : performedAt.plus(1, ChronoUnit.HOURS));
            medicalActRepository.save(a);
            created++;
        }
        if (kineProvider != null && r.nextInt(100) < 55) {
            MedicalAct a = new MedicalAct(p, kineProvider, MedicalActType.ADMINISTERED);
            a.setNotes("Seeded kinesitherapy act");
            a.setPerformedAt(performedAt == null ? Instant.now() : performedAt.plus(2, ChronoUnit.HOURS));
            medicalActRepository.save(a);
            created++;
        }
        return created;
    }

    private static PatientAllergySeverity randomSeverity(Random r) {
        int v = r.nextInt(100);
        if (v < 15) return PatientAllergySeverity.LOW;
        if (v < 55) return PatientAllergySeverity.MEDIUM;
        if (v < 90) return PatientAllergySeverity.HIGH;
        return PatientAllergySeverity.UNKNOWN;
    }

    private User ensureFakeDoctorUser(String password) {
        EnsureFakeDoctorResponse fake = ensureFakeDoctor();
        return userRepository.findByEmail(fake.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fake doctor user not found"));
    }

    private User ensurePatientUser(String email, String name, String password) {
        String normalized = email.trim().toLowerCase();
        return userRepository.findByEmail(normalized).orElseGet(() -> {
            User u = new User(normalized, name == null ? normalized : name);
            u.setRole(UserRole.PATIENT);
            u.setRegistrationStatus(RegistrationStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setPasswordHash(passwordEncoder.encode(password));
            return userRepository.save(u);
        });
    }

    private User ensureProfessionalUser(String email, String name, UserRole role, String password) {
        String normalized = email.trim().toLowerCase();
        return userRepository.findByEmail(normalized).orElseGet(() -> {
            User u = new User(normalized, name == null ? normalized : name);
            u.setRole(role);
            u.setRegistrationStatus(RegistrationStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setPasswordHash(passwordEncoder.encode(password));
            return userRepository.save(u);
        });
    }
}
