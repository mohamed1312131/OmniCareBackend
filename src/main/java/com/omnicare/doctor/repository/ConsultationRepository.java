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

    List<Consultation> findAllByProviderIdAndStatusOrderByTimestampDesc(UUID providerId, ConsultationStatus status);

    List<Consultation> findAllByProviderIdOrderByTimestampDesc(UUID providerId);

    List<Consultation> findAllByPatientIdOrderByTimestampDesc(UUID patientId);

    Optional<Consultation> findByIdAndDoctorId(UUID id, UUID doctorId);

    Optional<Consultation> findByIdAndProviderId(UUID id, UUID providerId);

    Optional<Consultation> findByIdAndPatientId(UUID id, UUID patientId);
}
