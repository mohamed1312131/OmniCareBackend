package com.omnicare.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, UUID> {

    List<PatientAllergy> findAllByPatientIdOrderByRecordedAtDesc(UUID patientId);

    Optional<PatientAllergy> findByIdAndPatientId(UUID id, UUID patientId);

    boolean existsByPatientIdAndSubstanceIgnoreCase(UUID patientId, String substance);
}
