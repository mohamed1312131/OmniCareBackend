package com.omnicare.access.controller;

import com.omnicare.access.model.PatientProviderAccess;
import com.omnicare.access.model.PatientShareToken;
import com.omnicare.access.service.PatientAccessService;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/access")
public class PatientAccessController {

    private final UserRepository userRepository;
    private final PatientAccessService patientAccessService;

    public PatientAccessController(UserRepository userRepository, PatientAccessService patientAccessService) {
        this.userRepository = userRepository;
        this.patientAccessService = patientAccessService;
    }

    public record CreateShareTokenRequest(UUID patientId, Long ttlSeconds) {
    }

    public record ShareTokenResponse(UUID token, UUID patientId, Instant expiresAt) {
        static ShareTokenResponse from(PatientShareToken t) {
            return new ShareTokenResponse(t.getToken(), t.getPatient() == null ? null : t.getPatient().getId(), t.getExpiresAt());
        }
    }

    public record RedeemShareTokenRequest(UUID token) {
    }

    public record AccessGrantResponse(UUID id, UUID patientId, UUID providerId, Instant createdAt, Instant revokedAt, Instant reportedAt) {
        static AccessGrantResponse from(PatientProviderAccess a) {
            return new AccessGrantResponse(
                    a.getId(),
                    a.getPatient() == null ? null : a.getPatient().getId(),
                    a.getProvider() == null ? null : a.getProvider().getId(),
                    a.getCreatedAt(),
                    a.getRevokedAt(),
                    a.getReportedAt()
            );
        }
    }

    @PostMapping("/share-token")
    @Transactional
    public ShareTokenResponse createShareToken(Authentication authentication, @RequestBody CreateShareTokenRequest request) {
        User actor = requireUser(authentication);
        if (request == null || request.patientId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        Duration ttl = request.ttlSeconds() == null ? null : Duration.ofSeconds(Math.max(1, request.ttlSeconds()));
        PatientShareToken created = patientAccessService.createShareTokenForOwnedPatient(actor, request.patientId(), ttl);
        return ShareTokenResponse.from(created);
    }

    @PostMapping("/redeem")
    @Transactional
    public AccessGrantResponse redeem(Authentication authentication, @RequestBody RedeemShareTokenRequest request) {
        User actor = requireUser(authentication);
        if (request == null || request.token() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token is required");
        }
        PatientProviderAccess grant = patientAccessService.redeemShareTokenAsProvider(actor, request.token());
        return AccessGrantResponse.from(grant);
    }

    @PostMapping("/grants/{accessId}/revoke")
    @Transactional
    public void revoke(Authentication authentication, @PathVariable UUID accessId) {
        User actor = requireUser(authentication);
        patientAccessService.revokeAccessAsActor(actor, accessId);
    }

    @PostMapping("/grants/{accessId}/report")
    @Transactional
    public void report(Authentication authentication, @PathVariable UUID accessId) {
        User actor = requireUser(authentication);
        patientAccessService.reportAccessAsActor(actor, accessId);
    }

    private User requireUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
