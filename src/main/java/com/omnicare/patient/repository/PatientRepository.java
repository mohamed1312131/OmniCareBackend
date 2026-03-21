package com.omnicare.patient.repository;

import com.omnicare.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    Optional<Patient> findByFamilyMemberId(UUID familyMemberId);

    @Modifying
    @Transactional
    void deleteByFamilyMemberId(UUID familyMemberId);

    List<Patient> findAllByOwnerUserId(UUID ownerUserId);

    Optional<Patient> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Query("select p.user.id from Patient p where p.user is not null")
    List<UUID> findAllUserIdsWithPatientRow();

    @Query("select p.familyMember.id from Patient p where p.familyMember is not null")
    List<UUID> findAllFamilyMemberIdsWithPatientRow();

    @Query("""
            select distinct p
            from Patient p
            left join fetch p.user
            left join fetch p.familyMember
            where p.id in :ids
            """)
    List<Patient> findAllByIdInWithIdentity(@org.springframework.data.repository.query.Param("ids") List<UUID> ids);
}
