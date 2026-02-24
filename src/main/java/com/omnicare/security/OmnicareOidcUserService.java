package com.omnicare.security;

import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import com.omnicare.patient.PatientService;
import com.omnicare.user.RegistrationStatus;
import com.omnicare.user.UserRole;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OmnicareOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final PatientService patientService;

    public OmnicareOidcUserService(UserRepository userRepository, PatientService patientService) {
        this.userRepository = userRepository;
        this.patientService = patientService;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Missing email from OIDC provider");
        }

        String resolvedName = (name == null || name.isBlank()) ? email : name;

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> new User(email, resolvedName));
        user.setName(resolvedName);

        if (user.getRole() == null) {
            user.setRole(UserRole.PATIENT);
        }
        if (user.getRegistrationStatus() == null) {
            user.setRegistrationStatus(RegistrationStatus.PENDING_PASSWORD);
        }

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
        }

        user = userRepository.save(user);
        patientService.ensureForUser(user);

        return oidcUser;
    }
}
