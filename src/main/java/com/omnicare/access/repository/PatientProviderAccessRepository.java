package com.omnicare.access.repository;

import com.omnicare.access.model.PatientProviderAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientProviderAccessRepository extends JpaRepository<PatientProviderAccess, UUID> {

    @Query("""
            select a
            from PatientProviderAccess a
            join fetch a.patient p
            left join fetch p.user
            left join fetch p.familyMember
            where p.id = :patientId
              and a.provider.id = :providerId
              and a.revokedAt is null
            """)
    Optional<PatientProviderAccess> findActiveByPatientIdAndProviderId(
            @Param("patientId") UUID patientId,
            @Param("providerId") UUID providerId
    );

    @Query("""
            select distinct p.id
            from PatientProviderAccess a
            join a.patient p
            where a.provider.id = :providerId
              and a.revokedAt is null
            """)
    List<UUID> findActivePatientIdsByProviderId(@Param("providerId") UUID providerId);

    @Query("""
            select a
            from PatientProviderAccess a
            join fetch a.patient p
            left join fetch p.user
            left join fetch p.familyMember
            where a.provider.id = :providerId
              and a.revokedAt is null
            """)
    List<PatientProviderAccess> findAllActiveByProviderId(@Param("providerId") UUID providerId);

    @Query("select a from PatientProviderAccess a where a.patient.id = :patientId and a.revokedAt is null")
    List<PatientProviderAccess> findAllActiveByPatientId(@Param("patientId") UUID patientId);

    @Query("select (count(a) > 0) from PatientProviderAccess a where a.patient.id = :patientId and a.provider.id = :providerId and a.revokedAt is null")
    boolean existsActiveByPatientIdAndProviderId(@Param("patientId") UUID patientId, @Param("providerId") UUID providerId);
}
