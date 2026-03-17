package com.omnicare.medicalact.repository;

import com.omnicare.medicalact.model.MedicalActCatalog;
import com.omnicare.provider.model.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalActCatalogRepository extends JpaRepository<MedicalActCatalog, UUID> {

    Optional<MedicalActCatalog> findByCodeIgnoreCase(String code);

    List<MedicalActCatalog> findAllByProviderTypeAndActiveTrueOrderByNameAsc(ProviderType providerType);
}
