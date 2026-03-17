package com.omnicare.reservation.repository;

import com.omnicare.reservation.model.ProposedSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposedSlotRepository extends JpaRepository<ProposedSlot, UUID> {

    List<ProposedSlot> findAllByRequestIdOrderByDateTimeAsc(UUID reservationRequestId);

    Optional<ProposedSlot> findByIdAndRequestId(UUID id, UUID reservationRequestId);

    void deleteAllByRequestId(UUID reservationRequestId);
}
