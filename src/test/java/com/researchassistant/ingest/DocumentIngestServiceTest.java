package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
}
