package com.omnicare.account;

import com.omnicare.api.ApiResponse;
import com.omnicare.document.MedicalDocument;
import com.omnicare.document.MedicalDocumentRepository;
import com.omnicare.document.MedicalDocumentType;
import com.omnicare.family.FamilyMember;
import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.medication.Medication;
import com.omnicare.medication.MedicationRepository;
import com.omnicare.passport.BloodGroup;
import com.omnicare.passport.MedicalInfoValidator;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientChronicCondition;
import com.omnicare.patient.model.PatientMedication;
import com.omnicare.patient.repository.PatientChronicConditionRepository;
import com.omnicare.patient.repository.PatientMedicationRepository;
import com.omnicare.patient.service.PatientService;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.service.ProviderService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping({"/api/account", "/v1/account"})
public class AccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MedicalInfoValidator medicalInfoValidator;
    private final FamilyMemberRepository familyMemberRepository;
    private final MedicalDocumentRepository medicalDocumentRepository;
    private final MedicationRepository medicationRepository;
    private final PatientService patientService;
    private final PatientChronicConditionRepository patientChronicConditionRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final ProviderService providerService;

    public AccountController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            MedicalInfoValidator medicalInfoValidator,
            FamilyMemberRepository familyMemberRepository,
            MedicalDocumentRepository medicalDocumentRepository,
            MedicationRepository medicationRepository,
            PatientService patientService,
            PatientChronicConditionRepository patientChronicConditionRepository,
            PatientMedicationRepository patientMedicationRepository,
            ProviderService providerService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.medicalInfoValidator = medicalInfoValidator;
        this.familyMemberRepository = familyMemberRepository;
        this.medicalDocumentRepository = medicalDocumentRepository;
        this.medicationRepository = medicationRepository;
        this.patientService = patientService;
        this.patientChronicConditionRepository = patientChronicConditionRepository;
        this.patientMedicationRepository = patientMedicationRepository;
        this.providerService = providerService;
    }

    public record SetPasswordRequest(String password) {
    }

    public record MeResponse(String email, String name, boolean hasPassword) {
    }

    public record UpdateMedicalPassportRequest(BloodGroup bloodGroup, Map<String, Object> medicalInfo) {
    }

    public record UpdateProfilePictureRequest(String profilePictureUrl, String profilePicturePublicId) {
    }

    public record UserProfileResponse(String firstName, String lastName, String avatar) {
    }

    public record MedicalPassportResponse(
            String email,
            String name,
            String firstName,
            String lastName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String gender,
            UserProfileResponse profile,
            UserRole role,
            UUID providerId,
            String providerType,
            BloodGroup bloodGroup,
            Map<String, Object> medicalInfo
    ) {
        public static MedicalPassportResponse from(
                User user,
                PatientService patientService,
                PatientChronicConditionRepository patientChronicConditionRepository,
                PatientMedicationRepository patientMedicationRepository,
                ProviderService providerService
        ) {
            UserProfileResponse profile = new UserProfileResponse(user.getFirstName(), user.getLastName(), user.getProfilePictureUrl());

            UUID providerId = null;
            String providerType = null;
            if (ProviderService.isProfessionalRole(user.getRole()) && user.getRole() != UserRole.ADMIN && providerService != null) {
                Provider provider = providerService.ensureForProfessionalUser(user);
                providerId = provider.getId();
                providerType = provider.getType() == null ? null : provider.getType().name();
            }

            Map<String, Object> mergedMedicalInfo = mergeMedicalInfoFromPatientRows(
                    user,
                    patientService,
                    patientChronicConditionRepository,
                    patientMedicationRepository
            );

            return new MedicalPassportResponse(
                    user.getEmail(),
                    user.getName(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPhoneNumber(),
                    user.getDateOfBirth(),
                    user.getGender(),
                    profile,
                    user.getRole(),
                    providerId,
                    providerType,
                    user.getBloodGroup(),
                    mergedMedicalInfo
            );
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

    public record MedicalDocumentResponse(
            UUID id,
            String title,
            MedicalDocumentType type,
            LocalDate issueDate,
            String fileUrl,
            String filePublicId,
            String fileProvider
    ) {
        public static MedicalDocumentResponse from(MedicalDocument doc) {
            return new MedicalDocumentResponse(
                    doc.getId(),
                    doc.getTitle(),
                    doc.getType(),
                    doc.getIssueDate(),
                    doc.getFileUrl(),
                    doc.getFilePublicId(),
                    doc.getFileProvider()
            );
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

        final Patient patient = patientService.ensureForUser(user);
        final Map<String, Object> incomingMedicalInfo = request == null ? null : request.medicalInfo();
        persistMedicalInfo(patient, user, incomingMedicalInfo);

        if (request != null) {
            if (request.bloodGroup() != null) {
                user.setBloodGroup(request.bloodGroup());
            }
            if (request.medicalInfo() != null) {
                medicalInfoValidator.validateOrThrow(request.medicalInfo());
                user.setMedicalInfo(sanitizeMedicalInfo(request.medicalInfo()));
            }
        }

        userRepository.save(user);
        return MedicalPassportResponse.from(
                user,
                patientService,
                patientChronicConditionRepository,
                patientMedicationRepository,
                providerService
        );
    }

    @PatchMapping("/profile-picture")
    @Transactional
    public ApiResponse<MedicalPassportResponse> patchProfilePicture(
            Authentication authentication,
            @RequestBody UpdateProfilePictureRequest request
    ) {
        User user = requireUser(authentication);

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        String url = request.profilePictureUrl();
        if (url != null) {
            url = url.trim();
        }
        String publicId = request.profilePicturePublicId();
        if (publicId != null) {
            publicId = publicId.trim();
        }

        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profilePictureUrl is required");
        }

        user.setProfilePictureUrl(url);
        user.setProfilePicturePublicId((publicId == null || publicId.isBlank()) ? null : publicId);
        userRepository.save(user);

        return ApiResponse.success(
                MedicalPassportResponse.from(
                        user,
                        patientService,
                        patientChronicConditionRepository,
                        patientMedicationRepository,
                        providerService
                )
        );
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

    @GetMapping("/full-profile")
    @Transactional(readOnly = true)
    public ApiResponse<FullProfileResponse> fullProfile(Authentication authentication) {
        User user = requireUser(authentication);

        List<FamilyMemberProfile> family = familyMemberRepository.findAllByUserId(user.getId()).stream()
                .map(FamilyMemberProfile::from)
                .toList();

        List<MedicalDocumentResponse> documents = medicalDocumentRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(MedicalDocumentResponse::from)
                .toList();

        return ApiResponse.success(
                new FullProfileResponse(
                        MedicalPassportResponse.from(
                                user,
                                patientService,
                                patientChronicConditionRepository,
                                patientMedicationRepository,
                                providerService
                        ),
                        family,
                        documents
                )
        );
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

    private static Map<String, Object> mergeMedicalInfoFromPatientRows(
            User user,
            PatientService patientService,
            PatientChronicConditionRepository patientChronicConditionRepository,
            PatientMedicationRepository patientMedicationRepository
    ) {
        Map<String, Object> base = user == null || user.getMedicalInfo() == null
                ? new HashMap<>()
                : new HashMap<>(user.getMedicalInfo());

        if (user == null || patientService == null) {
            return base;
        }

        Patient patient = patientService.ensureForUser(user);
        if (patient == null || patient.getId() == null) {
            return base;
        }

        if (patientChronicConditionRepository != null) {
            List<String> chronic = patientChronicConditionRepository
                    .findAllByPatientIdOrderByRecordedAtDesc(patient.getId())
                    .stream()
                    .map(PatientChronicCondition::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
            base.put("chronicConditions", chronic);
        }

        if (patientMedicationRepository != null) {
            List<Map<String, Object>> meds = patientMedicationRepository
                    .findAllByPatientIdOrderByRecordedAtDesc(patient.getId())
                    .stream()
                    .map(pm -> {
                        Map<String, Object> row = new HashMap<>();
                        if (pm.getMedication() != null && pm.getMedication().getId() != null) {
                            row.put("medicationId", pm.getMedication().getId().toString());
                            row.put("name", pm.getMedication().getName());
                        }
                        row.put("dosage", pm.getNotes());
                        row.put("frequency", pm.getFrequency());
                        return row;
                    })
                    .toList();
            base.put("currentMedications", meds);
        }

        return base;
    }

    private void persistMedicalInfo(Patient patient, User actor, Map<String, Object> medicalInfo) {
        if (patient == null || patient.getId() == null || medicalInfo == null) {
            return;
        }

        Object ccRaw = medicalInfo.get("chronicConditions");
        List<String> chronicConditions = (ccRaw instanceof List<?> raw)
                ? raw.stream().map(e -> e == null ? null : e.toString()).filter(Objects::nonNull).toList()
                : List.of();

        List<String> desiredChronic = chronicConditions.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        List<PatientChronicCondition> existingChronic = patientChronicConditionRepository
                .findAllByPatientIdOrderByRecordedAtDesc(patient.getId());
        List<PatientChronicCondition> toDeleteChronic = existingChronic.stream()
                .filter(r -> r.getName() != null && desiredChronic.stream().noneMatch(d -> d.equalsIgnoreCase(r.getName())))
                .toList();
        if (!toDeleteChronic.isEmpty()) {
            patientChronicConditionRepository.deleteAll(toDeleteChronic);
        }

        for (String name : desiredChronic) {
            if (patientChronicConditionRepository.existsByPatientIdAndNameIgnoreCase(patient.getId(), name)) {
                continue;
            }
            PatientChronicCondition row = new PatientChronicCondition(patient, name);
            row.setCreatedByUser(actor);
            patientChronicConditionRepository.save(row);
        }

        Object medsRaw = medicalInfo.get("currentMedications");
        List<Map<String, Object>> meds = (medsRaw instanceof List<?> raw)
                ? raw.stream()
                .filter(e -> e instanceof Map<?, ?>)
                .map(e -> {
                    Map<?, ?> m = (Map<?, ?>) e;
                    Map<String, Object> out = new HashMap<>();
                    for (Map.Entry<?, ?> entry : m.entrySet()) {
                        if (entry.getKey() == null) continue;
                        out.put(entry.getKey().toString(), entry.getValue());
                    }
                    return out;
                })
                .toList()
                : List.of();

        List<PatientMedication> existingMeds = patientMedicationRepository.findAllByPatientIdOrderByRecordedAtDesc(patient.getId());
        List<String> desiredMedicationIds = meds.stream()
                .map(m -> m.get("medicationId"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        List<PatientMedication> toDeleteMeds = existingMeds.stream()
                .filter(pm -> pm.getMedication() != null && pm.getMedication().getId() != null)
                .filter(pm -> desiredMedicationIds.stream().noneMatch(id -> id.equalsIgnoreCase(pm.getMedication().getId().toString())))
                .toList();
        if (!toDeleteMeds.isEmpty()) {
            patientMedicationRepository.deleteAll(toDeleteMeds);
        }

        for (Map<String, Object> m : meds) {
            String medicationId = m.get("medicationId") == null ? null : m.get("medicationId").toString().trim();
            if (medicationId == null || medicationId.isEmpty()) {
                continue;
            }

            UUID medUuid;
            try {
                medUuid = UUID.fromString(medicationId);
            } catch (Exception ignored) {
                continue;
            }

            Medication med = medicationRepository.findById(medUuid).orElse(null);
            if (med == null) {
                continue;
            }

            String dosage = m.get("dosage") == null ? null : m.get("dosage").toString();
            String frequency = m.get("frequency") == null ? null : m.get("frequency").toString();

            PatientMedication row = patientMedicationRepository.findByPatientIdAndMedicationId(patient.getId(), medUuid)
                    .orElseGet(() -> {
                        PatientMedication created = new PatientMedication(patient, med);
                        created.setCreatedByUser(actor);
                        return created;
                    });
            row.setNotes(dosage);
            row.setFrequency(frequency);
            patientMedicationRepository.save(row);
        }
    }
}
