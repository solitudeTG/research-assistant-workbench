package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
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

    @Mock
    private QueryRewriteService queryRewriteService;

    @Mock
    private MetadataSearchRepository metadataSearchRepository;

    @InjectMocks
    private PaperRagService paperRagService;

    @Test
    void hybridRetrievalMergesKeywordAndVectorSignals() {
        String query = "How does attention help sequence modeling?";
        List<Long> allowedDocumentIds = List.of(1L);
        QueryRewritePlan rewritePlan = QueryRewritePlan.from(
                query,
                "How does attention help sequence modeling?",
                List.of("attention", "sequence modeling")
        );

        RagChunk keywordHit = new RagChunk(11L, 1L, 0, "Attention improves sequence modeling", 0.75);
        RagChunk vectorHit = new RagChunk(11L, 1L, 0, "Attention improves sequence modeling", 0.95);

        when(queryRewriteService.rewrite(query)).thenReturn(rewritePlan);
        when(keywordSearchRepository.search(query, allowedDocumentIds, 5)).thenReturn(List.of(keywordHit));
        when(vectorSearchPort.search(query, allowedDocumentIds, 5)).thenReturn(List.of(vectorHit));
        when(metadataSearchRepository.search(query, allowedDocumentIds, 5)).thenReturn(List.of());

        RagResult result = paperRagService.retrieve(42L, query, allowedDocumentIds, 5);

        assertThat(result.query()).isEqualTo(query);
        assertThat(result.allowedDocumentIds()).containsExactlyElementsOf(allowedDocumentIds);
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).chunkId()).isEqualTo(11L);
        assertThat(result.chunks().get(0).finalScore()).isGreaterThan(0.80);

        verify(retrievalTraceRepository).save(eq(42L), eq(query), any(), any(), any());
    }

    @Test
    void retrievalUsesRewrittenQueriesAndMetadataHitsForCrossLanguageQuestion() {
        String query = "这篇论文研究了什么？";
        List<Long> allowedDocumentIds = List.of(7L);
        QueryRewritePlan rewritePlan = QueryRewritePlan.from(
                query,
                "What problem does this paper study?",
                List.of("satellite selection", "beamforming")
        );

        RagChunk englishKeywordHit = new RagChunk(21L, 7L, 1, "This paper studies satellite selection and beamforming.", 0.9);
        RagChunk metadataHit = new RagChunk(-7L, 7L, -1, "Satellite Selection with Deep Learning", 0.8);

        when(queryRewriteService.rewrite(query)).thenReturn(rewritePlan);
        when(keywordSearchRepository.search("这篇论文研究了什么？", allowedDocumentIds, 5)).thenReturn(List.of());
        when(keywordSearchRepository.search("What problem does this paper study?", allowedDocumentIds, 5))
                .thenReturn(List.of(englishKeywordHit));
        when(keywordSearchRepository.search("satellite selection beamforming", allowedDocumentIds, 5))
                .thenReturn(List.of());
        when(vectorSearchPort.search("这篇论文研究了什么？", allowedDocumentIds, 5)).thenReturn(List.of());
        when(vectorSearchPort.search("What problem does this paper study?", allowedDocumentIds, 5)).thenReturn(List.of());
        when(vectorSearchPort.search("satellite selection beamforming", allowedDocumentIds, 5)).thenReturn(List.of());
        when(metadataSearchRepository.search("这篇论文研究了什么？", allowedDocumentIds, 5)).thenReturn(List.of());
        when(metadataSearchRepository.search("What problem does this paper study?", allowedDocumentIds, 5)).thenReturn(List.of());
        when(metadataSearchRepository.search("satellite selection beamforming", allowedDocumentIds, 5))
                .thenReturn(List.of(metadataHit));

        RagResult result = paperRagService.retrieve(8L, query, allowedDocumentIds, 5);

        assertThat(result.chunks()).extracting(RagChunk::chunkId).contains(21L, -7L);
        verify(retrievalTraceRepository).save(eq(8L), eq(query), eq(Map.of(
                "documentIds", allowedDocumentIds,
                "rewrittenQueries", rewritePlan.retrievalQueries(),
                "keywords", rewritePlan.keywords(),
                "rewriteStrategy", rewritePlan.strategy()
        )), any(), any());
    }

    @Test
    void observationClassifiesNoBackendHits() {
        String query = "nonexistent technique";
        List<Long> allowedDocumentIds = List.of(7L);
        QueryRewritePlan rewritePlan = QueryRewritePlan.originalOnly(query);

        when(queryRewriteService.rewrite(query)).thenReturn(rewritePlan);
        when(keywordSearchRepository.search(query, allowedDocumentIds, 5)).thenReturn(List.of());
        when(vectorSearchPort.search(query, allowedDocumentIds, 5)).thenReturn(List.of());
        when(metadataSearchRepository.search(query, allowedDocumentIds, 5)).thenReturn(List.of());

        RagResult result = paperRagService.retrieve(8L, query, allowedDocumentIds, 5);

        assertThat(result.chunks()).isEmpty();
        assertThat(result.observation().zeroHitReason()).isEqualTo(ZeroHitReason.NO_BACKEND_HITS);
        assertThat(result.observation().backendStats().get("keyword").postScopeHits()).isZero();
        assertThat(result.observation().returnedScopedChunkCount()).isZero();
    }

    @Test
    void feedbackScoreAdjustmentIsExplainableAndBounded() {
        assertThat(RetrievalFeedbackScoring.finalScore(0.50, 2.0)).isEqualTo(0.60);
        assertThat(RetrievalFeedbackScoring.finalScore(0.50, 99.0)).isEqualTo(0.70);
        assertThat(RetrievalFeedbackScoring.finalScore(0.50, -99.0)).isEqualTo(0.30);
    }
}
