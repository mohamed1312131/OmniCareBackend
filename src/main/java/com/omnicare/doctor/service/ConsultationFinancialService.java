package com.omnicare.doctor.service;

import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationLocationType;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ConsultationFinancialService {

    private final BigDecimal defaultPlatformFeePercentage;
    private static final BigDecimal KINE_HOME_DISPLACEMENT_FEE = new BigDecimal("10.00");

    public ConsultationFinancialService(
            @Value("${app.platform.fee.default-percentage:15}") BigDecimal defaultPlatformFeePercentage
    ) {
        this.defaultPlatformFeePercentage = defaultPlatformFeePercentage == null ? new BigDecimal("15") : defaultPlatformFeePercentage;
    }

    public void apply(Consultation consultation) {
        if (consultation == null) {
            return;
        }

        Provider provider = consultation.getProvider();

        boolean kineHome = provider != null
                && provider.getType() == ProviderType.KINE
                && consultation.getLocationType() == ConsultationLocationType.HOME;

        if (kineHome) {
            if (consultation.getDisplacementFeeAmount() == null) {
                consultation.setDisplacementFeeAmount(KINE_HOME_DISPLACEMENT_FEE);
                BigDecimal currentFee = consultation.getFee() == null ? BigDecimal.ZERO : consultation.getFee();
                consultation.setFee(currentFee.add(KINE_HOME_DISPLACEMENT_FEE));
            }
        } else {
            consultation.setDisplacementFeeAmount(null);
        }

        BigDecimal gross = consultation.getFee() == null ? BigDecimal.ZERO : consultation.getFee();

        BigDecimal pct = provider == null ? null : provider.getPlatformFeePercentage();
        if (pct == null) {
            pct = defaultPlatformFeePercentage;
        }
        if (pct == null) {
            pct = new BigDecimal("15");
        }

        BigDecimal rate = pct.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        BigDecimal platformFeeAmount = gross.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = gross.subtract(platformFeeAmount).setScale(2, RoundingMode.HALF_UP);

        consultation.setPlatformFeePercentageApplied(pct.setScale(2, RoundingMode.HALF_UP));
        consultation.setPlatformFeeAmount(platformFeeAmount);
        consultation.setNetAmount(netAmount);
    }
}
