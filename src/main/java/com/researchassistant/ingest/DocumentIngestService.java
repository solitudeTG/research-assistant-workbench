package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
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

        try {
            documentProcessingJob.processDocument(documentId);
        } catch (RuntimeException e) {
            markUploadSubmissionFailed(documentId, storagePath, e);
            return Map.of(
                    "documentId", documentId,
                    "status", DocumentStatus.FAILED.name(),
                    "title", originalFileName
            );
        }

        return Map.of(
                "documentId", documentId,
                "status", DocumentStatus.UPLOADED.name(),
                "title", originalFileName
        );
    }

    private void markUploadSubmissionFailed(long documentId, String storagePath, RuntimeException exception) {
        try {
            documentRepository.updateStatus(
                    documentId,
                    DocumentStatus.FAILED,
                    FailureStage.INDEXING,
                    failureMessage(exception)
            );
        } catch (RuntimeException statusFailure) {
            exception.addSuppressed(statusFailure);
        }

        try {
            fileStorage.delete(storagePath);
        } catch (RuntimeException deleteFailure) {
            exception.addSuppressed(deleteFailure);
        }
    }

    private String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
