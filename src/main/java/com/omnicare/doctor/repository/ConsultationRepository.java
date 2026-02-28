package com.omnicare.doctor.repository;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    List<Consultation> findAllByDoctorIdAndStatusOrderByTimestampDesc(UUID doctorId, ConsultationStatus status);

    List<Consultation> findAllByDoctorIdOrderByTimestampDesc(UUID doctorId);

    Optional<Consultation> findByIdAndDoctorId(UUID id, UUID doctorId);
}
