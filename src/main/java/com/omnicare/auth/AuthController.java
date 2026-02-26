package com.omnicare.auth;

import com.omnicare.security.JwtService;
import com.omnicare.security.TokenRevocationService;
import com.omnicare.patient.PatientService;
import com.omnicare.user.RegistrationStatus;
import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import com.omnicare.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TokenRevocationService tokenRevocationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final PatientService patientService;

    public AuthController(
            TokenRevocationService tokenRevocationService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            EmailVerificationService emailVerificationService,
            PatientService patientService
    ) {
        this.tokenRevocationService = tokenRevocationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.patientService = patientService;
    }

    public record SetInitialPasswordRequest(String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record LoginResponse(String token) {
    }

    public record RegisterResponse(String message) {
    }

    public record RegisterRequest(String email, String name, String password) {
    }

    public record VerifyEmailOtpRequest(String email, String code) {
    }

    public record SetPhoneRequest(String email, String phoneNumber) {
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (request == null || request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }

        String email = request.email().trim();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email not verified");
        }

        String token = jwtService.createToken(user);
        return new LoginResponse(token);
    }

    @PostMapping("/register")
    @Transactional
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        if (request == null || request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }

        String email = request.email().trim().toLowerCase();
        String rawName = request.name();
        String resolvedName = (rawName == null || rawName.isBlank()) ? email : rawName.trim();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User(email, resolvedName);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.PATIENT);
        user.setRegistrationStatus(RegistrationStatus.PENDING_OTP);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user = userRepository.save(user);

        patientService.ensureForUser(user);

        EmailVerificationService.NewToken token = emailVerificationService.issueVerificationToken(user);
        emailVerificationService.sendVerificationEmail(user, token.rawToken());

        return new RegisterResponse("Verification email sent");
    }

    @PostMapping("/verify-email-otp")
    public RegisterResponse verifyEmailOtp(@RequestBody VerifyEmailOtpRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and code are required");
        }
        emailVerificationService.verifyEmailCodeOrThrow(request.email(), request.code());
        return new RegisterResponse("Email verified");
    }

    @PostMapping("/set-phone")
    @Transactional
    public RegisterResponse setPhone(@RequestBody SetPhoneRequest request) {
        if (request == null || request.email() == null || request.email().isBlank() || request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and phoneNumber are required");
        }

        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email not verified");
        }

        user.setPhoneNumber(normalizePhone(request.phoneNumber()));
        user.setPhoneVerified(false);
        userRepository.save(user);
        return new RegisterResponse("Phone set");
    }

    private static String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        String phone = value.trim();
        phone = phone.replace(" ", "");
        phone = phone.replace("-", "");
        return phone;
    }

    @GetMapping("/verify-email")
    public Object verifyEmail(
            @RequestParam("token") String token,
            @RequestParam(value = "redirect", required = false) String redirect
    ) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use /api/auth/verify-email-otp");
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
