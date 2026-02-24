package com.omnicare.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    Optional<Patient> findByFamilyMemberId(UUID familyMemberId);

    List<Patient> findAllByOwnerUserId(UUID ownerUserId);

    Optional<Patient> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
