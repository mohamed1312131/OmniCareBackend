package com.omnicare.body.service;

import com.omnicare.body.model.BodyPartCatalog;
import com.omnicare.body.repository.BodyPartCatalogRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class BodyPartCatalogSeeder implements ApplicationRunner {

    private final BodyPartCatalogRepository bodyPartCatalogRepository;

    public BodyPartCatalogSeeder(BodyPartCatalogRepository bodyPartCatalogRepository) {
        this.bodyPartCatalogRepository = bodyPartCatalogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Synced with OmniCareFrontend muscleParts keys.
        final Map<String, BodyPartCatalog> existingByKeyLower = new HashMap<>();
        for (BodyPartCatalog row : bodyPartCatalogRepository.findAll()) {
            if (row.getKey() == null) continue;
            existingByKeyLower.put(row.getKey().trim().toLowerCase(Locale.ROOT), row);
        }

        List<String> keys = List.of(
                "front-head",
                "front-neck",
                "back-neck",
                "front-chest-pectoral-left",
                "front-chest-pectoral-right",
                "front-abs-upper",
                "front-abs-middle",
                "front-abs-lower",
                "front-oblique-left",
                "front-oblique-right",
                "front-serratus-left",
                "front-serratus-right",
                "back-trap-upper",
                "back-lat-left",
                "back-lat-right",
                "back-spine-upper",
                "back-spine-middle",
                "back-lumbar",
                "back-glute-left",
                "back-glute-right",
                "front-shoulder-deltoid-left",
                "front-shoulder-deltoid-right",
                "back-shoulder-deltoid-left",
                "back-shoulder-deltoid-right",
                "front-bicep-left",
                "front-bicep-right",
                "back-tricep-left",
                "back-tricep-right",
                "front-forearm-left",
                "front-forearm-right",
                "back-forearm-left",
                "back-forearm-right",
                "front-hand-left",
                "front-hand-right",
                "back-hand-left",
                "back-hand-right",
                "front-thigh-quad-left",
                "front-thigh-quad-right",
                "back-thigh-hamstring-left",
                "back-thigh-hamstring-right",
                "front-knee-left",
                "front-knee-right",
                "back-knee-left",
                "back-knee-right",
                "front-shin-left",
                "front-shin-right",
                "front-calf-left",
                "front-calf-right",
                "back-calf-left",
                "back-calf-right",
                "front-foot-left",
                "front-foot-right",
                "back-foot-left",
                "back-foot-right"
        );

        final List<BodyPartCatalog> toSave = new ArrayList<>();

        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            final String trimmed = key.trim();
            final String lowered = trimmed.toLowerCase(Locale.ROOT);

            final BodyPartCatalog existing = existingByKeyLower.get(lowered);
            if (existing != null) {
                if (!existing.isActive()) {
                    existing.setActive(true);
                    toSave.add(existing);
                }
                continue;
            }

            final BodyPartCatalog created = new BodyPartCatalog(trimmed);
            existingByKeyLower.put(lowered, created);
            toSave.add(created);
        }

        if (!toSave.isEmpty()) {
            bodyPartCatalogRepository.saveAll(toSave);
        }
    }
}
