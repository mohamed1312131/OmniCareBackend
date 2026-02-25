package com.omnicare.doctor;

import com.omnicare.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
public class DoctorSearchController {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;

    public DoctorSearchController(UserRepository userRepository, DoctorRepository doctorRepository) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
    }

    public record DoctorSearchResult(
            UUID doctorId,
            UUID userId,
            String name,
            String specialty,
            Integer experienceYears,
            BigDecimal rating,
            Integer serviceRadiusKm,
            boolean isOnline
    ) {
        static DoctorSearchResult from(Doctor d) {
            return new DoctorSearchResult(
                    d.getId(),
                    d.getUser().getId(),
                    d.getUser().getName(),
                    d.getSpecialty(),
                    d.getExperienceYears(),
                    d.getRating(),
                    d.getServiceRadiusKm(),
                    d.isOnline()
            );
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<DoctorSearchResult> list(
            Authentication authentication,
            @RequestParam(value = "specialty", required = false) String specialty
    ) {
        requireAuthenticated(authentication);

        List<Doctor> doctors;
        if (specialty == null || specialty.isBlank()) {
            doctors = doctorRepository.findAll();
        } else {
            doctors = doctorRepository.findAllBySpecialtyIgnoreCaseContaining(specialty.trim());
        }

        return doctors.stream().map(DoctorSearchResult::from).toList();
    }

    private void requireAuthenticated(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
