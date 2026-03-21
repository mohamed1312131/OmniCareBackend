package com.omnicare.patient;

import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class PatientBackfillInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PatientBackfillInitializer.class);

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PatientRepository patientRepository;

    public PatientBackfillInitializer(UserRepository userRepository, FamilyMemberRepository familyMemberRepository, PatientRepository patientRepository) {
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    public void run(String... args) {
        long users = userRepository.count();
        if (users == 0) {
            return;
        }

        log.info("Ensuring Patient rows exist for users/family members...");

        final Set<UUID> existingUserIds = new HashSet<>();
        final Set<UUID> existingFamilyMemberIds = new HashSet<>();
        existingUserIds.addAll(patientRepository.findAllUserIdsWithPatientRow());
        existingFamilyMemberIds.addAll(patientRepository.findAllFamilyMemberIdsWithPatientRow());

        final List<User> allUsers = userRepository.findAll();
        final List<com.omnicare.family.FamilyMember> allFamilyMembers = familyMemberRepository.findAll();

        final Set<UUID> usersNeedingPatient = new HashSet<>();
        for (User u : allUsers) {
            if (u == null || u.getId() == null) continue;
            if (!existingUserIds.contains(u.getId())) {
                usersNeedingPatient.add(u.getId());
            }
        }

        final Set<UUID> familyMembersNeedingPatient = new HashSet<>();
        for (com.omnicare.family.FamilyMember fm : allFamilyMembers) {
            if (fm == null || fm.getId() == null) continue;
            if (!existingFamilyMemberIds.contains(fm.getId())) {
                familyMembersNeedingPatient.add(fm.getId());
            }
        }

        if (!usersNeedingPatient.isEmpty() || !familyMembersNeedingPatient.isEmpty()) {
            final List<Patient> toInsert = new java.util.ArrayList<>();

            for (User u : allUsers) {
                if (u == null || u.getId() == null) continue;
                if (!usersNeedingPatient.contains(u.getId())) continue;
                toInsert.add(Patient.forUser(u));
            }

            for (com.omnicare.family.FamilyMember fm : allFamilyMembers) {
                if (fm == null || fm.getId() == null) continue;
                if (!familyMembersNeedingPatient.contains(fm.getId())) continue;
                final User owner = fm.getUser();
                if (owner == null || owner.getId() == null) continue;
                toInsert.add(Patient.forFamilyMember(owner, fm));
            }

            if (!toInsert.isEmpty()) {
                patientRepository.saveAll(toInsert);
            }
        }

        log.info("Patient backfill complete.");
    }
}
