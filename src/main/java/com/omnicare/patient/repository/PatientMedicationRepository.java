package com.omnicare.patient.repository;

import com.omnicare.patient.model.PatientMedication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientMedicationRepository extends JpaRepository<PatientMedication, UUID> {

    List<PatientMedication> findAllByPatientIdOrderByRecordedAtDesc(UUID patientId);

    Optional<PatientMedication> findByIdAndPatientId(UUID id, UUID patientId);

    Optional<PatientMedication> findByPatientIdAndMedicationId(UUID patientId, UUID medicationId);

    Optional<PatientMedication> findByPatientIdAndSourcePrescriptionItemId(UUID patientId, UUID sourcePrescriptionItemId);

    List<PatientMedication> findAllByPatientIdAndSourcePrescriptionId(UUID patientId, UUID sourcePrescriptionId);

    boolean existsByPatientIdAndMedicationId(UUID patientId, UUID medicationId);
}
