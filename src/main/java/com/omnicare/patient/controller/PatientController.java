package com.omnicare.patient.controller;

import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.patient.PatientAllergySeverity;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientAllergy;
import com.omnicare.patient.model.PatientChronicCondition;
import com.omnicare.patient.model.PatientMedication;
import com.omnicare.patient.model.PatientType;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.patient.service.PatientAllergyService;
import com.omnicare.patient.service.PatientChronicConditionService;
import com.omnicare.patient.service.PatientMedicationService;
import com.omnicare.patient.service.PatientService;
import com.omnicare.passport.BloodGroup;
import com.omnicare.passport.MedicalInfoValidator;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.access.service.PatientAccessService;
import com.omnicare.access.service.PatientAccessService.Scope;
import com.omnicare.provider.service.ProviderService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientRepository patientRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final PatientService patientService;
    private final MedicalInfoValidator medicalInfoValidator;
    private final PatientAllergyService patientAllergyService;
    private final PatientMedicationService patientMedicationService;
    private final PatientChronicConditionService patientChronicConditionService;
    private final PatientAccessService patientAccessService;

    public PatientController(
            PatientRepository patientRepository,
            FamilyMemberRepository familyMemberRepository,
            UserRepository userRepository,
            PatientService patientService,
            MedicalInfoValidator medicalInfoValidator,
            PatientAllergyService patientAllergyService,
            PatientMedicationService patientMedicationService,
            PatientChronicConditionService patientChronicConditionService,
            PatientAccessService patientAccessService
    ) {
        this.patientRepository = patientRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.patientService = patientService;
        this.medicalInfoValidator = medicalInfoValidator;
        this.patientAllergyService = patientAllergyService;
        this.patientMedicationService = patientMedicationService;
        this.patientChronicConditionService = patientChronicConditionService;
        this.patientAccessService = patientAccessService;
    }

    public record PatientSummary(UUID patientId, PatientType type, String displayName, UUID userId, UUID familyMemberId) {
        public static PatientSummary from(Patient p) {
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

    public record PatientPassportResponse(
            UUID patientId,
            PatientType type,
            String displayName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String gender,
            BloodGroup bloodGroup,
            Map<String, Object> medicalInfo
    ) {
        static PatientPassportResponse from(Patient p) {
            String displayName;
            String phoneNumber = null;
            LocalDate dateOfBirth = null;
            String gender = null;
            BloodGroup bloodGroup = null;
            Map<String, Object> medicalInfo = null;

            if (p.getType() == PatientType.USER && p.getUser() != null) {
                displayName = p.getUser().getName();
                phoneNumber = p.getUser().getPhoneNumber();
                dateOfBirth = p.getUser().getDateOfBirth();
                gender = p.getUser().getGender();
                bloodGroup = p.getUser().getBloodGroup();
                medicalInfo = p.getUser().getMedicalInfo();
            } else if (p.getFamilyMember() != null) {
                displayName = p.getFamilyMember().getFullName();
                dateOfBirth = p.getFamilyMember().getBirthDate();
                gender = p.getFamilyMember().getGender();
                bloodGroup = p.getFamilyMember().getBloodGroup();
                medicalInfo = p.getFamilyMember().getMedicalInfo();
            } else {
                displayName = "Unknown";
            }

            return new PatientPassportResponse(
                    p.getId(),
                    p.getType(),
                    displayName,
                    phoneNumber,
                    dateOfBirth,
                    gender,
                    bloodGroup,
                    medicalInfo
            );
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
        if (!ProviderService.isProfessionalRole(actor.getRole()) || actor.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        List<UUID> patientIds = patientAccessService.listActivePatientIdsForProvider(actor);
        if (patientIds.isEmpty()) {
            return List.of();
        }

        return patientIds.stream()
                .map(id -> patientRepository.findById(id).orElse(null))
                .filter(p -> p != null)
                .map(PatientSummary::from)
                .toList();
    }

    @GetMapping("/{patientId}/passport")
    @Transactional(readOnly = true)
    public PatientPassportResponse getPassport(Authentication authentication, @PathVariable UUID patientId) {
        User actor = requireUser(authentication);
        if (!ProviderService.isProfessionalRole(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        if (actor.getRole() != UserRole.ADMIN) {
            patientAccessService.requireProviderAccess(actor, patientId, Scope.PASSPORT_READ);
        }

        Patient p = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        return PatientPassportResponse.from(p);
    }

    private static Map<String, Object> sanitizeMedicalInfo(Map<String, Object> medicalInfo) {
        if (medicalInfo == null) {
            return null;
        }
        Map<String, Object> copy = new HashMap<>(medicalInfo);
        copy.remove("currentMedications");
        copy.remove("chronicConditions");
        return copy;
    }

    public record UpdatePatientMedicalPassportRequest(BloodGroup bloodGroup, Map<String, Object> medicalInfo) {
    }

    @GetMapping("/{patientId}/medical-passport")
    @Transactional(readOnly = true)
    public PatientPassportResponse getMedicalPassport(Authentication authentication, @PathVariable UUID patientId) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Patient p = patientRepository.findByIdAndOwnerUserId(patientId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        return PatientPassportResponse.from(p);
    }

    @PostMapping("/{patientId}/medical-passport")
    @Transactional
    public PatientPassportResponse updateMedicalPassport(Authentication authentication, @PathVariable UUID patientId, @RequestBody UpdatePatientMedicalPassportRequest request) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Patient p = patientRepository.findByIdAndOwnerUserId(patientId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (request != null && request.medicalInfo() != null) {
            medicalInfoValidator.validateOrThrow(request.medicalInfo());
        }

        if (p.getType() == PatientType.USER && p.getUser() != null) {
            if (request != null && request.bloodGroup() != null) {
                p.getUser().setBloodGroup(request.bloodGroup());
            }
            if (request != null && request.medicalInfo() != null) {
                p.getUser().setMedicalInfo(sanitizeMedicalInfo(request.medicalInfo()));
            }
        } else if (p.getFamilyMember() != null) {
            if (request != null && request.bloodGroup() != null) {
                p.getFamilyMember().setBloodGroup(request.bloodGroup());
            }
            if (request != null && request.medicalInfo() != null) {
                p.getFamilyMember().setMedicalInfo(sanitizeMedicalInfo(request.medicalInfo()));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient has no subject");
        }

        return PatientPassportResponse.from(p);
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

    @GetMapping("/{patientId}/medications")
    public List<PatientMedicationResponse> listMedications(Authentication authentication, @PathVariable UUID patientId) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return patientMedicationService.listForOwner(owner.getId(), patientId).stream().map(PatientMedicationResponse::from).toList();
    }

    public record CreateMedicationRequest(UUID medicationId, Integer timesPerDay, String frequency, Integer durationDays, LocalDate startDate, String notes) {
    }

    public record PatientMedicationResponse(UUID id, UUID medicationId, String name, String dosage, String form, String dci, String type, Integer timesPerDay, String frequency, Integer durationDays, LocalDate startDate, String notes) {
        static PatientMedicationResponse from(PatientMedication pm) {
            return new PatientMedicationResponse(
                    pm.getId(),
                    pm.getMedication() != null ? pm.getMedication().getId() : null,
                    pm.getMedication() != null ? pm.getMedication().getName() : null,
                    pm.getMedication() != null ? pm.getMedication().getDosage() : null,
                    pm.getMedication() != null ? pm.getMedication().getForm() : null,
                    pm.getMedication() != null ? pm.getMedication().getDci() : null,
                    pm.getMedication() != null ? pm.getMedication().getType() : null,
                    pm.getTimesPerDay(),
                    pm.getFrequency(),
                    pm.getDurationDays(),
                    pm.getStartDate(),
                    pm.getNotes()
            );
        }
    }

    @PostMapping("/{patientId}/medications")
    public PatientMedicationResponse addMedication(Authentication authentication, @PathVariable UUID patientId, @RequestBody CreateMedicationRequest request) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        PatientMedication created = patientMedicationService.addForOwner(owner.getId(), patientId, email, new PatientMedicationService.CreateRequest(request.medicationId(), request.timesPerDay(), request.frequency(), request.durationDays(), request.startDate(), request.notes()));
        return PatientMedicationResponse.from(created);
    }

    @DeleteMapping("/{patientId}/medications/{patientMedicationId}")
    public void deleteMedication(Authentication authentication, @PathVariable UUID patientId, @PathVariable UUID patientMedicationId) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        patientMedicationService.deleteForOwner(owner.getId(), patientId, patientMedicationId);
    }

    @GetMapping("/{patientId}/conditions")
    public List<PatientChronicConditionResponse> listConditions(Authentication authentication, @PathVariable UUID patientId) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return patientChronicConditionService.listForOwner(owner.getId(), patientId).stream().map(PatientChronicConditionResponse::from).toList();
    }

    public record CreateConditionRequest(String name, String notes) {
    }

    public record PatientChronicConditionResponse(UUID id, String name, String notes) {
        static PatientChronicConditionResponse from(PatientChronicCondition cc) {
            return new PatientChronicConditionResponse(cc.getId(), cc.getName(), cc.getNotes());
        }
    }

    @PostMapping("/{patientId}/conditions")
    public PatientChronicConditionResponse addCondition(Authentication authentication, @PathVariable UUID patientId, @RequestBody CreateConditionRequest request) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        PatientChronicCondition created = patientChronicConditionService.addForOwner(owner.getId(), patientId, email, new PatientChronicConditionService.CreateRequest(request.name(), request.notes()));
        return PatientChronicConditionResponse.from(created);
    }

    @DeleteMapping("/{patientId}/conditions/{conditionId}")
    public void deleteCondition(Authentication authentication, @PathVariable UUID patientId, @PathVariable UUID conditionId) {
        String email = requireEmail(authentication);
        User owner = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        patientChronicConditionService.deleteForOwner(owner.getId(), patientId, conditionId);
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
