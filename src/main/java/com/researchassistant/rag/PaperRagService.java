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
        List<Long> normalizedAllowedDocumentIds = allowedDocumentIds == null ? List.of() : List.copyOf(allowedDocumentIds);
        QueryRewritePlan rewritePlan = queryRewriteService.rewrite(query);
        List<RagChunk> keywordHits = new ArrayList<>();
        List<RagChunk> vectorHits = new ArrayList<>();
        List<RagChunk> metadataHits = new ArrayList<>();

        for (String rewrittenQuery : rewritePlan.retrievalQueries()) {
            keywordHits.addAll(keywordSearchRepository.search(rewrittenQuery, normalizedAllowedDocumentIds, limit));
            vectorHits.addAll(vectorSearchPort.search(rewrittenQuery, normalizedAllowedDocumentIds, limit));
            metadataHits.addAll(metadataSearchRepository.search(rewrittenQuery, normalizedAllowedDocumentIds, limit));
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

        retrievalTraceRepository.save(
                sessionId,
                query,
                Map.of(
                        "documentIds", normalizedAllowedDocumentIds,
                        "rewrittenQueries", rewritePlan.retrievalQueries(),
                        "keywords", rewritePlan.keywords()
                ),
                mergeForTrace(keywordHits, vectorHits, metadataHits),
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

    private List<RagChunk> mergeForTrace(List<RagChunk> keywordHits, List<RagChunk> vectorHits, List<RagChunk> metadataHits) {
        List<RagChunk> combined = new ArrayList<>(keywordHits.size() + vectorHits.size() + metadataHits.size());
        combined.addAll(keywordHits);
        combined.addAll(vectorHits);
        combined.addAll(metadataHits);
        return combined;
    }
}
