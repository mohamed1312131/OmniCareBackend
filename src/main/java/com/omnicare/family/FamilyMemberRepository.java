package com.omnicare.family;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    long countByUserId(UUID userId);

    List<FamilyMember> findAllByUserId(UUID userId);

    Optional<FamilyMember> findByIdAndUserId(UUID id, UUID userId);
}
