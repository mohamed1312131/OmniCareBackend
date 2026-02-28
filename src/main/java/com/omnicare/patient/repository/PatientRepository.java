package com.omnicare.patient.repository;

import com.omnicare.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    Optional<Patient> findByFamilyMemberId(UUID familyMemberId);

    @Modifying
    @Transactional
    void deleteByFamilyMemberId(UUID familyMemberId);

    List<Patient> findAllByOwnerUserId(UUID ownerUserId);

    Optional<Patient> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
