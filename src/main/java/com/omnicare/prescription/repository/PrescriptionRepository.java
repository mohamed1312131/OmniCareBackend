package com.omnicare.prescription.repository;

import com.omnicare.prescription.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findAllByPatientIdOrderByIssuedAtDesc(UUID patientId);

    Optional<Prescription> findByIdAndPatientId(UUID id, UUID patientId);

    Optional<Prescription> findByConsultationId(UUID consultationId);
}
