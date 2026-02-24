package com.omnicare.medication;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class MedicationDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MedicationDataInitializer.class);

    private final MedicationRepository medicationRepository;

    public MedicationDataInitializer(MedicationRepository medicationRepository) {
        this.medicationRepository = medicationRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (medicationRepository.count() > 0) {
            return;
        }

        ClassPathResource resource = new ClassPathResource("medications.csv");
        if (!resource.exists()) {
            log.warn("medications.csv not found on classpath; skipping import");
            return;
        }

        log.info("Importing medications from medications.csv...");

        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                .withSkipLines(1)
                .build()) {

            List<Medication> batch = new ArrayList<>(500);
            String[] row;
            long imported = 0;

            while ((row = reader.readNext()) != null) {
                if (row.length < 8) {
                    continue;
                }

                Medication med = new Medication(
                        trimToNull(row[0]),
                        trimToNull(row[1]),
                        trimToNull(row[2]),
                        trimToNull(row[3]),
                        trimToNull(row[4]),
                        trimToNull(row[5]),
                        trimToNull(row[6]),
                        trimToNull(row[7])
                );

                if (med.getName() == null || med.getName().isBlank()) {
                    continue;
                }

                batch.add(med);
                if (batch.size() >= 500) {
                    medicationRepository.saveAll(batch);
                    imported += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                medicationRepository.saveAll(batch);
                imported += batch.size();
            }

            log.info("Medication import complete. Imported {} rows.", imported);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
