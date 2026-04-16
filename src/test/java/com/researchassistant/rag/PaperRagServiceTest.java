package com.researchassistant.rag;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperRagServiceTest {

    @Mock
    private KeywordSearchRepository keywordSearchRepository;

    @Mock
    private VectorSearchPort vectorSearchPort;

    @Mock
    private RetrievalTraceRepository retrievalTraceRepository;

    @InjectMocks
    private PaperRagService paperRagService;

    @Test
    void hybridRetrievalMergesKeywordAndVectorSignals() {
        String query = "How does attention help sequence modeling?";
        List<Long> allowedDocumentIds = List.of(1L);

        RagChunk keywordHit = new RagChunk(11L, 1L, 0, "Attention improves sequence modeling", 0.75);
        RagChunk vectorHit = new RagChunk(11L, 1L, 0, "Attention improves sequence modeling", 0.95);

        when(keywordSearchRepository.search(query, allowedDocumentIds, 5)).thenReturn(List.of(keywordHit));
        when(vectorSearchPort.search(query, allowedDocumentIds, 5)).thenReturn(List.of(vectorHit));

        RagResult result = paperRagService.retrieve(42L, query, allowedDocumentIds, 5);

        assertThat(result.query()).isEqualTo(query);
        assertThat(result.allowedDocumentIds()).containsExactlyElementsOf(allowedDocumentIds);
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).chunkId()).isEqualTo(11L);
        assertThat(result.chunks().get(0).finalScore()).isGreaterThan(0.80);

        verify(retrievalTraceRepository).save(eq(42L), eq(query), any(), any(), any());
    }
}
