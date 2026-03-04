package com.omnicare.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenRevocationService tokenRevocationService;

    public RevokedTokenValidator(TokenRevocationService tokenRevocationService) {
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (tokenRevocationService.isRevoked(token.getId())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token has been revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
