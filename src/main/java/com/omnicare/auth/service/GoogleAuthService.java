package com.omnicare.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.Collections;

@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId) {
        if (googleClientId == null || googleClientId.isBlank() || "__disabled__".equalsIgnoreCase(googleClientId.trim())) {
            this.verifier = null;
            return;
        }

        try {
            this.verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance()
            )
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to initialize GoogleIdTokenVerifier", e);
        }
    }

    public GoogleIdToken.Payload verifyIdTokenOrThrow(String idTokenString) {
        if (verifier == null) {
            throw new IllegalStateException("Google login is not configured on this server (missing spring.security.oauth2.client.registration.google.client-id)");
        }
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("idToken is required");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google idToken");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to verify Google idToken", e);
        }
    }
}
