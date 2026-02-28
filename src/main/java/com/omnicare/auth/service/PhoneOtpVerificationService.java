package com.omnicare.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.omnicare.auth.model.PhoneOtpVerification;
import com.omnicare.auth.repository.PhoneOtpVerificationRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PhoneOtpVerificationService {

    private final PhoneOtpVerificationRepository repository;

    public PhoneOtpVerificationService(PhoneOtpVerificationRepository repository) {
        this.repository = repository;
    }

    public record IssuedOtp(String phoneNumber, String otp, Instant expiresAt) {
    }

    @Transactional
    public IssuedOtp issueOtp(String rawPhoneNumber) {
        String phoneNumber = normalizePhone(rawPhoneNumber);

        repository.deleteByPhoneNumber(phoneNumber);

        String otp = generateOtp();
        String otpHash = sha256Hex(otp);
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);

        repository.save(new PhoneOtpVerification(phoneNumber, otpHash, expiresAt));

        return new IssuedOtp(phoneNumber, otp, expiresAt);
    }

    @Transactional
    public void verifyOtpOrThrow(String rawPhoneNumber, String otp) {
        String phoneNumber = normalizePhone(rawPhoneNumber);

        if (otp == null || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otp is required");
        }

        PhoneOtpVerification v = repository.findFirstByPhoneNumberOrderByExpiresAtDesc(phoneNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No OTP requested for this phone number"));

        if (v.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OTP already used");
        }

        if (v.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "OTP expired");
        }

        v.setConsumedAt(Instant.now());
        repository.save(v);
    }

    private static String generateOtp() {
        int value = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return Integer.toString(value);
    }

    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber is required");
        }

        String phone = value.trim();
        phone = phone.replace(" ", "");
        phone = phone.replace("-", "");
        return phone;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
