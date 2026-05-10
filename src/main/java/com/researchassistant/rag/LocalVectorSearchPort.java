package com.researchassistant.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(VectorStore.class)
public class LocalVectorSearchPort implements VectorSearchPort {

    private final EmbeddingModel embeddingModel;
    private final Map<Long, IndexedChunk> index = new ConcurrentHashMap<>();

    public LocalVectorSearchPort(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void reindexDocument(long documentId, List<RagChunk> chunks) {
        index.entrySet().removeIf(entry -> entry.getValue().chunk.documentId() == documentId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        for (RagChunk chunk : chunks) {
            float[] vector = embeddingModel.embed(chunk.content());
            index.put(chunk.chunkId(), new IndexedChunk(chunk, vector));
        }
    }

    @Override
    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        if (query == null || query.isBlank() || index.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingModel.embed(query);
        return index.values().stream()
                .filter(candidate -> allowedDocumentIds == null
                        || allowedDocumentIds.isEmpty()
                        || allowedDocumentIds.contains(candidate.chunk.documentId()))
                .map(candidate -> new RagChunk(
                        candidate.chunk.chunkId(),
                        candidate.chunk.documentId(),
                        candidate.chunk.chunkIndex(),
                        candidate.chunk.content(),
                        RetrievalFeedbackScoring.finalScore(
                                cosine(queryVector, candidate.vector),
                                candidate.chunk.feedbackScore()),
                        candidate.chunk.feedbackScore()))
                .filter(chunk -> chunk.finalScore() > 0.0)
                .sorted(Comparator.comparingDouble(RagChunk::finalScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void applyChunkFeedback(List<Long> chunkIds, double delta) {
        if (chunkIds == null || chunkIds.isEmpty() || delta == 0.0) {
            return;
        }
        for (Long chunkId : chunkIds) {
            if (chunkId == null) {
                continue;
            }
            index.computeIfPresent(chunkId, (ignored, indexedChunk) -> {
                RagChunk chunk = indexedChunk.chunk();
                RagChunk updatedChunk = new RagChunk(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        chunk.finalScore(),
                        chunk.feedbackScore() + delta
                );
                return new IndexedChunk(updatedChunk, indexedChunk.vector());
            });
        }
    }

    private double cosine(float[] left, float[] right) {
        double score = 0.0;
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            score += left[i] * right[i];
        }
        return score;
    }

    private record IndexedChunk(RagChunk chunk, float[] vector) {
    }
}
