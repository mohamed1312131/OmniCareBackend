package com.omnicare.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

import java.util.HashMap;
import java.util.Map;

public class GooglePromptAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    public GooglePromptAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customizeIfGoogle(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customizeIfGoogleRegistration(clientRegistrationId, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customizeIfGoogle(HttpServletRequest request, OAuth2AuthorizationRequest original) {
        if (original == null) {
            return null;
        }

        String uri = request.getRequestURI();
        if (uri != null && uri.endsWith("/google")) {
            return withPrompt(original);
        }

        return original;
    }

    private OAuth2AuthorizationRequest customizeIfGoogleRegistration(String clientRegistrationId, OAuth2AuthorizationRequest original) {
        if (original == null) {
            return null;
        }

        if ("google".equalsIgnoreCase(clientRegistrationId)) {
            return withPrompt(original);
        }

        return original;
    }

    private OAuth2AuthorizationRequest withPrompt(OAuth2AuthorizationRequest original) {
        Map<String, Object> additionalParameters = new HashMap<>(original.getAdditionalParameters());
        additionalParameters.put("prompt", "select_account consent");

        return OAuth2AuthorizationRequest.from(original)
                .additionalParameters(additionalParameters)
                .build();
    }
}
