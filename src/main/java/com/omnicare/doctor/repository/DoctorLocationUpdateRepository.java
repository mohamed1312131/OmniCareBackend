package com.omnicare.doctor.repository;

import com.omnicare.doctor.model.DoctorLocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorLocationUpdateRepository extends JpaRepository<DoctorLocationUpdate, UUID> {

    List<DoctorLocationUpdate> findByConsultationIdOrderByTimestampDesc(UUID consultationId);

    Optional<DoctorLocationUpdate> findFirstByConsultationIdOrderByTimestampDesc(UUID consultationId);

    @Query("SELECT l FROM DoctorLocationUpdate l WHERE l.consultationId = :consultationId AND l.timestamp >= :since ORDER BY l.timestamp DESC")
    List<DoctorLocationUpdate> findRecentByConsultationId(@Param("consultationId") UUID consultationId, @Param("since") Instant since);

    @Query("SELECT l FROM DoctorLocationUpdate l WHERE l.consultationId = :consultationId AND l.etaRecalculated = true ORDER BY l.timestamp DESC")
    List<DoctorLocationUpdate> findEtaRecalculatedByConsultationId(@Param("consultationId") UUID consultationId);

    @Query("SELECT AVG(l.speed) FROM DoctorLocationUpdate l WHERE l.consultationId = :consultationId AND l.timestamp >= :since AND l.speed IS NOT NULL")
    Double findAverageSpeedSince(@Param("consultationId") UUID consultationId, @Param("since") Instant since);
}
