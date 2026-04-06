package com.omnicare.doctor.service;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.RegistrationStatus;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class PendingDoctorConsultationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PendingDoctorConsultationSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProviderRepository providerRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final ConsultationRepository consultationRepository;

    public PendingDoctorConsultationSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ProviderRepository providerRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            ConsultationRepository consultationRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.providerRepository = providerRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.consultationRepository = consultationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Provider doctorProvider = getOrCreateDoctorProvider();
        if (doctorProvider == null || doctorProvider.getId() == null) {
            log.warn(
                    "[PendingDoctorConsultationSeeder] No DOCTOR provider available; skipping pending consultation seed.");
            return;
        }

        if (doctorProvider.getServiceRadiusKm() == null || doctorProvider.getServiceRadiusKm() <= 0) {
            doctorProvider.setServiceRadiusKm(50);
            providerRepository.save(doctorProvider);
        }

        Patient patient = getOrCreatePatient();
        if (patient == null || patient.getId() == null) {
            log.warn("[PendingDoctorConsultationSeeder] No patient available; skipping pending consultation seed.");
            return;
        }

        boolean alreadySeeded = consultationRepository.existsByProviderIdAndPatientIdAndStatusIn(
                doctorProvider.getId(),
                patient.getId(),
                List.of(ConsultationStatus.PENDING));

        if (alreadySeeded) {
            log.info(
                    "[PendingDoctorConsultationSeeder] Pending doctor consultation already exists for provider={} patient={}",
                    doctorProvider.getId(), patient.getId());
            return;
        }

        Doctor doctorDetails = doctorRepository.findByProviderId(doctorProvider.getId())
                .orElseGet(() -> doctorRepository.saveAndFlush(new Doctor(doctorProvider)));

        Consultation c = new Consultation();
        c.setDoctor(doctorDetails);
        c.setProvider(doctorProvider);
        c.setPatient(patient);
        c.setStatus(ConsultationStatus.PENDING);
        c.setSymptoms("Severe headache + fever for 2 days");
        c.setCity("Tunis");
        c.setStreetAddress("36 Rue de Marseille");
        c.setLatitude(36.833);
        c.setLongitude(10.193);
        c.setTimestamp(Instant.now());

        Consultation saved = consultationRepository.save(c);
        log.info("[PendingDoctorConsultationSeeder] Seeded DOCTOR PENDING consultation id={} near Tunis.",
                saved.getId());
    }

    private Provider getOrCreateDoctorProvider() {
        Provider existing = providerRepository.findAll().stream()
                .filter(p -> p != null && p.getType() == ProviderType.DOCTOR)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        final String email = "seed.doctor@dev.local";
        final String password = "Passw0rd!123";

        User doctorUser = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "Seed Doctor");
            u.setRole(UserRole.DOCTOR);
            u.setRegistrationStatus(RegistrationStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setPasswordHash(passwordEncoder.encode(password));
            return userRepository.save(u);
        });

        if (doctorUser.getRole() != UserRole.DOCTOR) {
            log.warn("[PendingDoctorConsultationSeeder] Existing seed user is not DOCTOR (email={}).", email);
            return null;
        }

        Provider provider = providerRepository.findByUserId(doctorUser.getId())
                .orElseGet(() -> providerRepository.save(new Provider(doctorUser, ProviderType.DOCTOR)));

        if (provider.getServiceRadiusKm() == null || provider.getServiceRadiusKm() <= 0) {
            provider.setServiceRadiusKm(50);
        }
        if (provider.getLatitude() == null || provider.getLongitude() == null) {
            provider.setLatitude(36.833);
            provider.setLongitude(10.193);
        }
        providerRepository.save(provider);

        doctorRepository.findByProviderId(provider.getId())
                .orElseGet(() -> doctorRepository.save(new Doctor(provider)));
        return provider;
    }

    private Patient getOrCreatePatient() {
        Patient existing = patientRepository.findAll().stream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }

        final String email = "seed.patient@dev.local";
        final String password = "Passw0rd!123";

        User patientUser = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "Seed Patient");
            u.setRole(UserRole.PATIENT);
            u.setRegistrationStatus(RegistrationStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setPasswordHash(passwordEncoder.encode(password));
            return userRepository.save(u);
        });

        return patientRepository.findByUserId(patientUser.getId())
                .orElseGet(() -> patientRepository.save(Patient.forUser(patientUser)));
    }
}
