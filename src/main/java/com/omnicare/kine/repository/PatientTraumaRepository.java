package com.omnicare.kine.repository;

import com.omnicare.kine.model.PatientTrauma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientTraumaRepository extends JpaRepository<PatientTrauma, UUID> {

    List<PatientTrauma> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
