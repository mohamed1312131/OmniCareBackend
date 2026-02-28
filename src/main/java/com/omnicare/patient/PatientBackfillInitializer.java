package com.omnicare.patient;

import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.patient.service.PatientService;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PatientBackfillInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PatientBackfillInitializer.class);

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PatientService patientService;

    public PatientBackfillInitializer(UserRepository userRepository, FamilyMemberRepository familyMemberRepository, PatientService patientService) {
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.patientService = patientService;
    }

    @Override
    public void run(String... args) {
        long users = userRepository.count();
        if (users == 0) {
            return;
        }

        log.info("Ensuring Patient rows exist for users/family members...");

        for (User user : userRepository.findAll()) {
            patientService.ensureForUser(user);
            familyMemberRepository.findAllByUserId(user.getId()).forEach(fm -> patientService.ensureForFamilyMember(user, fm));
        }

        log.info("Patient backfill complete.");
    }
}
