package com.omnicare.access.repository;

import com.omnicare.access.model.PatientShareToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientShareTokenRepository extends JpaRepository<PatientShareToken, UUID> {
}
