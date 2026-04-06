package com.omnicare.doctor.controller;

import com.omnicare.doctor.model.PaymentMethod;
import com.omnicare.doctor.model.Consultation;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.model.DoctorDocument;
import com.omnicare.doctor.model.DoctorDocumentStatus;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.doctor.repository.DoctorDocumentRepository;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.doctor.service.DoctorService;
import com.omnicare.doctor.service.RevenueService;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.repository.ProviderRepository;
import com.omnicare.provider.service.ProviderService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctor")
public class DoctorController {

    private final UserRepository userRepository;
    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;
    private final ProviderRepository providerRepository;
    private final ProviderService providerService;
    private final ConsultationRepository consultationRepository;
    private final RevenueService revenueService;
    private final DoctorDocumentRepository doctorDocumentRepository;

    public DoctorController(UserRepository userRepository, DoctorService doctorService,
            DoctorRepository doctorRepository, ProviderRepository providerRepository, ProviderService providerService,
            ConsultationRepository consultationRepository, RevenueService revenueService,
            DoctorDocumentRepository doctorDocumentRepository) {
        this.userRepository = userRepository;
        this.doctorService = doctorService;
        this.doctorRepository = doctorRepository;
        this.providerRepository = providerRepository;
        this.providerService = providerService;
        this.consultationRepository = consultationRepository;
        this.revenueService = revenueService;
        this.doctorDocumentRepository = doctorDocumentRepository;
    }

    public record PatchOnlineStatusRequest(Boolean isOnline, Boolean goLive) {
    }

    public record DoctorStatusResponse(UUID doctorId, boolean isOnline) {
    }

    public record PatchDoctorProfileRequest(String specialty, Integer serviceRadiusKm, Integer yearsExperience,
            Double latitude, Double longitude) {
    }

    public record VerificationDocumentResponse(UUID id, String title, String fileUrl, DoctorDocumentStatus status,
            Instant createdAt) {
        static VerificationDocumentResponse from(DoctorDocument doc) {
            return new VerificationDocumentResponse(doc.getId(), doc.getTitle(), doc.getFileUrl(), doc.getStatus(),
                    doc.getCreatedAt());
        }
    }

    public record DoctorProfileResponse(
            UUID doctorId,
            UUID userId,
            String name,
            String specialty,
            Integer yearsExperience,
            Integer totalReviews,
            BigDecimal rating,
            Integer serviceRadiusKm,
            boolean isOnline,
            Double latitude,
            Double longitude,
            List<VerificationDocumentResponse> verificationDocuments) {
        static DoctorProfileResponse from(Doctor d, List<DoctorDocument> docs) {
            if (d == null || d.getProvider() == null || d.getProvider().getUser() == null) {
                return new DoctorProfileResponse(null, null, null, null, null, null, null, null, false, null, null,
                        (docs == null ? List.of() : docs.stream().map(VerificationDocumentResponse::from).toList()));
            }
            return new DoctorProfileResponse(
                    d.getId(),
                    d.getProvider().getUser().getId(),
                    d.getProvider().getUser().getName(),
                    d.getSpecialty(),
                    d.getExperienceYears(),
                    d.getProvider().getTotalReviews(),
                    d.getProvider().getRating(),
                    d.getProvider().getServiceRadiusKm(),
                    d.getProvider().isOnline(),
                    d.getProvider().getLatitude(),
                    d.getProvider().getLongitude(),
                    (docs == null ? List.of() : docs.stream().map(VerificationDocumentResponse::from).toList()));
        }
    }

    public record ConsultationResponse(
            UUID id,
            UUID doctorId,
            UUID patientUserId,
            UUID patientFamilyMemberId,
            String patientName,
            String patientInitials,
            String symptoms,
            String diagnosis,
            String treatment,
            String clinicalNotes,
            ConsultationStatus status,
            Integer durationMinutes,
            BigDecimal fee,
            BigDecimal netAmount,
            BigDecimal omnicareFee,
            Instant timestamp) {
        static ConsultationResponse from(Consultation c) {
            UUID patientUserId = null;
            UUID familyId = null;
            if (c.getPatient() != null) {
                if (c.getPatient().getUser() != null) {
                    patientUserId = c.getPatient().getUser().getId();
                }
                if (c.getPatient().getFamilyMember() != null) {
                    familyId = c.getPatient().getFamilyMember().getId();
                }
            }

            String patientName = null;
            if (c.getPatient() != null) {
                if (c.getPatient().getUser() != null && c.getPatient().getUser().getName() != null) {
                    patientName = c.getPatient().getUser().getName();
                } else if (c.getPatient().getFamilyMember() != null
                        && c.getPatient().getFamilyMember().getFullName() != null) {
                    patientName = c.getPatient().getFamilyMember().getFullName();
                }
            }

            String initials = computeInitials(patientName);

            return new ConsultationResponse(
                    c.getId(),
                    c.getDoctor() == null ? null : c.getDoctor().getId(),
                    patientUserId,
                    familyId,
                    patientName,
                    initials,
                    c.getSymptoms(),
                    c.getDiagnosis(),
                    c.getTreatment(),
                    c.getClinicalNotes(),
                    c.getStatus(),
                    c.getDurationMinutes(),
                    c.getFee(),
                    c.getNetAmount(),
                    c.getOmnicareFee(),
                    c.getTimestamp());
        }
    }

