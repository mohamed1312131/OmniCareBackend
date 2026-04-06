package com.omnicare.auth.controller;

import com.omnicare.auth.service.PhoneOtpVerificationService;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.passport.BloodGroup;
import com.omnicare.patient.service.PatientService;
import com.omnicare.profile.model.RegistrationStatus;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.repository.ProviderRepository;
import com.omnicare.provider.service.ProviderService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth/dev")
public class DevAuthController {

    private final RequestMappingHandlerMapping handlerMapping;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final ProviderRepository providerRepository;
    private final PhoneOtpVerificationService phoneOtpVerificationService;
    private final PatientService patientService;
    private final ProviderService providerService;

    public DevAuthController(RequestMappingHandlerMapping handlerMapping, UserRepository userRepository,
            PasswordEncoder passwordEncoder, DoctorRepository doctorRepository, ProviderRepository providerRepository,
            PhoneOtpVerificationService phoneOtpVerificationService, PatientService patientService,
            ProviderService providerService) {
        this.handlerMapping = handlerMapping;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
        this.providerRepository = providerRepository;
        this.phoneOtpVerificationService = phoneOtpVerificationService;
        this.patientService = patientService;
        this.providerService = providerService;
    }

    public record CreateDoctorRequest(
            String email,
            String password,
            String name,
            String firstName,
            String lastName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String gender,
            String specialty,
            Integer yearsExperience,
            Integer serviceRadiusKm,
            Integer totalReviews,
            BigDecimal rating,
            Boolean isOnline,
            String medicalLicenseNumber) {
    }

    public record CreateDoctorResponse(String message) {
    }

    public record CreateProfessionalRequest(
            String email,
            String password,
            String name,
            UserRole role,
            String firstName,
            String lastName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String gender,
            String specialty,
            Integer yearsExperience,
            Integer serviceRadiusKm,
            Integer totalReviews,
            BigDecimal rating,
            Boolean isOnline,
            String medicalLicenseNumber) {
    }

    public record CreateProfessionalResponse(String message) {
    }

    public record CreatePatientRequest(
            String email,
            String password,
            String name,
            String firstName,
            String lastName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String gender,
            BloodGroup bloodGroup,
            Map<String, Object> medicalInfo) {
    }

    public record CreatePatientResponse(String message) {
    }

    public record CreateAdminRequest(String email, String password, String name) {
    }

    public record CreateAdminResponse(String message) {
    }

    public record RequestPhoneOtpRequest(String phoneNumber) {
    }

    public record RequestPhoneOtpResponse(String phoneNumber, String otp, Instant expiresAt) {
    }

    public record VerifyPhoneOtpRequest(String phoneNumber, String otp) {
    }

    public record VerifyPhoneOtpResponse(String message) {
    }

    @PostMapping(value = "/create-doctor")
    @Transactional
    public CreateDoctorResponse createDoctor(@RequestBody CreateDoctorRequest request) {
        if (request == null || request.email() == null || request.email().isBlank() || request.password() == null
                || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }

        String email = request.email().trim().toLowerCase();
        String resolvedName = (request.name() == null || request.name().isBlank()) ? email : request.name().trim();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User(email, resolvedName);
        user.setRole(UserRole.DOCTOR);
        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            user.setPhoneNumber(request.phoneNumber().trim());
            user.setPhoneVerified(false);
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null && !request.gender().isBlank()) {
            user.setGender(request.gender().trim());
        }

        userRepository.save(user);

        Provider provider = providerService.ensureForProfessionalUser(user);
        if (request.serviceRadiusKm() != null) {
            provider.setServiceRadiusKm(request.serviceRadiusKm());
        }
        if (request.totalReviews() != null) {
            provider.setTotalReviews(request.totalReviews());
        }
        if (request.rating() != null) {
            provider.setRating(request.rating());
        }
        if (request.isOnline() != null) {
            provider.setOnline(request.isOnline());
        }
        if (provider.getLatitude() == null || provider.getLongitude() == null) {
            provider.setLatitude(36.8065);
            provider.setLongitude(10.1815);
        }

        Doctor doctor = doctorRepository.findByProviderId(provider.getId())
                .orElseGet(() -> doctorRepository.save(new Doctor(provider)));
        if (request.specialty() != null && !request.specialty().isBlank()) {
            doctor.setSpecialty(request.specialty().trim());
        }
        if (request.yearsExperience() != null) {
            doctor.setExperienceYears(request.yearsExperience());
        }
        if (request.medicalLicenseNumber() != null && !request.medicalLicenseNumber().isBlank()) {
            doctor.setMedicalLicenseNumber(request.medicalLicenseNumber().trim());
        }

        providerRepository.save(provider);
        doctorRepository.save(doctor);

