package com.omnilinks.omnicare_backend.features.patient.repository;

import com.omnilinks.omnicare_backend.features.patient.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {
    
    List<FamilyMember> findByOwnerId(UUID ownerId);
    
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
