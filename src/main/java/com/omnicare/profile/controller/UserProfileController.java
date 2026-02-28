package com.omnicare.profile.controller;

import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserRepository userRepository;

    public UserProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UpdateMeRequest(String name, Map<String, Object> medicalInfo) {
    }

    public record MeProfileResponse(String email, String name, Map<String, Object> medicalInfo) {
    }

    @PatchMapping("/me")
    @Transactional
    public MeProfileResponse patchMe(Authentication authentication, @RequestBody UpdateMeRequest request) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email in authenticated principal");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request != null) {
            if (request.name() != null) {
                String trimmed = request.name().trim();
                if (trimmed.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name cannot be blank");
                }
                user.setName(trimmed);
            }

            if (request.medicalInfo() != null) {
                user.setMedicalInfo(request.medicalInfo());
            }
        }

        userRepository.save(user);
        return new MeProfileResponse(user.getEmail(), user.getName(), user.getMedicalInfo());
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
