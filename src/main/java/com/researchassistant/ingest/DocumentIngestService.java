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
    private final DocumentProcessingJob documentProcessingJob;

    public DocumentIngestService(
            FileStoragePort fileStorage,
            DocumentRepository documentRepository,
            DocumentProcessingJob documentProcessingJob) {
        this.fileStorage = fileStorage;
        this.documentRepository = documentRepository;
        this.documentProcessingJob = documentProcessingJob;
    }

    public Map<String, Object> registerUpload(MultipartFile file) {
        String storagePath = fileStorage.save(file);
        String originalFileName = FileStoragePort.normalizeOriginalFileName(file.getOriginalFilename());
        long documentId;
        try {
            documentId = documentRepository.insert(originalFileName, originalFileName, storagePath);
        } catch (RuntimeException e) {
            try {
                fileStorage.delete(storagePath);
            } catch (RuntimeException deleteFailure) {
                e.addSuppressed(deleteFailure);
            }
            throw e;
        }

        documentProcessingJob.processDocument(documentId);

        return Map.of(
                "documentId", documentId,
                "status", DocumentStatus.UPLOADED.name(),
                "title", originalFileName
        );
    }
}
