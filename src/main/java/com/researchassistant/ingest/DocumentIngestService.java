package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import com.researchassistant.ingest.model.DocumentStatus;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestService {

    private final FileStoragePort fileStorage;
    private final DocumentRepository documentRepository;

    public DocumentIngestService(FileStoragePort fileStorage, DocumentRepository documentRepository) {
        this.fileStorage = fileStorage;
        this.documentRepository = documentRepository;
    }

    public Map<String, Object> registerUpload(MultipartFile file) {
        String originalFileName = normalizeOriginalFileName(file);
        String storagePath = fileStorage.save(file);
        long documentId = documentRepository.insert(originalFileName, originalFileName, storagePath);

        return Map.of(
                "documentId", documentId,
                "status", DocumentStatus.UPLOADED.name(),
                "title", originalFileName
        );
    }

    private String normalizeOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            return "upload.bin";
        }
        return originalFileName;
    }
}
