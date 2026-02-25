package com.omnicare.doctor;

import com.omnicare.user.User;
import com.omnicare.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public Doctor ensureForDoctorUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is required");
        }
        if (user.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor role required");
        }

        return doctorRepository.findByUserId(user.getId())
                .orElseGet(() -> doctorRepository.save(new Doctor(user)));
    }

    @Transactional(readOnly = true)
    public Doctor requireByDoctorUserId(UUID doctorUserId) {
        return doctorRepository.findByUserId(doctorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
    }
}
