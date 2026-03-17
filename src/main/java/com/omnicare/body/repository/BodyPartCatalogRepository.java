package com.omnicare.body.repository;

import com.omnicare.body.model.BodyPartCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BodyPartCatalogRepository extends JpaRepository<BodyPartCatalog, UUID> {

    Optional<BodyPartCatalog> findByKeyIgnoreCase(String key);

    List<BodyPartCatalog> findAllByActiveTrueOrderByKeyAsc();

    long countByKeyIgnoreCaseIn(List<String> keys);
}
