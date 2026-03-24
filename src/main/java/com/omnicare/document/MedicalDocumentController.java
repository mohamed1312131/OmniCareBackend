package com.omnicare.document;

import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping({"/api/documents", "/v1/documents"})
public class MedicalDocumentController {

    private final MedicalDocumentRepository medicalDocumentRepository;
    private final UserRepository userRepository;

    public MedicalDocumentController(MedicalDocumentRepository medicalDocumentRepository, UserRepository userRepository) {
        this.medicalDocumentRepository = medicalDocumentRepository;
        this.userRepository = userRepository;
    }

    public record CreateMedicalDocumentRequest(
            String title,
            MedicalDocumentType type,
            LocalDate issueDate,
            String fileUrl,
            String filePublicId,
            String fileProvider
    ) {
    }

    public record MedicalDocumentResponse(
            UUID id,
            String title,
            MedicalDocumentType type,
            LocalDate issueDate,
            String fileUrl,
            String filePublicId,
            String fileProvider
    ) {
        public static MedicalDocumentResponse from(MedicalDocument doc) {
            return new MedicalDocumentResponse(
                    doc.getId(),
                    doc.getTitle(),
                    doc.getType(),
                    doc.getIssueDate(),
                    doc.getFileUrl(),
                    doc.getFilePublicId(),
                    doc.getFileProvider()
            );
        }
    }

    @PostMapping
    @Transactional
    public MedicalDocumentResponse create(Authentication authentication, @RequestBody CreateMedicalDocumentRequest request) {
        User user = requireUser(authentication);
        if (user.getRole() != UserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient role required");
        }

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        if (request.fileUrl() == null || request.fileUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileUrl is required");
        }

        MedicalDocument doc = new MedicalDocument(user, request.title().trim(), request.type());
        doc.setIssueDate(request.issueDate());
        doc.setFileUrl(request.fileUrl().trim());
        if (request.filePublicId() != null) {
            String trimmed = request.filePublicId().trim();
            doc.setFilePublicId(trimmed.isEmpty() ? null : trimmed);
        }
        if (request.fileProvider() != null) {
            String trimmed = request.fileProvider().trim();
            doc.setFileProvider(trimmed.isEmpty() ? null : trimmed);
        }

        MedicalDocument saved = medicalDocumentRepository.save(doc);
        return MedicalDocumentResponse.from(saved);
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }

    private User requireUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email in authenticated principal");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