    public record RecentTransaction(
            UUID consultationId,
            String patientName,
            Instant date,
            PaymentMethod paymentMethod,
            BigDecimal grossAmount,
            BigDecimal fee,
            BigDecimal netAmount) {
        static RecentTransaction from(Consultation c) {
            String patientName = null;
            if (c.getPatient() != null) {
                if (c.getPatient().getUser() != null && c.getPatient().getUser().getName() != null) {
                    patientName = c.getPatient().getUser().getName();
                } else if (c.getPatient().getFamilyMember() != null
                        && c.getPatient().getFamilyMember().getFullName() != null) {
                    patientName = c.getPatient().getFamilyMember().getFullName();
                }
            }

            return new RecentTransaction(
                    c.getId(),
                    patientName,
                    c.getTimestamp(),
                    c.getPaymentMethod(),
                    c.getFee(),
                    c.getOmnicareFee(),
                    c.getNetAmount());
        }
    }

    public record RevenueResponse(
            BigDecimal totalGrossEarnings,
            BigDecimal totalNetPart,
            BigDecimal omnicareCommission,
            int visitsCompletedCount,
            List<RecentTransaction> recentTransactions) {
    }

    @PatchMapping("/status")
    @Transactional
    public DoctorStatusResponse patchStatus(Authentication authentication,
            @RequestBody PatchOnlineStatusRequest request) {
        User actor = requireUser(authentication);
        requireDoctor(actor);

        Doctor doctor = doctorService.ensureForDoctorUser(actor);
        Provider provider = providerService.ensureForProfessionalUser(actor);

        Boolean desired = null;
        if (request != null) {
            if (request.goLive() != null) {
                desired = request.goLive();
            } else if (request.isOnline() != null) {
                desired = request.isOnline();
            }
        }
        boolean next = desired != null ? desired : !provider.isOnline();
        provider.setOnline(next);
        providerRepository.save(provider);
        return new DoctorStatusResponse(doctor.getId(), provider.isOnline());
    }

    @PatchMapping("/profile")
    @Transactional
    public DoctorProfileResponse patchProfile(Authentication authentication,
            @RequestBody PatchDoctorProfileRequest request) {
        User actor = requireUser(authentication);
        requireDoctor(actor);

        Doctor doctor = doctorService.ensureForDoctorUser(actor);
        Provider provider = providerService.ensureForProfessionalUser(actor);

        if (request != null) {
            if (request.specialty() != null) {
                String trimmed = request.specialty().trim();
                doctor.setSpecialty(trimmed.isEmpty() ? null : trimmed);
            }
            if (request.serviceRadiusKm() != null) {
                provider.setServiceRadiusKm(request.serviceRadiusKm());
            }
            if (request.yearsExperience() != null) {
                doctor.setExperienceYears(request.yearsExperience());
            }
            if (request.latitude() != null) {
                provider.setLatitude(request.latitude());
            }
            if (request.longitude() != null) {
                provider.setLongitude(request.longitude());
            }
        }

        doctorRepository.save(doctor);
        providerRepository.save(provider);
        List<DoctorDocument> docs = doctorDocumentRepository.findAllByDoctorIdOrderByCreatedAtDesc(doctor.getId());
        return DoctorProfileResponse.from(doctor, docs);
    }

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public DoctorProfileResponse getProfile(Authentication authentication) {
        User actor = requireUser(authentication);
        requireDoctor(actor);

        Doctor doctor = doctorService.ensureForDoctorUser(actor);
        List<DoctorDocument> docs = doctorDocumentRepository.findAllByDoctorIdOrderByCreatedAtDesc(doctor.getId());
        return DoctorProfileResponse.from(doctor, docs);
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public List<ConsultationResponse> history(Authentication authentication) {
        User actor = requireUser(authentication);
        requireDoctor(actor);

        Doctor doctor = doctorService.ensureForDoctorUser(actor);

        return consultationRepository
                .findAllByDoctorIdAndStatusOrderByTimestampDesc(doctor.getId(), ConsultationStatus.COMPLETED)
                .stream()
                .map(ConsultationResponse::from)
                .toList();
    }

    @GetMapping("/revenue")
    @Transactional(readOnly = true)
    public RevenueResponse revenue(Authentication authentication,
            @RequestParam(value = "limit", required = false) Integer limit) {
        User actor = requireUser(authentication);
        requireDoctor(actor);

        Doctor doctor = doctorService.ensureForDoctorUser(actor);
        RevenueService.RevenueTotals totals = revenueService.computeTotalsForDoctor(doctor.getId());

        int resolvedLimit = limit == null ? 10 : Math.max(1, Math.min(50, limit));
        List<RecentTransaction> recent = consultationRepository
                .findAllByDoctorIdAndStatusOrderByTimestampDesc(doctor.getId(), ConsultationStatus.COMPLETED)
                .stream()
                .limit(resolvedLimit)
                .map(RecentTransaction::from)
                .toList();

        return new RevenueResponse(
                totals.totalGrossEarnings(),
                totals.totalNetPart(),
                totals.omnicareCommission(),
                totals.visitsCompletedCount(),
                recent);
    }

    private static String computeInitials(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(t.charAt(0)));
            if (sb.length() >= 3) {
                break;
            }
        }
        return sb.toString();
    }

    private User requireUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static void requireDoctor(User user) {
        if (user.getRole() != UserRole.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor role required");
        }
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
