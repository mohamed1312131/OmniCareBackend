package com.omnicare.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.omnicare.storage.FileStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary")
public class CloudinaryStorageService implements FileStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public StoredFile upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }

        final String normalizedFolder = normalizeFolder(folder);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", normalizedFolder,
                            "resource_type", "auto"
                    )
            );

            String url = (String) result.get("secure_url");
            if (url == null || url.isBlank()) {
                url = (String) result.get("url");
            }
            String publicId = (String) result.get("public_id");

            if (url == null || url.isBlank()) {
                throw new IllegalStateException("Cloudinary upload succeeded but no URL returned");
            }
            if (publicId == null || publicId.isBlank()) {
                throw new IllegalStateException("Cloudinary upload succeeded but no public_id returned");
            }

            return new StoredFile(url, publicId, "cloudinary");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload file", e);
        }
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete file", e);
        }
    }

    private static String normalizeFolder(String folder) {
        if (folder == null) return "";
        String v = folder.trim();
        while (v.startsWith("/")) {
            v = v.substring(1);
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }
}
