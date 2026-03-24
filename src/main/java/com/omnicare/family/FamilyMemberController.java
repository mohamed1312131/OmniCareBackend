package com.omnicare.family;

import com.omnicare.passport.BloodGroup;
import com.omnicare.passport.MedicalInfoValidator;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/family")
public class FamilyMemberController {

    private final FamilyMemberService familyMemberService;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final MedicalInfoValidator medicalInfoValidator;

    public FamilyMemberController(FamilyMemberService familyMemberService, FamilyMemberRepository familyMemberRepository, UserRepository userRepository, MedicalInfoValidator medicalInfoValidator) {
        this.familyMemberService = familyMemberService;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.medicalInfoValidator = medicalInfoValidator;
    }

    public record CreateFamilyMemberRequest(
            String fullName,
            String firstName,
            String lastName,
            String relationship,
            String relationshipDescription,
            String phoneNumber,
            LocalDate birthDate,
            String gender,
            String profilePictureUrl,
            String profilePicturePublicId,
            Map<String, Object> medicalInfo
    ) {
    }

    public record UpdateFamilyMemberRequest(
            String fullName,
            String firstName,
            String lastName,
            String relationship,
            String relationshipDescription,
            String phoneNumber,
            LocalDate birthDate,
            String gender,
            String profilePictureUrl,
            String profilePicturePublicId,
            Map<String, Object> medicalInfo
    ) {
    }

    public record FamilyMemberResponse(
            UUID id,
            String fullName,
            String firstName,
            String lastName,
            String relationship,
            String relationshipDescription,
            String phoneNumber,
            LocalDate birthDate,
            Integer ageYears,
            String gender,
            String profilePictureUrl,
            String profilePicturePublicId,
            BloodGroup bloodGroup,
            Map<String, Object> medicalInfo
    ) {
        public static FamilyMemberResponse from(FamilyMember member) {
            Integer age = null;
            if (member.getBirthDate() != null) {
                age = Period.between(member.getBirthDate(), LocalDate.now()).getYears();
            }
            return new FamilyMemberResponse(
                    member.getId(),
                    member.getFullName(),
                    member.getFirstName(),
                    member.getLastName(),
                    member.getRelationship(),
                    member.getRelationshipDescription(),
                    member.getPhoneNumber(),
                    member.getBirthDate(),
                    age,
                    member.getGender(),
                    member.getProfilePictureUrl(),
                    member.getProfilePicturePublicId(),
                    member.getBloodGroup(),
                    member.getMedicalInfo()
            );
        }
    }

    public record UpdateMedicalPassportRequest(BloodGroup bloodGroup, Map<String, Object> medicalInfo) {
    }

    @PostMapping
    public FamilyMemberResponse create(Authentication authentication, @RequestBody CreateFamilyMemberRequest request) {
        String email = requireEmail(authentication);
        FamilyMember created = familyMemberService.createForUserEmail(
                email,
                new FamilyMemberService.CreateRequest(
                        request.fullName(),
                        request.firstName(),
                        request.lastName(),
                        request.relationship(),
                        request.relationshipDescription(),
                        request.phoneNumber(),
                        request.birthDate(),
                        request.gender(),
                        request.profilePictureUrl(),
                        request.profilePicturePublicId(),
                        request.medicalInfo()
                )
        );
        return FamilyMemberResponse.from(created);
    }

    @GetMapping
    public List<FamilyMemberResponse> list(Authentication authentication) {
        String email = requireEmail(authentication);
        return familyMemberService.listForUserEmail(email).stream().map(FamilyMemberResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication authentication, @PathVariable("id") UUID id) {
        String email = requireEmail(authentication);
        familyMemberService.deleteForUserEmail(email, id);
    }

    @PatchMapping("/{id}")
    public FamilyMemberResponse patch(Authentication authentication, @PathVariable("id") UUID id, @RequestBody UpdateFamilyMemberRequest request) {
        String email = requireEmail(authentication);
        FamilyMember updated = familyMemberService.updateForUserEmail(
                email,
                id,
                new FamilyMemberService.UpdateRequest(
                        request.fullName(),
                        request.firstName(),
                        request.lastName(),
                        request.relationship(),
                        request.relationshipDescription(),
                        request.phoneNumber(),
                        request.birthDate(),
                        request.gender(),
                        request.profilePictureUrl(),
                        request.profilePicturePublicId(),
                        request.medicalInfo()
                )
        );
        return FamilyMemberResponse.from(updated);
    }

    @PatchMapping("/{id}/medical-passport")
    public FamilyMemberResponse patchMedicalPassport(Authentication authentication, @PathVariable("id") UUID id, @RequestBody UpdateMedicalPassportRequest request) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        FamilyMember member = familyMemberRepository.findByIdAndUserId(id, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));

        if (request != null) {
            if (request.bloodGroup() != null) {
                member.setBloodGroup(request.bloodGroup());
            }
            if (request.medicalInfo() != null) {
                medicalInfoValidator.validateOrThrow(request.medicalInfo());
                member.setMedicalInfo(request.medicalInfo());
            }
        }

        familyMemberRepository.save(member);
        return FamilyMemberResponse.from(member);
    }

    private static String requireEmail(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email in authenticated principal");
        }
        return email;
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
