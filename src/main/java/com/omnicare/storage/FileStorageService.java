package com.omnicare.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    record StoredFile(String url, String publicId, String provider) {
    }

    StoredFile upload(MultipartFile file, String folder);

    void delete(String publicId);
}
