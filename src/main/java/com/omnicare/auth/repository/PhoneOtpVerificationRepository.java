package com.omnicare.auth.repository;

import com.omnicare.auth.model.PhoneOtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhoneOtpVerificationRepository extends JpaRepository<PhoneOtpVerification, UUID> {

    Optional<PhoneOtpVerification> findFirstByPhoneNumberOrderByExpiresAtDesc(String phoneNumber);

    long deleteByPhoneNumber(String phoneNumber);
}
