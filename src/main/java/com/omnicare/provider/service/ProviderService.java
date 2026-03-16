package com.omnicare.provider.service;

import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.repository.ProviderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ProviderService {

    private final ProviderRepository providerRepository;

    public ProviderService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    public static boolean isProfessionalRole(UserRole role) {
        return role == UserRole.DOCTOR || role == UserRole.NURSE || role == UserRole.KINE || role == UserRole.PSYCHIATRIST || role == UserRole.ADMIN;
    }

    public static ProviderType mapRoleToProviderType(UserRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case DOCTOR -> ProviderType.DOCTOR;
            case NURSE -> ProviderType.NURSE;
            case KINE -> ProviderType.KINE;
            case PSYCHIATRIST -> ProviderType.PSYCHIATRIST;
            default -> null;
        };
    }

    @Transactional
    public Provider ensureForProfessionalUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is required");
        }

        if (!isProfessionalRole(user.getRole()) || user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professional role required");
        }

        ProviderType type = mapRoleToProviderType(user.getRole());
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider role");
        }

        return providerRepository.findByUserId(user.getId())
                .orElseGet(() -> providerRepository.save(new Provider(user, type)));
    }

    @Transactional(readOnly = true)
    public Provider requireByProviderUserId(UUID providerUserId) {
        return providerRepository.findByUserId(providerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider profile not found"));
    }

    @Transactional(readOnly = true)
    public Provider requireById(UUID providerId) {
        if (providerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
    }
}
