package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import com.researchassistant.ingest.model.DocumentAnalysis;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.ingest.model.ResearchDocument;
import com.researchassistant.ingest.model.SourceDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestService {
    private static final String DEFAULT_NOTE_TITLE = "Untitled note";
    private static final String NOTE_URI_PREFIX = "note:";

    private final FileStoragePort fileStorage;
    private final DocumentRepository documentRepository;
    private final DocumentProcessingJob documentProcessingJob;
    private final DocumentAnalysisService documentAnalysisService;
    private final WorkbenchEventPublisher eventPublisher;

    public DocumentIngestService(
            FileStoragePort fileStorage,
            DocumentRepository documentRepository,
            DocumentProcessingJob documentProcessingJob,
            DocumentAnalysisService documentAnalysisService,
            WorkbenchEventPublisher eventPublisher) {
        this.fileStorage = fileStorage;
        this.documentRepository = documentRepository;
        this.documentProcessingJob = documentProcessingJob;
        this.documentAnalysisService = documentAnalysisService;
        this.eventPublisher = eventPublisher;
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

    public Optional<ResearchDocument> findDocument(long documentId) {
        return documentRepository.findById(documentId);
    }

    public List<ResearchDocument> listDocuments() {
        return documentRepository.findAll();
    }

    public Optional<DocumentAnalysis> findDocumentAnalysis(long documentId) {
        return documentAnalysisService.findByDocumentId(documentId);
    }

    public SourceDocument importProjectFileSource(String projectId, MultipartFile file) {
        String storagePath = fileStorage.save(file);
        String originalFileName = FileStoragePort.normalizeOriginalFileName(file.getOriginalFilename());
        String type = originalFileName.toLowerCase().endsWith(".pdf") ? "pdf" : "note";
        SourceDocument source = documentRepository.insertSource(
                projectId,
                type,
                originalFileName,
                storagePath,
                status(DocumentStatus.UPLOADED)
        );
        publishSourceStatus(source);
        return runFileSourceChain(source);
    }

    public SourceDocument importProjectWebOrNoteSource(
            String projectId,
            String type,
            String title,
            String uri,
            String content) {
        String normalizedType = normalizeSourceType(type);
        String initialStatus = "web_page".equals(normalizedType)
                ? status(DocumentStatus.SUBMITTED)
                : status(DocumentStatus.UPLOADED);
        SourceDocument source = documentRepository.insertSource(
                projectId,
                normalizedType,
                sourceTitle(normalizedType, title, uri),
                sourceUri(normalizedType, uri, content),
                initialStatus
        );
        publishSourceStatus(source);
        if ("web_page".equals(normalizedType)) {
            return runWebSourceChain(source);
        }
        return runTextSourceChain(source, noteContentFrom(source.uri()));
    }

    public List<SourceDocument> listProjectSources(String projectId) {
        return documentRepository.listSources(projectId);
    }

    public Optional<SourceDocument> findProjectSource(String projectId, String sourceId) {
        return documentRepository.findSource(projectId, sourceId);
    }

    public Optional<SourceDocument> retryProjectSource(String projectId, String sourceId) {
        return findProjectSource(projectId, sourceId)
                .map(source -> {
                    if (!status(DocumentStatus.FAILED).equals(source.status())) {
                        return source;
                    }
                    if ("web_page".equals(source.type())) {
                        return runWebSourceChain(source);
                    }
                    if ("note".equals(source.type()) && isInlineNote(source.uri())) {
                        return runTextSourceChain(source, noteContentFrom(source.uri()));
                    }
                    return runFileSourceChain(source);
                });
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

    private SourceDocument runFileSourceChain(SourceDocument source) {
        FailureStage currentStage = FailureStage.PARSING;
        try {
            transition(source, DocumentStatus.PARSING, null, null);
            ensureStoredFileHasContent(source);
            currentStage = FailureStage.INDEXING;
            transition(source, DocumentStatus.INDEXING, null, null);
            currentStage = FailureStage.EXTRACTING;
            transition(source, DocumentStatus.EXTRACTING, null, null);
            transition(source, DocumentStatus.INDEXED, null, null);
            currentStage = FailureStage.DEPOSITING;
            transition(source, DocumentStatus.DEPOSITING, null, null);
            return transition(source, DocumentStatus.DEPOSITED, null, null);
        } catch (RuntimeException exception) {
            return markSourceFailed(source, currentStage, exception);
        }
    }

    private SourceDocument runTextSourceChain(SourceDocument source, String content) {
        FailureStage currentStage = FailureStage.PARSING;
        try {
            transition(source, DocumentStatus.PARSING, null, null);
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Source note content is empty");
            }
            currentStage = FailureStage.INDEXING;
            transition(source, DocumentStatus.INDEXING, null, null);
            currentStage = FailureStage.EXTRACTING;
            transition(source, DocumentStatus.EXTRACTING, null, null);
            transition(source, DocumentStatus.INDEXED, null, null);
            currentStage = FailureStage.DEPOSITING;
            transition(source, DocumentStatus.DEPOSITING, null, null);
            return transition(source, DocumentStatus.DEPOSITED, null, null);
        } catch (RuntimeException exception) {
            return markSourceFailed(source, currentStage, exception);
        }
    }

    private SourceDocument runWebSourceChain(SourceDocument source) {
        FailureStage currentStage = FailureStage.FETCHING;
        try {
            transition(source, DocumentStatus.FETCHING, null, null);
            if (source.uri() == null || source.uri().isBlank()) {
                throw new IllegalArgumentException("Source URI is required");
            }
            currentStage = FailureStage.EXTRACTING;
            transition(source, DocumentStatus.EXTRACTING, null, null);
            transition(source, DocumentStatus.INDEXED, null, null);
            currentStage = FailureStage.DEPOSITING;
            transition(source, DocumentStatus.DEPOSITING, null, null);
            return transition(source, DocumentStatus.DEPOSITED, null, null);
        } catch (RuntimeException exception) {
            return markSourceFailed(source, currentStage, exception);
        }
    }

    private SourceDocument transition(
            SourceDocument source,
            DocumentStatus status,
            FailureStage failureStage,
            String errorMessage) {
        documentRepository.updateSourceStatus(
                source.projectId(),
                source.id(),
                status(status),
                failureStage == null ? null : stage(failureStage),
                errorMessage
        );
        SourceDocument updated = documentRepository.findSource(source.projectId(), source.id())
                .orElseThrow(() -> new IllegalStateException("Source not found after status update: " + source.id()));
        publishSourceStatus(updated);
        return updated;
    }

    private SourceDocument markSourceFailed(SourceDocument source, FailureStage failureStage, RuntimeException exception) {
        return transition(source, DocumentStatus.FAILED, failureStage, failureMessage(exception));
    }

    private void publishSourceStatus(SourceDocument source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceId", source.id());
        payload.put("status", source.status());
        payload.put("failureStage", source.failureStage());
        payload.put("errorMessage", source.errorMessage());
        eventPublisher.publish(WorkbenchEvent.pendingForSource(
                WorkbenchEventType.SOURCE_STATUS_CHANGED,
                source.projectId(),
                source.id(),
                "ingestion-worker",
                payload
        ));
    }

    private void ensureStoredFileHasContent(SourceDocument source) {
        try {
            if (Files.size(fileStorage.resolve(source.uri())) == 0) {
                throw new IllegalArgumentException("Source file is empty");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private String normalizeSourceType(String type) {
        if (type == null || type.isBlank()) {
            throw new UnsupportedSourceTypeException(type);
        }
        String normalized = type.trim().toLowerCase();
        if ("web".equals(normalized) || "url".equals(normalized)) {
            return "web_page";
        }
        if (!"pdf".equals(normalized) && !"web_page".equals(normalized) && !"note".equals(normalized)) {
            throw new UnsupportedSourceTypeException(type);
        }
        return normalized;
    }

    private String sourceTitle(String type, String title, String uri) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if ("note".equals(type)) {
            return DEFAULT_NOTE_TITLE;
        }
        return uri;
    }

    private String sourceUri(String type, String uri, String content) {
        if ("note".equals(type)) {
            return NOTE_URI_PREFIX + (content == null ? "" : content);
        }
        return uri;
    }

    private String noteContentFrom(String uri) {
        if (uri == null) {
            return null;
        }
        if (isInlineNote(uri)) {
            return uri.substring(NOTE_URI_PREFIX.length());
        }
        return uri;
    }

    private boolean isInlineNote(String uri) {
        return uri != null && uri.startsWith(NOTE_URI_PREFIX);
    }

    private String status(DocumentStatus status) {
        return status.name().toLowerCase();
    }

    private String stage(FailureStage failureStage) {
        return failureStage.name().toLowerCase();
    }
}
