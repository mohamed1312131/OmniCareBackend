package com.omnicare.kine.repository;

import com.omnicare.kine.model.TreatmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, UUID> {

    List<TreatmentPlan> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<TreatmentPlan> findAllByProviderIdOrderByCreatedAtDesc(UUID providerId);
}
