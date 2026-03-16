package com.omnicare.doctor.repository;

import com.omnicare.doctor.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Optional<Doctor> findByProviderUserId(UUID userId);

    Optional<Doctor> findByProviderId(UUID providerId);

    List<Doctor> findAllBySpecialtyIgnoreCase(String specialty);

    List<Doctor> findAllBySpecialtyIgnoreCaseContaining(String specialty);
}
