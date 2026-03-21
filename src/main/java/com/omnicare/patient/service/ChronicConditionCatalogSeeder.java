package com.omnicare.patient.service;

import com.omnicare.patient.model.ChronicConditionCatalog;
import com.omnicare.patient.repository.ChronicConditionCatalogRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;

@Component
public class ChronicConditionCatalogSeeder implements ApplicationRunner {

    private final ChronicConditionCatalogRepository chronicConditionCatalogRepository;

    public ChronicConditionCatalogSeeder(ChronicConditionCatalogRepository chronicConditionCatalogRepository) {
        this.chronicConditionCatalogRepository = chronicConditionCatalogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        final Set<String> existingCodesLower = new HashSet<>();
        for (ChronicConditionCatalog row : chronicConditionCatalogRepository.findAll()) {
            if (row.getCode() == null) continue;
            existingCodesLower.add(row.getCode().trim().toLowerCase(Locale.ROOT));
        }

        List<ChronicConditionCatalog> items = List.of(
                new ChronicConditionCatalog("DIABETES", "Diabetes (Type 1 or 2)"),
                new ChronicConditionCatalog("HYPERTENSION", "Hypertension (High Blood Pressure)"),
                new ChronicConditionCatalog("ASTHMA", "Asthma"),
                new ChronicConditionCatalog("COPD", "COPD (Chronic Obstructive Pulmonary Disease)"),
                new ChronicConditionCatalog("HYPOTHYROIDISM", "Hypothyroidism"),
                new ChronicConditionCatalog("CKD", "Chronic Kidney Disease"),
                new ChronicConditionCatalog("EPILEPSY", "Epilepsy"),
                new ChronicConditionCatalog("RHEUMATOID_ARTHRITIS", "Rheumatoid Arthritis"),
                new ChronicConditionCatalog("HYPERLIPIDEMIA", "Hyperlipidemia (High Cholesterol)"),
                new ChronicConditionCatalog("DEPRESSIVE_DISORDER", "Depressive Disorder")
        );

        final List<ChronicConditionCatalog> toInsert = new ArrayList<>();
        for (ChronicConditionCatalog incoming : items) {
            if (incoming == null || incoming.getCode() == null || incoming.getCode().isBlank()) {
                continue;
            }
            final String codeLower = incoming.getCode().trim().toLowerCase(Locale.ROOT);
            if (existingCodesLower.contains(codeLower)) {
                continue;
            }
            existingCodesLower.add(codeLower);
            toInsert.add(incoming);
        }

        if (!toInsert.isEmpty()) {
            chronicConditionCatalogRepository.saveAll(toInsert);
        }
    }
}
