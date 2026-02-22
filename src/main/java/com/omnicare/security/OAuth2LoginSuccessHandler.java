package com.omnicare.security;

import com.omnicare.config.AppProperties;
import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import com.omnicare.user.RegistrationStatus;
import com.omnicare.user.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final String redirectUri;
    private final UserRepository userRepository;

    public OAuth2LoginSuccessHandler(JwtService jwtService, AppProperties appProperties, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.redirectUri = appProperties.oauth2().redirectUri();
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
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

        user = userRepository.save(user);

        String token = jwtService.createToken(user);
        String location = redirectUri + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        response.sendRedirect(location);
    }
}
