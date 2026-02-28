package com.omnicare.security.service;

import com.omnicare.config.AppProperties;
import com.omnicare.profile.model.User;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final long ttlSeconds;

    public JwtService(JwtEncoder jwtEncoder, AppProperties appProperties) {
        this.jwtEncoder = jwtEncoder;
        this.ttlSeconds = appProperties.jwt().ttlSeconds();
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();

        String email = user.getEmail();
        String name = user.getName();
        String role = user.getRole() == null ? "PATIENT" : user.getRole().name();
        String registrationStatus = user.getRegistrationStatus() == null ? "PENDING_PASSWORD" : user.getRegistrationStatus().name();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("omnicare")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(email)
                .id(jti)
                .claim("email", email)
                .claim("name", name)
                .claim("roles", List.of(role))
                .claim("registrationStatus", registrationStatus)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

}
