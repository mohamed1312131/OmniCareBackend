package com.omnicare.auth;

import com.omnicare.config.AppProperties;
import com.omnicare.mail.MailService;
import com.omnicare.user.User;
import com.omnicare.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

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
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String tokenHash = sha256Hex(rawToken);

        user.setEmailVerified(false);
        user.setEmailVerificationTokenHash(tokenHash);
        user.setEmailVerificationExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);

        return new NewToken(rawToken);
    }

    public void sendVerificationEmail(User user, String rawToken) {
        String baseUrl = appProperties.mail().verifyBaseUrl();
        String verifyLink = baseUrl + "/api/auth/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8) + "&redirect=1";

        String subject = "Verify your email";

        String displayName = user.getName() == null || user.getName().isBlank()
                ? "there"
                : escapeHtml(user.getName().trim());
        String safeEmail = escapeHtml(user.getEmail());
        String safeLink = escapeHtml(verifyLink);

        String html = """
                <!doctype html>
                <html>
                <head>
                  <meta charset=\"utf-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>Verify your email</title>
                </head>
                <body style=\"margin:0; padding:0; background:#f3f6fb; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;\">
                  <div style=\"max-width:640px; margin:0 auto; padding:24px;\">
                    <div style=\"background:#ffffff; border:1px solid #e6edf5; border-radius:14px; overflow:hidden;\">
                      <div style=\"background:linear-gradient(135deg,#0b1220,#141b2f); padding:20px 22px;\">
                        <div style=\"color:#ffffff; font-weight:700; letter-spacing:0.2px; font-size:16px;\">Omnicare</div>
                        <div style=\"color:#cbd5e1; margin-top:4px; font-size:13px;\">Email verification</div>
                      </div>

                      <div style=\"padding:22px; color:#0f172a;\">
                        <p style=\"margin:0 0 12px; font-size:14px; line-height:1.55;\">Hi %s,</p>
                        <p style=\"margin:0 0 16px; font-size:14px; line-height:1.55;\">Thanks for creating an Omnicare account for <strong>%s</strong>. Please verify your email address to continue.</p>

                        <div style=\"margin:18px 0 18px;\">
                          <a href=\"%s\" style=\"display:inline-block; background:#2563eb; color:#ffffff; text-decoration:none; padding:12px 16px; border-radius:10px; font-weight:600; font-size:14px;\">Verify email</a>
                        </div>

                        <p style=\"margin:0 0 10px; font-size:12.5px; line-height:1.55; color:#475569;\">If the button doesn't work, copy and paste this link into your browser:</p>
                        <p style=\"margin:0 0 16px; font-size:12.5px; line-height:1.55; word-break:break-all;\"><a href=\"%s\" style=\"color:#2563eb;\">%s</a></p>

                        <p style=\"margin:0; font-size:12.5px; line-height:1.55; color:#64748b;\">If you did not create this account, you can safely ignore this email.</p>
                      </div>
                    </div>

                    <div style=\"padding:12px 6px; color:#94a3b8; font-size:12px; text-align:center;\">
                      This message was sent automatically. Please do not reply.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(displayName, safeEmail, safeLink, safeLink, safeLink);

        mailService.sendHtmlEmail(appProperties.mail().from(), user.getEmail(), subject, html);
    }

    @Transactional
    public void verifyTokenOrThrow(String rawToken) {
        verifyTokenAndReturnUserOrThrow(rawToken);
    }

    @Transactional
    public User verifyTokenAndReturnUserOrThrow(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token is required");
        }

        String tokenHash = sha256Hex(rawToken);

        User user = userRepository.findByEmailVerificationTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid token"));

        Instant expiresAt = user.getEmailVerificationExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token expired");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationExpiresAt(null);
        return userRepository.save(user);
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
}
