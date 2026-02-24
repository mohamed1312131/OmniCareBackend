package com.omnicare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        OAuth2 oauth2,
        Jwt jwt,
        Mail mail
) {

    public record Cors(String allowedOrigin) {
    }

    public record OAuth2(String redirectUri) {
    }

    public record Jwt(String secret, long ttlSeconds) {
    }

    public record Mail(String from, String verifyBaseUrl) {
    }
}
