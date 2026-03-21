package com.omnicare.doctor.repository;

import com.omnicare.doctor.dto.ConsultationSummaryDTO;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    record ConsultationSummaryRow(
            UUID id,
            String patientName,
            java.time.Instant timestamp,
            ConsultationStatus status,
            java.math.BigDecimal fee,
            String symptoms,
            Double latitude,
            Double longitude
    ) {
        public ConsultationSummaryDTO toDto() {
            return new ConsultationSummaryDTO(id, patientName, timestamp, status, fee, symptoms);
        }
    }

    @Query("""
            select new com.omnicare.doctor.dto.ConsultationSummaryDTO(
                c.id,
                coalesce(fm.fullName, u.name),
                c.timestamp,
                c.status,
                c.fee,
                c.symptoms
            )
            from Consultation c
            join c.patient p
            left join p.user u
            left join p.familyMember fm
            where c.provider.id = :providerId
              and c.status = :status
            order by c.timestamp desc
            """)
    List<ConsultationSummaryDTO> findSummariesByProviderIdAndStatusOrderByTimestampDesc(
            @Param("providerId") UUID providerId,
            @Param("status") ConsultationStatus status
    );

    @Query("""
            select new com.omnicare.doctor.repository.ConsultationRepository$ConsultationSummaryRow(
                c.id,
                coalesce(fm.fullName, u.name),
                c.timestamp,
                c.status,
                c.fee,
                c.symptoms,
                c.latitude,
                c.longitude
            )
            from Consultation c
            join c.patient p
            left join p.user u
            left join p.familyMember fm
            where c.provider.id = :providerId
              and c.status = :status
            order by c.timestamp desc
            """)
    List<ConsultationSummaryRow> findSummaryRowsByProviderIdAndStatusOrderByTimestampDesc(
            @Param("providerId") UUID providerId,
            @Param("status") ConsultationStatus status
    );

    boolean existsByProviderIdAndPatientIdAndStatusIn(UUID providerId, UUID patientId, List<ConsultationStatus> status);

    @EntityGraph(attributePaths = {"patient", "patient.user", "affectedAreas"})
    List<Consultation> findAllByDoctorIdAndStatusOrderByTimestampDesc(UUID doctorId, ConsultationStatus status);

    @EntityGraph(attributePaths = {"patient", "patient.user", "affectedAreas"})
    List<Consultation> findAllByDoctorIdOrderByTimestampDesc(UUID doctorId);

    @Query("""
            select distinct c
            from Consultation c
            join fetch c.patient p
            left join fetch p.user
            left join fetch p.familyMember
            left join fetch c.affectedAreas
            where c.provider.id = :providerId
              and c.status = :status
            order by c.timestamp desc
            """)
    List<Consultation> findAllByProviderIdAndStatusOrderByTimestampDesc(
            @Param("providerId") UUID providerId,
            @Param("status") ConsultationStatus status
    );

    @EntityGraph(attributePaths = {"patient", "patient.user", "affectedAreas"})
    List<Consultation> findAllByProviderIdOrderByTimestampDesc(UUID providerId);

    @EntityGraph(attributePaths = {"patient", "patient.user", "affectedAreas"})
    List<Consultation> findAllByPatientIdOrderByTimestampDesc(UUID patientId);

    Optional<Consultation> findByIdAndDoctorId(UUID id, UUID doctorId);

    Optional<Consultation> findByIdAndProviderId(UUID id, UUID providerId);

    Optional<Consultation> findByIdAndPatientId(UUID id, UUID patientId);
}
