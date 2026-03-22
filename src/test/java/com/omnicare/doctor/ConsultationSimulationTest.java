package com.omnicare.doctor;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationLocationType;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.service.ProviderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@SpringBootTest
class ConsultationSimulationTest {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private ConsultationRepository consultationRepository;

    private static final List<String> URGENT_SYMPTOMS = List.of(
            "URGENT: Severe chest pain and shortness of breath",
            "High fever (40°C) and persistent vomiting",
            "Possible allergic reaction; facial swelling",
            "Sudden intense migraine and blurred vision",
            "Deep laceration on forearm; needs immediate stitches",
            "Severe abdominal pain with nausea",
            "Dizziness + fainting episodes",
            "Severe back pain radiating to leg"
    );

    @Test
    @Transactional
    void simulateRandomUrgentPendingConsultationForSeedDoctor() {
        // Uses the existing dev seed doctor created by PendingDoctorConsultationSeeder
        final String seedDoctorEmail = "seed.doctor@dev.local";

        User doctorUser = userRepository.findByEmail(seedDoctorEmail)
                .orElseThrow(() -> new IllegalStateException("Seed doctor user not found: " + seedDoctorEmail));

        Provider provider = providerService.ensureForProfessionalUser(doctorUser);

        Doctor doctor = doctorRepository.findByProviderId(provider.getId())
                .orElseGet(() -> doctorRepository.save(new Doctor(provider)));

        List<Patient> allPatients = patientRepository.findAll();
        if (allPatients.isEmpty()) {
            throw new IllegalStateException("No patients found in DB; seed patients first.");
        }

        Random r = new Random();
        Patient patient = allPatients.get(r.nextInt(allPatients.size()));

        String symptoms = URGENT_SYMPTOMS.get(r.nextInt(URGENT_SYMPTOMS.size()));
        int painLevel = 7 + r.nextInt(4); // 7-10

        Consultation c = new Consultation();
        c.setProvider(provider);
        c.setDoctor(doctor);
        c.setPatient(patient);
        c.setStatus(ConsultationStatus.PENDING);
        c.setSymptoms(symptoms);
        c.setPainLevel(painLevel);
        c.setCity("Tunis");
        c.setStreetAddress("Random urgent request");
        c.setLatitude(36.8333136);
        c.setLongitude(10.1931267);
        c.setLocationType(ConsultationLocationType.HOME);
        c.setTimestamp(Instant.now());

        Consultation saved = consultationRepository.save(c);

        String patientLabel = (patient.getUser() != null)
                ? patient.getUser().getName()
                : (patient.getFamilyMember() != null ? patient.getFamilyMember().getFullName() : "patient");

        System.out.println("Simulated PENDING consultation id=" + saved.getId()
                + " providerId=" + provider.getId()
                + " doctorId=" + doctor.getId()
                + " patientId=" + patient.getId()
                + " patientName=" + patientLabel);
    }
}
