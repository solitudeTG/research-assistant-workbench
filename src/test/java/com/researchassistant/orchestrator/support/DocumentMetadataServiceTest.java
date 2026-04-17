package com.researchassistant.orchestrator.support;

import com.researchassistant.ingest.DocumentRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocumentMetadataServiceTest {

    private final DocumentMetadataService documentMetadataService =
            new DocumentMetadataService(mock(DocumentRepository.class));

    @Test
    void treatsAbstractQuestionsAsOverviewQuestions() {
        assertThat(documentMetadataService.isOverviewQuestion("摘要的内容是什么"))
                .isTrue();
    }
}
