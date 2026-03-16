package com.omnicare.medicalact.repository;

import com.omnicare.medicalact.model.MedicalAct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicalActRepository extends JpaRepository<MedicalAct, UUID> {

    List<MedicalAct> findAllByPrescriptionIdOrderByPerformedAtDesc(UUID prescriptionId);
}
