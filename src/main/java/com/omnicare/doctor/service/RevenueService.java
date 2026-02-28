package com.omnicare.doctor.service;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.repository.ConsultationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class RevenueService {

    private final ConsultationRepository consultationRepository;

    public RevenueService(ConsultationRepository consultationRepository) {
        this.consultationRepository = consultationRepository;
    }

    public record RevenueTotals(
            int visitsCompletedCount,
            BigDecimal totalGrossEarnings,
            BigDecimal totalNetPart,
            BigDecimal omnicareCommission
    ) {
    }

    @Transactional(readOnly = true)
    public RevenueTotals computeTotalsForDoctor(UUID doctorId) {
        List<Consultation> completed = consultationRepository
                .findAllByDoctorIdAndStatusOrderByTimestampDesc(doctorId, ConsultationStatus.COMPLETED);

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;

        for (Consultation c : completed) {
            BigDecimal f = c.getFee() == null ? BigDecimal.ZERO : c.getFee();
            gross = gross.add(f);
            net = net.add(c.getNetAmount());
            fee = fee.add(c.getOmnicareFee());
        }

        return new RevenueTotals(completed.size(), gross, net, fee);
    }
}
