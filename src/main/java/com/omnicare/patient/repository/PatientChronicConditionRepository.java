package com.omnicare.patient.repository;

import com.omnicare.patient.model.PatientChronicCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientChronicConditionRepository extends JpaRepository<PatientChronicCondition, UUID> {

    List<PatientChronicCondition> findAllByPatientIdOrderByRecordedAtDesc(UUID patientId);

    Optional<PatientChronicCondition> findByIdAndPatientId(UUID id, UUID patientId);

    boolean existsByPatientIdAndNameIgnoreCase(UUID patientId, String name);
}
