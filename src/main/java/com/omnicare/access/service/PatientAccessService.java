package com.omnicare.access.service;

import com.omnicare.access.model.PatientProviderAccess;
import com.omnicare.access.model.PatientShareToken;
import com.omnicare.access.repository.PatientProviderAccessRepository;
import com.omnicare.access.repository.PatientShareTokenRepository;
import com.omnicare.doctor.model.ConsultationStatus;
import com.omnicare.doctor.repository.ConsultationRepository;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.repository.PatientRepository;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.service.ProviderService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PatientAccessService {

    public enum Scope {
        PASSPORT_READ,
        BODY_GRAPH_READ,
        CONSULTATIONS_READ,
        CONSULTATIONS_WRITE,
        REMINDERS_READ,
        REMINDERS_WRITE
    }

    @Transactional(readOnly = true)
    public List<UUID> listActivePatientIdsForProvider(User providerUser) {
        if (providerUser == null || providerUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!ProviderService.isProfessionalRole(providerUser.getRole()) || providerUser.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        Provider provider = providerService.ensureForProfessionalUser(providerUser);
        return patientProviderAccessRepository.findActivePatientIdsByProviderId(provider.getId());
    }

    @Transactional
    public PatientProviderAccess grantAccessFromPatientToProvider(User patientUser, UUID patientId, UUID providerId) {
        if (patientUser == null || patientUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (patientUser.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient role required");
        }
        if (patientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        if (providerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }

        Patient patient = patientRepository.findByIdAndOwnerUserId(patientId, patientUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        Provider provider = providerRepositoryRequireById(providerId);

        return patientProviderAccessRepository
                .findActiveByPatientIdAndProviderId(patient.getId(), provider.getId())
                .orElseGet(() -> patientProviderAccessRepository.save(new PatientProviderAccess(patient, provider, patientUser)));
    }

    private Provider providerRepositoryRequireById(UUID providerId) {
        // avoid injecting ProviderRepository here; use ProviderService to resolve provider
        return providerService.requireById(providerId);
    }

    private final PatientRepository patientRepository;
    private final PatientShareTokenRepository patientShareTokenRepository;
    private final PatientProviderAccessRepository patientProviderAccessRepository;
    private final ProviderService providerService;
    private final ConsultationRepository consultationRepository;

    public PatientAccessService(
            PatientRepository patientRepository,
            PatientShareTokenRepository patientShareTokenRepository,
            PatientProviderAccessRepository patientProviderAccessRepository,
            ProviderService providerService,
            ConsultationRepository consultationRepository
    ) {
        this.patientRepository = patientRepository;
        this.patientShareTokenRepository = patientShareTokenRepository;
        this.patientProviderAccessRepository = patientProviderAccessRepository;
        this.providerService = providerService;
        this.consultationRepository = consultationRepository;
    }

    @Transactional
    public PatientShareToken createShareTokenForOwnedPatient(User patientUser, UUID patientId, Duration ttl) {
        if (patientUser == null || patientUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (patientUser.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient role required");
        }
        if (patientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }

        Patient p = patientRepository.findByIdAndOwnerUserId(patientId, patientUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        Duration effectiveTtl = ttl == null ? Duration.ofDays(30) : ttl;
        if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ttl must be positive");
        }
        if (effectiveTtl.compareTo(Duration.ofDays(365)) > 0) {
            effectiveTtl = Duration.ofDays(365);
        }

        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(effectiveTtl);
        PatientShareToken row = new PatientShareToken(token, p, patientUser, expiresAt);
        return patientShareTokenRepository.save(row);
    }

    @Transactional
    public PatientProviderAccess redeemShareTokenAsProvider(User providerUser, UUID token) {
        if (providerUser == null || providerUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!ProviderService.isProfessionalRole(providerUser.getRole()) || providerUser.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token is required");
        }

        PatientShareToken share = patientShareTokenRepository.findById(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found"));

        Instant now = Instant.now();
        if (share.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token expired");
        }
        if (share.isUsed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Token already used");
        }

        Provider provider = providerService.ensureForProfessionalUser(providerUser);

        Patient patient = share.getPatient();
        if (patient == null || patient.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token patient missing");
        }

        PatientProviderAccess access = patientProviderAccessRepository
                .findActiveByPatientIdAndProviderId(patient.getId(), provider.getId())
                .orElseGet(() -> patientProviderAccessRepository.save(new PatientProviderAccess(patient, provider, share.getCreatedByUser())));

        share.markUsed(now);
        patientShareTokenRepository.save(share);

        return access;
    }

    @Transactional(readOnly = true)
    public void requireProviderAccess(User actor, UUID patientId, Scope scope) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!ProviderService.isProfessionalRole(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Provider provider = providerService.ensureForProfessionalUser(actor);
        PatientProviderAccess access = patientProviderAccessRepository.findActiveByPatientIdAndProviderId(patientId, provider.getId())
                .orElse(null);

        if (access == null) {
            if (scope == Scope.PASSPORT_READ) {
                boolean hasConsultation = consultationRepository.existsByProviderIdAndPatientIdAndStatusIn(
                        provider.getId(),
                        patientId,
                        List.of(ConsultationStatus.PENDING, ConsultationStatus.ACCEPTED, ConsultationStatus.COMPLETED)
                );
                if (hasConsultation) {
                    return;
                }
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to patient");
        }

        boolean ok = switch (scope) {
            case PASSPORT_READ -> access.isCanReadPassport();
            case BODY_GRAPH_READ -> access.isCanReadBodyGraph();
            case CONSULTATIONS_READ -> access.isCanReadConsultations();
            case CONSULTATIONS_WRITE -> access.isCanWriteConsultations();
            case REMINDERS_READ -> access.isCanReadReminders();
            case REMINDERS_WRITE -> access.isCanWriteReminders();
        };

        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access scope denied");
        }
    }

    @Transactional
    public void revokeAccessAsActor(User actor, UUID accessId) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (accessId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessId is required");
        }

        PatientProviderAccess access = patientProviderAccessRepository.findById(accessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Access grant not found"));

        boolean allowed = false;
        if (actor.getRole() == UserRole.PATIENT) {
            if (access.getPatient() != null && access.getPatient().getOwnerUser() != null) {
                allowed = actor.getId().equals(access.getPatient().getOwnerUser().getId());
            }
        } else if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            Provider provider = providerService.ensureForProfessionalUser(actor);
            allowed = access.getProvider() != null && provider.getId().equals(access.getProvider().getId());
        } else if (actor.getRole() == UserRole.ADMIN) {
            allowed = true;
        }

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (access.getRevokedAt() == null) {
            access.revoke(Instant.now(), actor);
            patientProviderAccessRepository.save(access);
        }
    }

    @Transactional
    public void reportAccessAsActor(User actor, UUID accessId) {
        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (accessId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessId is required");
        }

        PatientProviderAccess access = patientProviderAccessRepository.findById(accessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Access grant not found"));

        boolean allowed = false;
        if (actor.getRole() == UserRole.PATIENT) {
            if (access.getPatient() != null && access.getPatient().getOwnerUser() != null) {
                allowed = actor.getId().equals(access.getPatient().getOwnerUser().getId());
            }
        } else if (ProviderService.isProfessionalRole(actor.getRole()) && actor.getRole() != UserRole.ADMIN) {
            Provider provider = providerService.ensureForProfessionalUser(actor);
            allowed = access.getProvider() != null && provider.getId().equals(access.getProvider().getId());
        } else if (actor.getRole() == UserRole.ADMIN) {
            allowed = true;
        }

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        access.report(Instant.now(), actor);
        patientProviderAccessRepository.save(access);
    }
}
