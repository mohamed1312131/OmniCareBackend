package com.omnicare.medicalact.service;

import com.omnicare.medicalact.model.MedicalActCatalog;
import com.omnicare.medicalact.repository.MedicalActCatalogRepository;
import com.omnicare.provider.model.ProviderType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class MedicalActCatalogSeeder implements ApplicationRunner {

    private final MedicalActCatalogRepository medicalActCatalogRepository;

    public MedicalActCatalogSeeder(MedicalActCatalogRepository medicalActCatalogRepository) {
        this.medicalActCatalogRepository = medicalActCatalogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedNurseCatalog();
        seedKineCatalog();
    }

    private void seedNurseCatalog() {
        List<MedicalActCatalog> nurse = List.of(
                new MedicalActCatalog("NURSE_IM_INJECTION", "Intramuscular (IM) Injection", new BigDecimal("12.00"), 10, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_IV_INJECTION", "Intravenous (IV) Injection", new BigDecimal("18.00"), 20, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_SIMPLE_WOUND_DRESSING", "Simple Wound Dressing", new BigDecimal("15.00"), 20, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_COMPLEX_WOUND_CARE", "Complex/Post-Surgical Wound Care", new BigDecimal("25.00"), 35, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_BLOOD_SAMPLE", "Blood Sample Collection (Prélèvement)", new BigDecimal("14.00"), 15, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_SUTURE_REMOVAL", "Suture/Staple Removal", new BigDecimal("16.00"), 20, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_VITALS_MONITORING", "Blood Pressure & Vital Signs Monitoring", new BigDecimal("10.00"), 10, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_URINARY_CATHETER", "Urinary Catheterization", new BigDecimal("22.00"), 30, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_NEBULIZER", "Aerosol/Nebulizer Therapy", new BigDecimal("13.00"), 20, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_STOMA_CARE", "Stoma Care", new BigDecimal("20.00"), 30, ProviderType.NURSE),
                new MedicalActCatalog("NURSE_OTHER_COMPLEX_CARE", "Other / Complex Care", new BigDecimal("0.00"), 30, ProviderType.NURSE)
        );

        upsertByCode(nurse);
    }

    private void seedKineCatalog() {
        List<MedicalActCatalog> kine = List.of(
                new MedicalActCatalog("KINE_MANUAL_THERAPY", "Manual Therapy / Massage", new BigDecimal("20.00"), 30, ProviderType.KINE),
                new MedicalActCatalog("KINE_RESPIRATORY", "Respiratory Kinesiology", new BigDecimal("22.00"), 35, ProviderType.KINE),
                new MedicalActCatalog("KINE_NEURO_REHAB", "Neurological Rehabilitation", new BigDecimal("25.00"), 45, ProviderType.KINE),
                new MedicalActCatalog("KINE_ELECTRO_ULTRASOUND", "Electrotherapy / Ultrasound", new BigDecimal("18.00"), 25, ProviderType.KINE),
                new MedicalActCatalog("KINE_POST_TRAUMATIC_MOB", "Post-Traumatic Mobilization", new BigDecimal("23.00"), 40, ProviderType.KINE)
        );

        upsertByCode(kine);
    }

    private void upsertByCode(List<MedicalActCatalog> acts) {
        for (MedicalActCatalog incoming : acts) {
            medicalActCatalogRepository.findByCodeIgnoreCase(incoming.getCode())
                    .ifPresentOrElse(existing -> {
                        existing.setName(incoming.getName());
                        existing.setBasePrice(incoming.getBasePrice());
                        existing.setEstimatedDurationMinutes(incoming.getEstimatedDurationMinutes());
                        existing.setProviderType(incoming.getProviderType());
                        existing.setActive(true);
                        medicalActCatalogRepository.save(existing);
                    }, () -> medicalActCatalogRepository.save(incoming));
        }
    }
}
