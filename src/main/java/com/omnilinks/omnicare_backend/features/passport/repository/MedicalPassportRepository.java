package com.omnilinks.omnicare_backend.features.passport.repository;

import com.omnilinks.omnicare_backend.features.passport.entity.MedicalPassport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalPassportRepository extends JpaRepository<MedicalPassport, UUID> {
    
    Optional<MedicalPassport> findByFamilyMemberId(UUID familyMemberId);
    
    boolean existsByFamilyMemberId(UUID familyMemberId);
    
    void deleteByFamilyMemberId(UUID familyMemberId);
}