        return new CreateDoctorResponse("Doctor created and verified");
    }

    @PostMapping(value = "/create-professional")
    @Transactional
    public CreateProfessionalResponse createProfessional(@RequestBody CreateProfessionalRequest request) {
        if (request == null || request.email() == null || request.email().isBlank() || request.password() == null
                || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }
        if (request.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }

        UserRole role = request.role();
        if (!com.omnicare.provider.service.ProviderService.isProfessionalRole(role) || role == UserRole.ADMIN
                || role == UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported professional role");
        }

        String email = request.email().trim().toLowerCase();
        String resolvedName = (request.name() == null || request.name().isBlank()) ? email : request.name().trim();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User(email, resolvedName);
        user.setRole(role);
        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            user.setPhoneNumber(request.phoneNumber().trim());
            user.setPhoneVerified(false);
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null && !request.gender().isBlank()) {
            user.setGender(request.gender().trim());
        }

        userRepository.save(user);

        Provider provider = providerService.ensureForProfessionalUser(user);
        if (request.serviceRadiusKm() != null) {
            provider.setServiceRadiusKm(request.serviceRadiusKm());
        }
        if (request.totalReviews() != null) {
            provider.setTotalReviews(request.totalReviews());
        }
        if (request.rating() != null) {
            provider.setRating(request.rating());
        }
        if (request.isOnline() != null) {
            provider.setOnline(request.isOnline());
        }
        if (provider.getLatitude() == null || provider.getLongitude() == null) {
            provider.setLatitude(36.8065);
            provider.setLongitude(10.1815);
        }
        providerRepository.save(provider);

        if (role == UserRole.DOCTOR) {
            Doctor doctor = doctorRepository.findByProviderId(provider.getId())
                    .orElseGet(() -> doctorRepository.save(new Doctor(provider)));
            if (request.specialty() != null && !request.specialty().isBlank()) {
                doctor.setSpecialty(request.specialty().trim());
            }
            if (request.yearsExperience() != null) {
                doctor.setExperienceYears(request.yearsExperience());
            }
            if (request.medicalLicenseNumber() != null && !request.medicalLicenseNumber().isBlank()) {
                doctor.setMedicalLicenseNumber(request.medicalLicenseNumber().trim());
            }
            doctorRepository.save(doctor);
        }

        return new CreateProfessionalResponse("Professional created and verified");
    }

    @PostMapping(value = "/create-patient")
    @Transactional
    public CreatePatientResponse createPatient(@RequestBody CreatePatientRequest request) {
        if (request == null || request.email() == null || request.email().isBlank() || request.password() == null
                || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }

        String email = request.email().trim().toLowerCase();
        String resolvedName = (request.name() == null || request.name().isBlank()) ? email : request.name().trim();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User(email, resolvedName);
        user.setRole(UserRole.PATIENT);
        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            user.setPhoneNumber(request.phoneNumber().trim());
            user.setPhoneVerified(false);
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null && !request.gender().isBlank()) {
            user.setGender(request.gender().trim());
        }
        if (request.bloodGroup() != null) {
            user.setBloodGroup(request.bloodGroup());
        }
        if (request.medicalInfo() != null) {
            user.setMedicalInfo(request.medicalInfo());
        }

        userRepository.save(user);

        patientService.ensureForUser(user);

        return new CreatePatientResponse("Patient created and verified");
    }

    @PostMapping(value = "/create-admin")
    @Transactional
    public CreateAdminResponse createAdmin(@RequestBody CreateAdminRequest request) {
        if (request == null || request.email() == null || request.email().isBlank() || request.password() == null
                || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }

        String email = request.email().trim().toLowerCase();
        String resolvedName = (request.name() == null || request.name().isBlank()) ? email : request.name().trim();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User(email, resolvedName);
        user.setRole(UserRole.ADMIN);
        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return new CreateAdminResponse("Admin created and verified");
    }

    @PostMapping(value = "/request-phone-otp")
    @Transactional
    public RequestPhoneOtpResponse requestPhoneOtp(@RequestBody RequestPhoneOtpRequest request) {
        if (request == null || request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber is required");
        }

        PhoneOtpVerificationService.IssuedOtp issued = phoneOtpVerificationService.issueOtp(request.phoneNumber());
        return new RequestPhoneOtpResponse(issued.phoneNumber(), issued.otp(), issued.expiresAt());
    }

    @PostMapping(value = "/verify-phone-otp")
    @Transactional
    public VerifyPhoneOtpResponse verifyPhoneOtp(@RequestBody VerifyPhoneOtpRequest request) {
        if (request == null || request.phoneNumber() == null || request.phoneNumber().isBlank() || request.otp() == null
                || request.otp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber and otp are required");
        }

        phoneOtpVerificationService.verifyOtpOrThrow(request.phoneNumber(), request.otp());
        return new VerifyPhoneOtpResponse("OTP verified");
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(value = "token", required = false) String token) {
        String safeToken = token == null ? "" : htmlEscape(token);

        return "<!doctype html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\" />\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n"
                + "  <title>Omnicare Dev Auth Callback</title>\n"
                + "  <style>body{font-family:system-ui,Segoe UI,Arial;margin:24px}textarea{width:100%;height:220px}code{background:#f3f3f3;padding:2px 4px;border-radius:4px}</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <h1>OAuth2 login success</h1>\n"
                + (token == null
                        ? "  <p><b>No token</b> was provided on the query string.</p>\n"
                        : "  <p>Copy the JWT below and use it as <code>Authorization: Bearer &lt;token&gt;</code>.</p>\n")
                + (token == null
                        ? ""
                        : "  <p><a href=\"/dev/patient.html?token=" + safeToken
                                + "\">Open Dev UI with this token</a></p>\n")
                + "  <textarea readonly>" + safeToken + "</textarea>\n"
                + "  <h2>Example</h2>\n"
                + "  <pre>curl -H \"Authorization: Bearer " + safeToken
                + "\" http://localhost:8080/api/account/me</pre>\n"
                + "</body>\n"
                + "</html>\n";
    }

    @GetMapping(value = "/mappings", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String mappings() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(e -> e.getKey() + " -> " + e.getValue().getBeanType().getSimpleName() + "#"
                        + e.getValue().getMethod().getName())
                .collect(Collectors.joining("\n"));
    }

    private static String htmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
