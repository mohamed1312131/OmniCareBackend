package com.omnicare.reservation.repository;

import com.omnicare.reservation.model.AvailabilityIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityIntentRepository extends JpaRepository<AvailabilityIntent, UUID> {

    List<AvailabilityIntent> findAllByRequestIdOrderByIdAsc(UUID reservationRequestId);

    void deleteAllByRequestId(UUID reservationRequestId);
}
