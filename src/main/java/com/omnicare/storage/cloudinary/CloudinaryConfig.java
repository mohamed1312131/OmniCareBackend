package com.omnicare.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary")
public class CloudinaryConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Bean
    public Cloudinary cloudinary(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.signature-algorithm:}") String signatureAlgorithm
    ) {
        if (cloudName != null) cloudName = cloudName.trim();
        if (apiKey != null) apiKey = apiKey.trim();
        if (apiSecret != null) apiSecret = apiSecret.trim();
        if (signatureAlgorithm != null) signatureAlgorithm = signatureAlgorithm.trim();

        if (cloudName == null || cloudName.isBlank()) {
            throw new IllegalStateException("Missing app.cloudinary.cloud-name");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing app.cloudinary.api-key");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("Missing app.cloudinary.api-secret");
        }

        String maskedSecretPrefix = apiSecret.length() < 4 ? "****" : apiSecret.substring(0, 4) + "****";
        log.info(
                "[Cloudinary] Initialized with cloudName='{}', apiKey='{}', apiSecretPrefix='{}', apiSecretLength={}",
                cloudName,
                apiKey,
                maskedSecretPrefix,
                apiSecret.length()
        );

        final String effectiveSignatureAlgorithm = normalizeSignatureAlgorithm(signatureAlgorithm);
        if (effectiveSignatureAlgorithm != null && !effectiveSignatureAlgorithm.isBlank()) {
            log.info("[Cloudinary] signatureAlgorithm={}", effectiveSignatureAlgorithm);
        }

        if (apiSecret.contains("...")) {
            throw new IllegalStateException(
                    "Cloudinary api secret appears truncated (contains '...'). Set CLOUDINARY_API_SECRET to the full value."
            );
        }

        if (apiKey.length() < 8) {
            log.warn("[Cloudinary] apiKey looks unusually short (length={}). Double check CLOUDINARY_API_KEY.", apiKey.length());
        }

        Map<String, Object> config = ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        );
        if (effectiveSignatureAlgorithm != null && !effectiveSignatureAlgorithm.isBlank()) {
            config.put("signature_algorithm", effectiveSignatureAlgorithm);
        }

        return new Cloudinary(config);
    }

    private static String normalizeSignatureAlgorithm(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;

        v = v.replace("-", "").replace("_", "");
        v = v.toUpperCase();
        if (v.equals("SHA1") || v.equals("SHA256")) {
            return v;
        }
        return raw.trim();
    }

    @Bean
    public ApplicationRunner cloudinaryHealthCheckRunner(Cloudinary cloudinary) {
        return args -> {
            try {
                Map<?, ?> result = cloudinary.api().ping(ObjectUtils.emptyMap());
                log.info("[Cloudinary] ping ok: {}", result);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cloudinary credentials are invalid (ping failed). Verify CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET.",
                        e
                );
            }
        };
    }
}
