package com.researchassistant.rag;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpVectorSearchPortTest {

    private final NoOpVectorSearchPort vectorSearchPort = new NoOpVectorSearchPort();

    @Test
    void searchReturnsNoResultsWhenVectorSearchIsDisabled() {
        List<RagChunk> result = vectorSearchPort.search("attention", List.of(1L), 5);

        assertThat(result).isEmpty();
    }

    @Test
    void reindexDocumentDoesNothingWhenVectorSearchIsDisabled() {
        vectorSearchPort.reindexDocument(1L, List.of(new RagChunk(11L, 1L, 0, "content", 0.5)));

        assertThat(vectorSearchPort.search("content", List.of(1L), 5)).isEmpty();
    }
}
