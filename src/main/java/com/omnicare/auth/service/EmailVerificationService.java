package com.omnicare.auth.service;

import com.omnicare.config.AppProperties;
import com.omnicare.mail.MailService;
import com.omnicare.profile.model.User;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final AppProperties appProperties;

    public EmailVerificationService(UserRepository userRepository, MailService mailService, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.appProperties = appProperties;
    }

    public record NewToken(String rawToken) {
    }

    @Transactional
    public NewToken issueVerificationToken(User user) {
        String rawToken = generateVerificationCode();
        String tokenHash = sha256Hex((user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase()) + ":" + rawToken);

        user.setEmailVerified(false);
        user.setEmailVerificationTokenHash(tokenHash);
        user.setEmailVerificationExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);

        return new NewToken(rawToken);
    }

    public void sendVerificationEmail(User user, String rawToken) {
        String subject = "Verify your email";

        String displayName = user.getName() == null || user.getName().isBlank()
                ? "there"
                : escapeHtml(user.getName().trim());
        String safeEmail = escapeHtml(user.getEmail());
        String safeCode = escapeHtml(rawToken);

        String html = """
                <!doctype html>
                <html>
                <head>
                  <meta charset=\"utf-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>Verify your email</title>
                </head>
                <body style=\"margin:0; padding:0; background:#F4F5F7; font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;\">
                  <div style=\"max-width:640px; margin:0 auto; padding:24px;\">
                    <div style=\"background:#ffffff; border:1px solid #e5e7eb; border-radius:16px; overflow:hidden;\">
                      <div style=\"background:#0F6F73; padding:20px 22px;\">
                        <div style=\"color:#ffffff; font-weight:700; letter-spacing:0.2px; font-size:16px;\">OmniCare</div>
                        <div style=\"color:#d1fae5; margin-top:4px; font-size:13px;\">Email verification</div>
                      </div>

                      <div style=\"padding:22px; color:#0f172a;\">
                        <p style=\"margin:0 0 12px; font-size:14px; line-height:1.55;\">Hi %s,</p>
                        <p style="margin:0 0 16px; font-size:14px; line-height:1.55;">Thanks for creating an OmniCare account for <strong>%s</strong>. Use this code to verify your email:</p>

                        <div style=\"margin:14px 0 18px;\">
                          <div style=\"display:inline-block; background:#F4F5F7; border:1px solid #e5e7eb; border-radius:12px; padding:12px 14px;\">
                            <div style=\"font-size:12px; color:#0F6F73; font-weight:700; letter-spacing:0.14em; text-transform:uppercase;\">Verification code</div>
                            <div style=\"margin-top:6px; font-size:26px; font-weight:800; letter-spacing:0.22em; color:#0F6F73;\">%s</div>
                          </div>
                        </div>

                        <p style="margin:0; font-size:12.5px; line-height:1.55; color:#64748b;">If you did not create this account, you can safely ignore this email.</p>
                      </div>
                    </div>

                    <div style=\"padding:12px 6px; color:#64748b; font-size:12px; text-align:center;\">
                      This message was sent automatically. Please do not reply.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(displayName, safeEmail, safeCode);

        mailService.sendHtmlEmail(appProperties.mail().from(), user.getEmail(), subject, html);
    }

    @Transactional
    public void verifyTokenOrThrow(String rawToken) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
    }

    @Transactional
    public User verifyTokenAndReturnUserOrThrow(String rawToken) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
    }

    @Transactional
    public void verifyEmailCodeOrThrow(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and code are required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Instant expiresAt = user.getEmailVerificationExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Code expired");
        }

        String expectedHash = user.getEmailVerificationTokenHash();
        if (expectedHash == null || expectedHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No verification code issued");
        }

        String providedHash = sha256Hex(normalizedEmail + ":" + code.trim());
        if (!providedHash.equals(expectedHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid code");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationExpiresAt(null);
        userRepository.save(user);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String generateVerificationCode() {
        int value = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return Integer.toString(value);
    }
}
