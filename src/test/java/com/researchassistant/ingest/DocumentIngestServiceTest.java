package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.ingest.model.SourceDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestServiceTest {

    @Mock
    private FileStoragePort fileStorage;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentProcessingJob documentProcessingJob;

    @Mock
    private DocumentAnalysisService documentAnalysisService;

    @Mock
    private WorkbenchEventPublisher eventPublisher;

    @InjectMocks
    private DocumentIngestService documentIngestService;

    @Test
    void registerUploadMarksFailedWhenAsyncSubmissionIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "paper.txt",
                "text/plain",
                "research notes".getBytes()
        );

        when(fileStorage.save(file)).thenReturn("target/test-storage/uploads/abc_paper.txt");
        when(documentRepository.insert("paper.txt", "paper.txt", "target/test-storage/uploads/abc_paper.txt"))
                .thenReturn(99L);
        when(documentProcessingJob.processDocument(99L))
                .thenThrow(new TaskRejectedException("executor saturated"));

        Map<String, Object> response = documentIngestService.registerUpload(file);

        assertThat(response).containsEntry("documentId", 99L);
        assertThat(response).containsEntry("status", DocumentStatus.FAILED.name());
        assertThat(response).containsEntry("title", "paper.txt");

        verify(documentRepository).updateStatus(
                eq(99L),
                eq(DocumentStatus.FAILED),
                eq(FailureStage.INDEXING),
                eq("executor saturated")
        );
        verify(fileStorage).delete("target/test-storage/uploads/abc_paper.txt");
    }

    @Test
    void projectFileSourceFailureDuringIndexingRecordsIndexingStage() throws Exception {
        assertProjectFileFailureStage("indexing", FailureStage.INDEXING);
    }

    @Test
    void projectFileSourceFailureDuringExtractingRecordsExtractingStage() throws Exception {
        assertProjectFileFailureStage("extracting", FailureStage.EXTRACTING);
    }

    @Test
    void projectFileSourceFailureDuringDepositingRecordsDepositingStage() throws Exception {
        assertProjectFileFailureStage("depositing", FailureStage.DEPOSITING);
    }

    @Test
    void retryFailedNoteUsesStoredNoteContentWithoutFileStorage() {
        SourceDocument failedNote = new SourceDocument(
                "source-1",
                "project-1",
                "note",
                "Untitled note",
                "note:Recovered note body",
                "failed",
                "parsing",
                "first attempt failed",
                0,
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now()
        );
        when(documentRepository.findSource("project-1", "source-1")).thenReturn(Optional.of(failedNote));

        documentIngestService.retryProjectSource("project-1", "source-1");

        verify(fileStorage, never()).resolve(org.mockito.ArgumentMatchers.any());
        verify(documentRepository).updateSourceStatus("project-1", "source-1", "parsing", null, null);
        verify(documentRepository).updateSourceStatus("project-1", "source-1", "deposited", null, null);
    }

    @Test
    void retryFailedMultipartNoteUsesStoredFilePipeline() throws Exception {
        Path sourcePath = Files.createTempFile("retry-file-note-", ".txt");
        String storagePath = sourcePath.toString();
        SourceDocument failedNote = new SourceDocument(
                "source-2",
                "project-1",
                "note",
                "notes.txt",
                storagePath,
                "failed",
                "parsing",
                "first attempt failed",
                0,
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now()
        );
        when(documentRepository.findSource("project-1", "source-2")).thenReturn(Optional.of(failedNote));
        when(fileStorage.resolve(storagePath)).thenReturn(sourcePath);

        documentIngestService.retryProjectSource("project-1", "source-2");

        verify(fileStorage).resolve(storagePath);
        verify(documentRepository).updateSourceStatus(
                "project-1",
                "source-2",
                "failed",
                "parsing",
                "Source file is empty"
        );
        verify(documentRepository, never()).updateSourceStatus("project-1", "source-2", "deposited", null, null);
    }

    private void assertProjectFileFailureStage(String failingStatus, FailureStage expectedStage) throws Exception {
        Path sourcePath = Files.createTempFile("source-stage-", ".txt");
        Files.writeString(sourcePath, "content");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "content".getBytes()
        );
        SourceDocument source = new SourceDocument(
                "source-1",
                "project-1",
                "note",
                "notes.txt",
                sourcePath.toString(),
                "uploaded",
                null,
                null,
                0,
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now()
        );

        when(fileStorage.save(file)).thenReturn(sourcePath.toString());
        when(fileStorage.resolve(sourcePath.toString())).thenReturn(sourcePath);
        when(documentRepository.insertSource("project-1", "note", "notes.txt", sourcePath.toString(), "uploaded"))
                .thenReturn(source);
        when(documentRepository.findSource("project-1", "source-1")).thenReturn(Optional.of(source));
        doAnswer(invocation -> {
            String status = invocation.getArgument(2);
            if (failingStatus.equals(status)) {
                throw new IllegalStateException("boom at " + failingStatus);
            }
            return null;
        }).when(documentRepository).updateSourceStatus(
                eq("project-1"),
                eq("source-1"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        documentIngestService.importProjectFileSource("project-1", file);

        verify(documentRepository).updateSourceStatus(
                "project-1",
                "source-1",
                "failed",
                expectedStage.name().toLowerCase(),
                "boom at " + failingStatus
        );
    }
}
