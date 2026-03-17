package com.omnicare.patient.service;

import com.omnicare.patient.model.ChronicConditionCatalog;
import com.omnicare.patient.repository.ChronicConditionCatalogRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ChronicConditionCatalogSeeder implements ApplicationRunner {

    private final ChronicConditionCatalogRepository chronicConditionCatalogRepository;

    public ChronicConditionCatalogSeeder(ChronicConditionCatalogRepository chronicConditionCatalogRepository) {
        this.chronicConditionCatalogRepository = chronicConditionCatalogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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

        for (ChronicConditionCatalog incoming : items) {
            chronicConditionCatalogRepository.findByCodeIgnoreCase(incoming.getCode())
                    .ifPresentOrElse(existing -> {
                        existing.setName(incoming.getName());
                        existing.setActive(true);
                        chronicConditionCatalogRepository.save(existing);
                    }, () -> chronicConditionCatalogRepository.save(incoming));
        }
    }
}
