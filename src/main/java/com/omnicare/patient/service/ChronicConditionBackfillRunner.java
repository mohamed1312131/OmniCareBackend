package com.omnicare.patient.service;

import com.omnicare.patient.model.ChronicConditionCatalog;
import com.omnicare.patient.model.PatientChronicCondition;
import com.omnicare.patient.repository.ChronicConditionCatalogRepository;
import com.omnicare.patient.repository.PatientChronicConditionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ChronicConditionBackfillRunner implements ApplicationRunner {

    private final PatientChronicConditionRepository patientChronicConditionRepository;
    private final ChronicConditionCatalogRepository chronicConditionCatalogRepository;

    public ChronicConditionBackfillRunner(PatientChronicConditionRepository patientChronicConditionRepository, ChronicConditionCatalogRepository chronicConditionCatalogRepository) {
        this.patientChronicConditionRepository = patientChronicConditionRepository;
        this.chronicConditionCatalogRepository = chronicConditionCatalogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ChronicConditionCatalog> catalog = chronicConditionCatalogRepository.findAll();
        if (catalog.isEmpty()) {
            return;
        }

        Map<String, ChronicConditionCatalog> byName = new HashMap<>();
        for (ChronicConditionCatalog c : catalog) {
            if (c == null || c.getName() == null) {
                continue;
            }
            byName.put(normalize(c.getName()), c);
        }

        List<PatientChronicCondition> rows = patientChronicConditionRepository.findAll();
        boolean dirty = false;
        for (PatientChronicCondition cc : rows) {
            if (cc == null) {
                continue;
            }
            if (cc.getCondition() != null) {
                continue;
            }
            if (cc.getName() == null || cc.getName().isBlank()) {
                continue;
            }

            ChronicConditionCatalog match = byName.get(normalize(cc.getName()));
            if (match != null) {
                cc.setCondition(match);
                cc.setName(match.getName());
                dirty = true;
            }
        }

        if (dirty) {
            patientChronicConditionRepository.saveAll(rows);
        }
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
