package com.omnicare.patient.service;

import com.omnicare.family.FamilyMember;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
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
        return patientRepository.findByUserId(user.getId())
                .orElseGet(() -> patientRepository.saveAndFlush(Patient.forUser(user)));
    }

    @Transactional
    public Patient ensureForFamilyMember(User ownerUser, FamilyMember familyMember) {
        return patientRepository.findByFamilyMemberId(familyMember.getId())
                .orElseGet(() -> patientRepository.saveAndFlush(Patient.forFamilyMember(ownerUser, familyMember)));
    }
}
