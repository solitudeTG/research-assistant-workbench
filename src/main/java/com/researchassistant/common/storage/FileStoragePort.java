package com.researchassistant.common.storage;

import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface FileStoragePort {

    String save(MultipartFile file);

    Path resolve(String storagePath);

    void delete(String storagePath);

    static String normalizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "upload.bin";
        }

        String candidate = originalFileName.replace('\\', '/');
        int lastSlash = candidate.lastIndexOf('/');
        if (lastSlash >= 0) {
            candidate = candidate.substring(lastSlash + 1);
        }

        candidate = candidate.trim();
        if (candidate.isBlank() || ".".equals(candidate) || "..".equals(candidate)) {
            return "upload.bin";
        }

        candidate = candidate.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
        candidate = candidate.replaceAll("[. ]+$", "");
        if (candidate.isBlank() || ".".equals(candidate) || "..".equals(candidate)) {
            return "upload.bin";
        }

        return candidate;
    }
}
