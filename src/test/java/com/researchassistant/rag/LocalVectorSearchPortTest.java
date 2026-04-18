package com.researchassistant.rag;

import com.researchassistant.common.config.DeterministicLocalEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalVectorSearchPortTest {

    private final LocalVectorSearchPort vectorSearchPort =
            new LocalVectorSearchPort(new DeterministicLocalEmbeddingModel());

    @Test
    void searchReturnsRelevantResultsAfterReindex() {
        vectorSearchPort.reindexDocument(1L, List.of(
                new RagChunk(11L, 1L, 0, "Joint beamforming optimization for LEO satellite communication.", 0.0),
                new RagChunk(12L, 1L, 1, "A retrieval pipeline for agent memory orchestration.", 0.0)
        ));

        List<RagChunk> result = vectorSearchPort.search("beamforming and satellite selection", List.of(1L), 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunkId()).isEqualTo(11L);
        assertThat(result.get(0).finalScore()).isPositive();
    }

    @Test
    void searchOnlyReturnsChunksFromAllowedDocuments() {
        vectorSearchPort.reindexDocument(1L, List.of(
                new RagChunk(11L, 1L, 0, "Satellite selection with deep learning.", 0.0)
        ));
        vectorSearchPort.reindexDocument(2L, List.of(
                new RagChunk(21L, 2L, 0, "Diffusion transformers for video generation.", 0.0)
        ));

        List<RagChunk> result = vectorSearchPort.search("video generation", List.of(1L), 5);

        assertThat(result).extracting(RagChunk::documentId).containsOnly(1L);
        assertThat(result).extracting(RagChunk::chunkId).doesNotContain(21L);
    }
}
