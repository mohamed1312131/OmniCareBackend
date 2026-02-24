package com.omnicare.family;

import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import com.omnicare.patient.PatientService;
import com.omnicare.patient.PatientRepository;
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

    public record CreateRequest(String fullName, String relationship, LocalDate birthDate, String gender, Map<String, Object> medicalInfo) {
    }

    public record UpdateRequest(String fullName, String relationship, LocalDate birthDate, String gender, Map<String, Object> medicalInfo) {
    }

    @Transactional
    public FamilyMember createForUserEmail(String email, CreateRequest request) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (request == null || request.fullName() == null || request.fullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fullName is required");
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

        FamilyMember member = new FamilyMember(user, request.fullName().trim(), request.relationship().trim());
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
            if (request.relationship() != null) {
                String trimmed = request.relationship().trim();
                if (trimmed.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relationship cannot be blank");
                }
                member.setRelationship(trimmed);
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
