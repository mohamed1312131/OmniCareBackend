package com.omnicare.family;

import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.patient.service.PatientService;
import com.omnicare.patient.repository.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FamilyMemberService {

    private static final int MAX_FAMILY_MEMBERS = 5;

    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final PatientService patientService;
    private final PatientRepository patientRepository;

    public FamilyMemberService(FamilyMemberRepository familyMemberRepository, UserRepository userRepository, PatientService patientService, PatientRepository patientRepository) {
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.patientService = patientService;
        this.patientRepository = patientRepository;
    }

    public record CreateRequest(
            String fullName,
            String firstName,
            String lastName,
            String relationship,
            String relationshipDescription,
            String phoneNumber,
            LocalDate birthDate,
            String gender,
            Map<String, Object> medicalInfo
    ) {
    }

    public record UpdateRequest(
            String fullName,
            String firstName,
            String lastName,
            String relationship,
            String relationshipDescription,
            String phoneNumber,
            LocalDate birthDate,
            String gender,
            Map<String, Object> medicalInfo
    ) {
    }

    @Transactional
    public FamilyMember createForUserEmail(String email, CreateRequest request) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        if (request.relationship() == null || request.relationship().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relationship is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        long count = familyMemberRepository.countByUserId(user.getId());
        if (count >= MAX_FAMILY_MEMBERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Family limit reached");
        }

        String resolvedFullName = null;
        if (request.fullName() != null && !request.fullName().isBlank()) {
            resolvedFullName = request.fullName().trim();
        } else {
            String fn = request.firstName() == null ? "" : request.firstName().trim();
            String ln = request.lastName() == null ? "" : request.lastName().trim();
            String joined = (fn + " " + ln).trim();
            if (!joined.isEmpty()) {
                resolvedFullName = joined;
            }
        }
        if (resolvedFullName == null || resolvedFullName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fullName or firstName/lastName is required");
        }

        FamilyMember member = new FamilyMember(user, resolvedFullName, request.relationship().trim());
        if (request.firstName() != null) {
            String trimmed = request.firstName().trim();
            member.setFirstName(trimmed.isEmpty() ? null : trimmed);
        }
        if (request.lastName() != null) {
            String trimmed = request.lastName().trim();
            member.setLastName(trimmed.isEmpty() ? null : trimmed);
        }
        if (request.relationshipDescription() != null) {
            String trimmed = request.relationshipDescription().trim();
            member.setRelationshipDescription(trimmed.isEmpty() ? null : trimmed);
        }
        if (request.phoneNumber() != null) {
            String trimmed = request.phoneNumber().trim();
            member.setPhoneNumber(trimmed.isEmpty() ? null : trimmed);
        }
        member.setBirthDate(request.birthDate());
        if (request.gender() != null && !request.gender().isBlank()) {
            member.setGender(request.gender().trim());
        }
        member.setMedicalInfo(request.medicalInfo());

        FamilyMember saved = familyMemberRepository.save(member);
        patientService.ensureForFamilyMember(user, saved);
        return saved;
    }

    public List<FamilyMember> listForUserEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return familyMemberRepository.findAllByUserId(user.getId());
    }

    @Transactional
    public void deleteForUserEmail(String email, UUID familyMemberId) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (familyMemberId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        FamilyMember member = familyMemberRepository.findByIdAndUserId(familyMemberId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));

        patientRepository.deleteByFamilyMemberId(member.getId());
        familyMemberRepository.delete(member);
    }

    @Transactional
    public FamilyMember updateForUserEmail(String email, UUID familyMemberId, UpdateRequest request) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (familyMemberId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        FamilyMember member = familyMemberRepository.findByIdAndUserId(familyMemberId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));

        if (request != null) {
            if (request.fullName() != null) {
                String trimmed = request.fullName().trim();
                if (trimmed.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fullName cannot be blank");
                }
                member.setFullName(trimmed);
            }
            if (request.firstName() != null) {
                String trimmed = request.firstName().trim();
                member.setFirstName(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.lastName() != null) {
                String trimmed = request.lastName().trim();
                member.setLastName(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.relationship() != null) {
                String trimmed = request.relationship().trim();
                if (trimmed.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relationship cannot be blank");
                }
                member.setRelationship(trimmed);
            }
            if (request.relationshipDescription() != null) {
                String trimmed = request.relationshipDescription().trim();
                member.setRelationshipDescription(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.phoneNumber() != null) {
                String trimmed = request.phoneNumber().trim();
                member.setPhoneNumber(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.birthDate() != null) {
                member.setBirthDate(request.birthDate());
            }
            if (request.gender() != null) {
                String trimmed = request.gender().trim();
                member.setGender(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.medicalInfo() != null) {
                member.setMedicalInfo(request.medicalInfo());
            }
        }

        return familyMemberRepository.save(member);
    }
}
