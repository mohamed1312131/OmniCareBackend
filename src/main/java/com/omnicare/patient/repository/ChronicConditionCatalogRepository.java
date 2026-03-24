package com.omnicare.patient.repository;

import com.omnicare.patient.model.ChronicConditionCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChronicConditionCatalogRepository extends JpaRepository<ChronicConditionCatalog, UUID> {

    Optional<ChronicConditionCatalog> findByCodeIgnoreCase(String code);

    Optional<ChronicConditionCatalog> findByNameIgnoreCase(String name);
}
