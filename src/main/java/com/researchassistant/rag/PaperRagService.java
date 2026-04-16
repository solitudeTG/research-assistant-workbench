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

    private final KeywordSearchRepository keywordSearchRepository;
    private final VectorSearchPort vectorSearchPort;
    private final RetrievalTraceRepository retrievalTraceRepository;

    public PaperRagService(
            KeywordSearchRepository keywordSearchRepository,
            VectorSearchPort vectorSearchPort,
            RetrievalTraceRepository retrievalTraceRepository) {
        this.keywordSearchRepository = keywordSearchRepository;
        this.vectorSearchPort = vectorSearchPort;
        this.retrievalTraceRepository = retrievalTraceRepository;
    }

    public RagResult retrieve(long sessionId, String query, List<Long> allowedDocumentIds, int limit) {
        List<Long> normalizedAllowedDocumentIds = allowedDocumentIds == null ? List.of() : List.copyOf(allowedDocumentIds);
        List<RagChunk> keywordHits = keywordSearchRepository.search(query, normalizedAllowedDocumentIds, limit);
        List<RagChunk> vectorHits = vectorSearchPort.search(query, normalizedAllowedDocumentIds, limit);

        Map<Long, RagChunk> merged = new LinkedHashMap<>();
        mergeInto(merged, keywordHits, KEYWORD_WEIGHT);
        mergeInto(merged, vectorHits, VECTOR_WEIGHT);

        List<RagChunk> reranked = new ArrayList<>(merged.values());
        reranked.sort(Comparator.comparingDouble(RagChunk::finalScore).reversed());
        if (reranked.size() > limit) {
            reranked = new ArrayList<>(reranked.subList(0, limit));
        }

        retrievalTraceRepository.save(
                sessionId,
                query,
                Map.of("documentIds", normalizedAllowedDocumentIds),
                keywordHits,
                reranked
        );

        return new RagResult(query, normalizedAllowedDocumentIds, reranked);
    }

    private void mergeInto(Map<Long, RagChunk> merged, List<RagChunk> chunks, double weight) {
        for (RagChunk chunk : chunks) {
            RagChunk weightedChunk = new RagChunk(
                    chunk.chunkId(),
                    chunk.documentId(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.finalScore() * weight
            );
            merged.merge(
                    chunk.chunkId(),
                    weightedChunk,
                    (existing, incoming) -> new RagChunk(
                            existing.chunkId(),
                            existing.documentId(),
                            existing.chunkIndex(),
                            existing.content(),
                            existing.finalScore() + incoming.finalScore()
                    )
            );
        }
    }
}
