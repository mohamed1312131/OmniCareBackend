package com.omnicare.auth;

import com.omnicare.security.TokenRevocationService;
import com.omnicare.user.RegistrationStatus;
import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TokenRevocationService tokenRevocationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(TokenRevocationService tokenRevocationService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.tokenRevocationService = tokenRevocationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record SetInitialPasswordRequest(String password) {
    }

    @PostMapping("/logout")
    public void logout(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Jwt jwt = jwtAuth.getToken();
        tokenRevocationService.revoke(jwt);
    }

    @PostMapping("/set-initial-password")
    @Transactional
    public void setInitialPassword(Authentication authentication, @RequestBody SetInitialPasswordRequest request) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        if (request == null || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        String email = jwtAuth.getToken().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email in authenticated principal");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Password already set");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRegistrationStatus(RegistrationStatus.PENDING_OTP);
        userRepository.save(user);
    }
}
