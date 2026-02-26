package com.omnicare.auth;

import com.omnicare.user.UserRepository;
import com.omnicare.user.RegistrationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class PhoneOtpController {

    private final PhoneOtpVerificationService phoneOtpVerificationService;
    private final UserRepository userRepository;

    public PhoneOtpController(PhoneOtpVerificationService phoneOtpVerificationService, UserRepository userRepository) {
        this.phoneOtpVerificationService = phoneOtpVerificationService;
        this.userRepository = userRepository;
    }

    public record RequestPhoneOtpRequest(String phoneNumber) {
    }

    public record RequestPhoneOtpResponse(String phoneNumber, String otp, Instant expiresAt) {
    }

    public record VerifyPhoneOtpRequest(String phoneNumber, String otp) {
    }

    public record VerifyPhoneOtpResponse(String message) {
    }

    @PostMapping("/request-phone-otp")
    @Transactional
    public RequestPhoneOtpResponse requestPhoneOtp(@RequestBody RequestPhoneOtpRequest request) {
        if (request == null || request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber is required");
        }

        PhoneOtpVerificationService.IssuedOtp issued = phoneOtpVerificationService.issueOtp(request.phoneNumber());
        return new RequestPhoneOtpResponse(issued.phoneNumber(), issued.otp(), issued.expiresAt());
    }

    @PostMapping("/verify-phone-otp")
    @Transactional
    public VerifyPhoneOtpResponse verifyPhoneOtp(@RequestBody VerifyPhoneOtpRequest request) {
        if (request == null || request.phoneNumber() == null || request.phoneNumber().isBlank() || request.otp() == null || request.otp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber and otp are required");
        }

        phoneOtpVerificationService.verifyOtpOrThrow(request.phoneNumber(), request.otp());

        String normalizedPhone = normalizePhoneForLookup(request.phoneNumber());
        long matches = userRepository.countByPhoneNumber(normalizedPhone);
        if (matches == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with this phone number");
        }
        if (matches > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is used by multiple users");
        }

        userRepository.findByPhoneNumber(normalizedPhone).ifPresent(user -> {
            user.setPhoneVerified(true);
            user.setRegistrationStatus(RegistrationStatus.ACTIVE);
            userRepository.save(user);
        });

        return new VerifyPhoneOtpResponse("OTP verified");
    }

    private static String normalizePhoneForLookup(String value) {
        if (value == null) {
            return null;
        }
        String phone = value.trim();
        phone = phone.replace(" ", "");
        phone = phone.replace("-", "");
        return phone;
    }
}
