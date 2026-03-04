package com.omnicare.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenRevocationService {

    private static final String PREFIX = "revoked:jti:";

    private final StringRedisTemplate redisTemplate;

    public TokenRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(Jwt jwt) {
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            return;
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            redisTemplate.opsForValue().set(PREFIX + jwt.getId(), "1");
            return;
        }

        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        redisTemplate.opsForValue().set(PREFIX + jwt.getId(), "1", ttl);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }
}
