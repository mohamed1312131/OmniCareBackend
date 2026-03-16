package com.omnicare.doctor.service;

import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.model.User;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.service.ProviderService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final ProviderService providerService;

    public DoctorService(DoctorRepository doctorRepository, ProviderService providerService) {
        this.doctorRepository = doctorRepository;
        this.providerService = providerService;
    }

    @Transactional
    public Doctor ensureForDoctorUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is required");
        }
        if (user.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(user);
        return doctorRepository.findByProviderId(provider.getId())
                .orElseGet(() -> doctorRepository.save(new Doctor(provider)));
    }

    @Transactional(readOnly = true)
    public Doctor requireByDoctorUserId(UUID doctorUserId) {
        return doctorRepository.findByProviderUserId(doctorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
    }
}
