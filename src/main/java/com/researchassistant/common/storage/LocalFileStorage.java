package com.researchassistant.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileStorage implements FileStoragePort {

    private final Path uploadsRoot;

    public LocalFileStorage(@Value("${app.storage.root}") String storageRoot) {
        this.uploadsRoot = Paths.get(storageRoot).toAbsolutePath().normalize().resolve("uploads");
    }

    @Override
    public String save(MultipartFile file) {
        Objects.requireNonNull(file, "file must not be null");

        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + "_" + originalFileName;
        Path target = uploadsRoot.resolve(storedFileName).normalize();

        try {
            Files.createDirectories(uploadsRoot);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }

        return target.toAbsolutePath().toString();
    }

    @Override
    public Path resolve(String storagePath) {
        Path resolvedPath = Paths.get(storagePath);
        if (resolvedPath.isAbsolute()) {
            return resolvedPath.normalize();
        }
        return uploadsRoot.resolve(resolvedPath).normalize();
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "upload.bin";
        }
        Path fileName = Paths.get(originalFileName).getFileName();
        return fileName == null ? "upload.bin" : fileName.toString();
    }
}
