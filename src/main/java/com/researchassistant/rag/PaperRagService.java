package com.researchassistant.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaperRagService {

    private static final double KEYWORD_WEIGHT = 0.45;
    private static final double VECTOR_WEIGHT = 0.55;
    private static final double METADATA_WEIGHT = 0.35;

    private final KeywordSearchRepository keywordSearchRepository;
    private final VectorSearchPort vectorSearchPort;
    private final RetrievalTraceRepository retrievalTraceRepository;
    private final QueryRewriteService queryRewriteService;
    private final MetadataSearchRepository metadataSearchRepository;

    public PaperRagService(
            KeywordSearchRepository keywordSearchRepository,
            VectorSearchPort vectorSearchPort,
            RetrievalTraceRepository retrievalTraceRepository,
            QueryRewriteService queryRewriteService,
            MetadataSearchRepository metadataSearchRepository) {
        this.keywordSearchRepository = keywordSearchRepository;
        this.vectorSearchPort = vectorSearchPort;
        this.retrievalTraceRepository = retrievalTraceRepository;
        this.queryRewriteService = queryRewriteService;
        this.metadataSearchRepository = metadataSearchRepository;
    }

    public RagResult retrieve(long sessionId, String query, List<Long> allowedDocumentIds, int limit) {
        return retrieve(sessionId, query, allowedDocumentIds, limit, RetrievalTraceContext.empty());
    }

    public RagResult retrieve(
            long sessionId,
            String query,
            List<Long> allowedDocumentIds,
            int limit,
            RetrievalTraceContext traceContext) {
        List<Long> normalizedAllowedDocumentIds = allowedDocumentIds == null ? List.of() : List.copyOf(allowedDocumentIds);
        QueryRewritePlan rewritePlan = queryRewriteService.rewrite(query);
        List<RagChunk> keywordHits = new ArrayList<>();
        List<RagChunk> vectorHits = new ArrayList<>();
        List<RagChunk> metadataHits = new ArrayList<>();
        int queryCount = rewritePlan.retrievalQueries().size();
        long keywordDurationMs = 0L;
        long vectorDurationMs = 0L;
        long metadataDurationMs = 0L;
        int vectorPreScopeHits = 0;
        int vectorPostScopeHits = 0;

        for (String rewrittenQuery : rewritePlan.retrievalQueries()) {
            long startedAt = System.nanoTime();
            keywordHits.addAll(keywordSearchRepository.search(rewrittenQuery, normalizedAllowedDocumentIds, limit));
            keywordDurationMs += elapsedMs(startedAt);

            startedAt = System.nanoTime();
            RetrievalSearchResult vectorResult = vectorSearchPort.searchWithStats(rewrittenQuery, normalizedAllowedDocumentIds, limit);
            vectorHits.addAll(vectorResult.chunks());
            vectorPreScopeHits += vectorResult.preScopeHits();
            vectorPostScopeHits += vectorResult.postScopeHits();
            vectorDurationMs += elapsedMs(startedAt);

            startedAt = System.nanoTime();
            metadataHits.addAll(metadataSearchRepository.search(rewrittenQuery, normalizedAllowedDocumentIds, limit));
            metadataDurationMs += elapsedMs(startedAt);
        }

        Map<Long, RagChunk> merged = new LinkedHashMap<>();
        mergeInto(merged, keywordHits, KEYWORD_WEIGHT);
        mergeInto(merged, vectorHits, VECTOR_WEIGHT);
        mergeInto(merged, metadataHits, METADATA_WEIGHT);

        List<RagChunk> reranked = new ArrayList<>(merged.values());
        reranked.sort(Comparator.comparingDouble(RagChunk::finalScore).reversed());
        if (reranked.size() > limit) {
            reranked = new ArrayList<>(reranked.subList(0, limit));
        }

        Map<String, RetrievalBackendStats> backendStats = new LinkedHashMap<>();
        backendStats.put("keyword", RetrievalBackendStats.of(queryCount, keywordHits.size(), keywordDurationMs));
        backendStats.put("vector", new RetrievalBackendStats(
                queryCount,
                vectorPreScopeHits,
                vectorPostScopeHits,
                vectorDurationMs
        ));
        backendStats.put("metadata", RetrievalBackendStats.of(queryCount, metadataHits.size(), metadataDurationMs));
        RetrievalObservation observation = RetrievalObservation.builder(query, normalizedAllowedDocumentIds, limit)
                .rewritePlan(rewritePlan)
                .backendStats(backendStats)
                .mergedCandidateCount(merged.size())
                .rerankedChunkCount(reranked.size())
                .returnedScopedChunkCount(reranked.size())
                .zeroHitReason(classifyZeroHit(
                        keywordHits,
                        vectorHits,
                        metadataHits,
                        merged,
                        reranked,
                        query,
                        vectorPreScopeHits,
                        vectorPostScopeHits
                ))
                .build();

        Map<String, Object> traceFilters = new LinkedHashMap<>();
        traceFilters.put("documentIds", normalizedAllowedDocumentIds);
        traceFilters.put("rewrittenQueries", rewritePlan.retrievalQueries());
        traceFilters.put("keywords", rewritePlan.keywords());
        traceFilters.put("rewriteStrategy", rewritePlan.strategy());
        if (traceContext != null) {
            traceFilters.putAll(traceContext.toMetadata());
        }

        retrievalTraceRepository.save(
                sessionId,
                query,
                traceFilters,
                mergeForTrace(keywordHits, vectorHits, metadataHits),
                Map.of(
                        "chunks", reranked,
                        "observation", observation.summary()
                )
        );

        return new RagResult(query, normalizedAllowedDocumentIds, reranked, observation);
    }

    private ZeroHitReason classifyZeroHit(
            List<RagChunk> keywordHits,
            List<RagChunk> vectorHits,
            List<RagChunk> metadataHits,
            Map<Long, RagChunk> merged,
            List<RagChunk> reranked,
            String query,
            int vectorPreScopeHits,
            int vectorPostScopeHits) {
        if (query == null || query.isBlank()) {
            return ZeroHitReason.QUERY_EMPTY_OR_INVALID;
        }
        if (!reranked.isEmpty()) {
            return null;
        }
        if (vectorPreScopeHits > vectorPostScopeHits && vectorPostScopeHits == 0) {
            return ZeroHitReason.SCOPE_FILTERED_EMPTY;
        }
        if (keywordHits.isEmpty() && vectorHits.isEmpty() && metadataHits.isEmpty()) {
            return ZeroHitReason.NO_BACKEND_HITS;
        }
        if (merged.isEmpty()) {
            return ZeroHitReason.SCOPE_FILTERED_EMPTY;
        }
        return ZeroHitReason.RERANK_EMPTY;
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void mergeInto(Map<Long, RagChunk> merged, List<RagChunk> chunks, double weight) {
        for (RagChunk chunk : chunks) {
            RagChunk weightedChunk = new RagChunk(
                    chunk.chunkId(),
                    chunk.documentId(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.finalScore() * weight,
                    chunk.feedbackScore()
            );
            merged.merge(
                    chunk.chunkId(),
                    weightedChunk,
                    (existing, incoming) -> new RagChunk(
                            existing.chunkId(),
                            existing.documentId(),
                            existing.chunkIndex(),
                            existing.content(),
                            existing.finalScore() + incoming.finalScore(),
                            feedbackScoreForMergedChunk(existing, incoming)
                    )
            );
        }
    }

    private double feedbackScoreForMergedChunk(RagChunk existing, RagChunk incoming) {
        if (existing.feedbackScore() != 0.0) {
            return existing.feedbackScore();
        }
        return incoming.feedbackScore();
    }

    private List<RagChunk> mergeForTrace(List<RagChunk> keywordHits, List<RagChunk> vectorHits, List<RagChunk> metadataHits) {
        List<RagChunk> combined = new ArrayList<>(keywordHits.size() + vectorHits.size() + metadataHits.size());
        combined.addAll(keywordHits);
        combined.addAll(vectorHits);
        combined.addAll(metadataHits);
        return combined;
    }
}
