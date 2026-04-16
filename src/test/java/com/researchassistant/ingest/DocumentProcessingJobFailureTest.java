package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.rag.VectorSearchPort;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingJobFailureTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private PdfTextExtractor pdfTextExtractor;

    @Mock
    private OverlapTextChunker overlapTextChunker;

    @Mock
    private FileStoragePort fileStorage;

    @Mock
    private VectorSearchPort vectorSearchPort;

    @InjectMocks
    private DocumentProcessingJob documentProcessingJob;

    @Test
    void processDocumentMarksFailedWhenDocumentIsMissing() {
        long documentId = 42L;
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        CompletableFuture<Void> result = documentProcessingJob.processDocument(documentId);

        assertThatThrownBy(result::join)
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(documentRepository).updateStatus(
                eq(documentId),
                eq(DocumentStatus.FAILED),
                eq(FailureStage.PARSING),
                eq("Document not found: 42")
        );
    }
}
