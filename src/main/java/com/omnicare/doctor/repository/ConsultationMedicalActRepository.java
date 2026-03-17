package com.omnicare.doctor.repository;

import com.omnicare.doctor.model.ConsultationMedicalAct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsultationMedicalActRepository extends JpaRepository<ConsultationMedicalAct, UUID> {

    List<ConsultationMedicalAct> findAllByConsultationId(UUID consultationId);
}
