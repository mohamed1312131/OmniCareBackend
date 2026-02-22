package com.omnicare.security;

import com.omnicare.user.RegistrationStatus;
import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RegistrationStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public RegistrationStatusFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }

        if (!path.startsWith("/api/")) {
            return true;
        }

        return path.equals("/api/auth/set-initial-password")
                || path.equals("/api/account/me")
                || path.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtAuth.getToken().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RegistrationStatus status = user.getRegistrationStatus();
        if (status != RegistrationStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Registration not complete\",\"registrationStatus\":\"" + (status == null ? "UNKNOWN" : status.name()) + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
