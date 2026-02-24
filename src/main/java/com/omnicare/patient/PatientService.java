package com.omnicare.patient;

import com.omnicare.family.FamilyMember;
import com.omnicare.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional
    public Patient ensureForUser(User user) {
        return patientRepository.findByUserId(user.getId()).orElseGet(() -> patientRepository.save(Patient.forUser(user)));
    }

    @Transactional
    public Patient ensureForFamilyMember(User ownerUser, FamilyMember familyMember) {
        return patientRepository.findByFamilyMemberId(familyMember.getId()).orElseGet(() -> patientRepository.save(Patient.forFamilyMember(ownerUser, familyMember)));
    }
}
