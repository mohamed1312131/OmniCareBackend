package com.omnicare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleMapsConfig {

    private final String apiKey;

    public GoogleMapsConfig(@Value("${app.google-maps.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String apiKey() {
        return apiKey;
    }
}
