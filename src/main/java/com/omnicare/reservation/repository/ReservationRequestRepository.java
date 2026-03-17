package com.omnicare.reservation.repository;

import com.omnicare.reservation.model.ReservationRequest;
import com.omnicare.reservation.model.ReservationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRequestRepository extends JpaRepository<ReservationRequest, UUID> {

    List<ReservationRequest> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<ReservationRequest> findAllByProviderIdOrderByCreatedAtDesc(UUID providerId);

    List<ReservationRequest> findAllByProviderIdAndStatusOrderByCreatedAtDesc(UUID providerId, ReservationRequestStatus status);

    Optional<ReservationRequest> findByIdAndPatientId(UUID id, UUID patientId);

    Optional<ReservationRequest> findByIdAndProviderId(UUID id, UUID providerId);
}
