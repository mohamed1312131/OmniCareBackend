package com.omnicare.provider.service;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.repository.ConsultationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ProviderBackfillRunner implements ApplicationRunner {

    private final ConsultationRepository consultationRepository;

    public ProviderBackfillRunner(ConsultationRepository consultationRepository) {
        this.consultationRepository = consultationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Consultation> consultations = consultationRepository.findAll();
        boolean dirty = false;
        for (Consultation c : consultations) {
            if (c == null) {
                continue;
            }
            if (c.getProvider() != null) {
                continue;
            }
            if (c.getDoctor() == null || c.getDoctor().getProvider() == null) {
                continue;
            }
            c.setProvider(c.getDoctor().getProvider());
            dirty = true;
        }

        if (dirty) {
            consultationRepository.saveAll(consultations);
        }
    }
}
