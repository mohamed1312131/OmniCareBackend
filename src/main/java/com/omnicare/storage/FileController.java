package com.omnicare.storage;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public record FileResponse(String url, String publicId, String provider) {
        public static FileResponse from(FileStorageService.StoredFile f) {
            return new FileResponse(f.url(), f.publicId(), f.provider());
        }
    }

    @PostMapping("/upload")
    public FileResponse upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String resolvedFolder = (folder == null || folder.isBlank()) ? "omnicare" : folder.trim();
        if (!isAllowedFolder(resolvedFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid folder. Must be under /omnicare (e.g. /omnicare/avatars or /omnicare/documents)."
            );
        }

        FileStorageService.StoredFile stored = fileStorageService.upload(file, resolvedFolder);
        return FileResponse.from(stored);
    }

    private static boolean isAllowedFolder(String folder) {
        String v = folder.trim();
        while (v.startsWith("/")) v = v.substring(1);
        return v.equals("omnicare") || v.startsWith("omnicare/");
    }
}
