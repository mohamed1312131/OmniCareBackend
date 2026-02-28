package com.omnicare.account;

import com.omnicare.document.MedicalDocument;
import com.omnicare.document.MedicalDocumentRepository;
import com.omnicare.document.MedicalDocumentType;
import com.omnicare.family.FamilyMember;
import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.passport.BloodGroup;
import com.omnicare.passport.MedicalInfoValidator;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MedicalInfoValidator medicalInfoValidator;
    private final FamilyMemberRepository familyMemberRepository;
    private final MedicalDocumentRepository medicalDocumentRepository;

    public AccountController(UserRepository userRepository, PasswordEncoder passwordEncoder, MedicalInfoValidator medicalInfoValidator, FamilyMemberRepository familyMemberRepository, MedicalDocumentRepository medicalDocumentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.medicalInfoValidator = medicalInfoValidator;
        this.familyMemberRepository = familyMemberRepository;
        this.medicalDocumentRepository = medicalDocumentRepository;
    }

    public record SetPasswordRequest(String password) {
    }

    public record MeResponse(String email, String name, boolean hasPassword) {
    }

    public record UpdateMedicalPassportRequest(BloodGroup bloodGroup, Map<String, Object> medicalInfo) {
    }

    public record MedicalPassportResponse(String email, String name, BloodGroup bloodGroup, Map<String, Object> medicalInfo) {
        public static MedicalPassportResponse from(User user) {
            return new MedicalPassportResponse(user.getEmail(), user.getName(), user.getBloodGroup(), user.getMedicalInfo());
        }
    }

    public record FamilyMemberProfile(UUID id, String fullName, String relationship, LocalDate birthDate, Integer ageYears, String gender, BloodGroup bloodGroup, Map<String, Object> medicalInfo) {
        public static FamilyMemberProfile from(FamilyMember member) {
            Integer age = null;
            if (member.getBirthDate() != null) {
                age = Period.between(member.getBirthDate(), LocalDate.now()).getYears();
            }
            return new FamilyMemberProfile(
                    member.getId(),
                    member.getFullName(),
                    member.getRelationship(),
                    member.getBirthDate(),
                    age,
                    member.getGender(),
                    member.getBloodGroup(),
                    member.getMedicalInfo()
            );
        }
    }

    public record MedicalDocumentResponse(UUID id, String title, MedicalDocumentType type, LocalDate issueDate, String fileUrl) {
        public static MedicalDocumentResponse from(MedicalDocument doc) {
            return new MedicalDocumentResponse(doc.getId(), doc.getTitle(), doc.getType(), doc.getIssueDate(), doc.getFileUrl());
        }
    }

    public record FullProfileResponse(MedicalPassportResponse user, List<FamilyMemberProfile> familyMembers, List<MedicalDocumentResponse> documents) {
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User user = requireUser(authentication);

        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        return new MeResponse(user.getEmail(), user.getName(), hasPassword);
    }

    @PatchMapping("/medical-passport")
    @Transactional
    public MedicalPassportResponse patchMedicalPassport(Authentication authentication, @RequestBody UpdateMedicalPassportRequest request) {
        User user = requireUser(authentication);

        if (request != null) {
            if (request.bloodGroup() != null) {
                user.setBloodGroup(request.bloodGroup());
            }
            if (request.medicalInfo() != null) {
                medicalInfoValidator.validateOrThrow(request.medicalInfo());
                user.setMedicalInfo(request.medicalInfo());
            }
        }

        userRepository.save(user);
        return MedicalPassportResponse.from(user);
    }

    @GetMapping("/full-profile")
    @Transactional(readOnly = true)
    public FullProfileResponse fullProfile(Authentication authentication) {
        User user = requireUser(authentication);

        List<FamilyMemberProfile> family = familyMemberRepository.findAllByUserId(user.getId()).stream()
                .map(FamilyMemberProfile::from)
                .toList();

        List<MedicalDocumentResponse> documents = medicalDocumentRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(MedicalDocumentResponse::from)
                .toList();

        return new FullProfileResponse(MedicalPassportResponse.from(user), family, documents);
    }

    @PostMapping("/password")
    @Transactional
    public void setPassword(Authentication authentication, @RequestBody SetPasswordRequest request) {
        User user = requireUser(authentication);

        if (request == null || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Password already set");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }

    private User requireUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email in authenticated principal");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
