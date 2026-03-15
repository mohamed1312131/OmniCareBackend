package com.omnicare.patient.controller;

import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.patient.PatientAllergySeverity;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientAllergy;
import com.omnicare.patient.model.PatientType;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.patient.service.PatientAllergyService;
import com.omnicare.patient.service.PatientService;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PatientService patientService;
    private final PatientAllergyService patientAllergyService;
    private final PatientRepository patientRepository;

    public PatientController(UserRepository userRepository, FamilyMemberRepository familyMemberRepository, PatientService patientService, PatientAllergyService patientAllergyService, PatientRepository patientRepository) {
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.patientService = patientService;
        this.patientAllergyService = patientAllergyService;
        this.patientRepository = patientRepository;
    }

    public record PatientSummary(UUID patientId, PatientType type, String displayName, UUID userId, UUID familyMemberId) {
        static PatientSummary from(Patient p) {
            String name;
            UUID userId = null;
            UUID familyMemberId = null;
            if (p.getType() == PatientType.USER && p.getUser() != null) {
                name = p.getUser().getName();
                userId = p.getUser().getId();
            } else if (p.getFamilyMember() != null) {
                name = p.getFamilyMember().getFullName();
                familyMemberId = p.getFamilyMember().getId();
            } else {
                name = "Unknown";
            }
            return new PatientSummary(p.getId(), p.getType(), name, userId, familyMemberId);
        }
    }

    @GetMapping
    @Transactional
    public List<PatientSummary> list(Authentication authentication) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Patient> ensured = new ArrayList<>();
        ensured.add(patientService.ensureForUser(owner));
        familyMemberRepository.findAllByUserId(owner.getId())
                .forEach(fm -> ensured.add(patientService.ensureForFamilyMember(owner, fm)));

        return ensured.stream().map(PatientSummary::from).toList();
    }

    @GetMapping("/doctor")
    @Transactional(readOnly = true)
    public List<PatientSummary> listForDoctor(Authentication authentication) {
        User actor = requireUser(authentication);
        if (actor.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor role required");
        }

        List<Patient> all = new ArrayList<>(patientRepository.findAll());
        all.removeIf(p -> p.getType() == PatientType.USER && p.getUser() != null && p.getUser().getId().equals(actor.getId()));
        Collections.shuffle(all);
        return all.stream().limit(50).map(PatientSummary::from).toList();
    }

    @GetMapping("/{patientId}/allergies")
    public List<PatientAllergyResponse> listAllergies(Authentication authentication, @PathVariable UUID patientId) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return patientAllergyService.listForOwner(owner.getId(), patientId).stream().map(PatientAllergyResponse::from).toList();
    }

    public record CreateAllergyRequest(String substance, String reaction, PatientAllergySeverity severity) {
    }

    public record PatientAllergyResponse(UUID id, String substance, String reaction, PatientAllergySeverity severity) {
        static PatientAllergyResponse from(PatientAllergy a) {
            return new PatientAllergyResponse(a.getId(), a.getSubstance(), a.getReaction(), a.getSeverity());
        }
    }

    @PostMapping("/{patientId}/allergies")
    public PatientAllergyResponse addAllergy(Authentication authentication, @PathVariable UUID patientId, @RequestBody CreateAllergyRequest request) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        PatientAllergy created = patientAllergyService.addForOwner(owner.getId(), patientId, email, new PatientAllergyService.CreateRequest(request.substance(), request.reaction(), request.severity()));
        return PatientAllergyResponse.from(created);
    }

    @DeleteMapping("/{patientId}/allergies/{allergyId}")
    public void deleteAllergy(Authentication authentication, @PathVariable UUID patientId, @PathVariable UUID allergyId) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        patientAllergyService.deleteForOwner(owner.getId(), patientId, allergyId);
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

    private User requireUser(Authentication authentication) {
        String email = requireEmail(authentication);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
