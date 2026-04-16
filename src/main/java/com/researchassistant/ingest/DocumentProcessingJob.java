package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.ingest.model.ResearchDocument;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.VectorSearchPort;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingJob {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final OverlapTextChunker overlapTextChunker;
    private final FileStoragePort fileStorage;
    private final VectorSearchPort vectorSearchPort;

    public DocumentProcessingJob(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            PdfTextExtractor pdfTextExtractor,
            OverlapTextChunker overlapTextChunker,
            FileStoragePort fileStorage,
            VectorSearchPort vectorSearchPort) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.overlapTextChunker = overlapTextChunker;
        this.fileStorage = fileStorage;
        this.vectorSearchPort = vectorSearchPort;
    }

    @Async("indexingExecutor")
    public CompletableFuture<Void> processDocument(long documentId) {
        FailureStage failureStage = FailureStage.PARSING;
        try {
            ResearchDocument document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            documentRepository.updateStatus(documentId, DocumentStatus.PARSING, null, null);

            Path filePath = fileStorage.resolve(document.storagePath());
            String extractedText = pdfTextExtractor.extract(filePath);
            List<String> chunks = overlapTextChunker.chunk(extractedText);

            failureStage = FailureStage.INDEXING;
            documentRepository.updateStatus(documentId, DocumentStatus.INDEXING, null, null);
            documentChunkRepository.replaceChunks(documentId, chunks);
            vectorSearchPort.reindexDocument(documentId, toRagChunks(documentId));
            documentRepository.updateStatus(documentId, DocumentStatus.INDEXED, null, null);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            markFailed(documentId, failureStage, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private void markFailed(long documentId, FailureStage failureStage, Exception exception) {
        try {
            documentRepository.updateStatus(documentId, DocumentStatus.FAILED, failureStage, failureMessage(exception));
        } catch (RuntimeException statusFailure) {
            exception.addSuppressed(statusFailure);
        }
    }

    private String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private List<RagChunk> toRagChunks(long documentId) {
        return documentChunkRepository.findByDocumentId(documentId).stream()
                .map(row -> new RagChunk(row.id(), row.documentId(), row.chunkIndex(), row.content(), 0.0))
                .toList();
    }
}
