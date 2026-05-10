package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean({VectorStore.class, EmbeddingModel.class})
public class PgVectorSearchPort implements VectorSearchPort {

    private final VectorStore vectorStore;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PgVectorSearchPort(VectorStore vectorStore, NamedParameterJdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void reindexDocument(long documentId, List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(
                        chunk.content(),
                        Map.of(
                                "documentId", Long.toString(chunk.documentId()),
                                "chunkId", Long.toString(chunk.chunkId()),
                                "chunkIndex", Integer.toString(chunk.chunkIndex())
                        )
                ))
                .toList();
        vectorStore.add(documents);
    }

    @Override
    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(Math.max(limit * 3, limit))
                .build());
        if (documents == null) {
            return List.of();
        }

        List<RagChunk> chunks = documents.stream()
                .map(document -> new RagChunk(
                        Long.parseLong(document.getMetadata().get("chunkId").toString()),
                        Long.parseLong(document.getMetadata().get("documentId").toString()),
                        Integer.parseInt(document.getMetadata().get("chunkIndex").toString()),
                        document.getText(),
                        document.getScore() == null ? 0.0 : document.getScore()
                ))
                .filter(chunk -> allowedDocumentIds == null || allowedDocumentIds.isEmpty() || allowedDocumentIds.contains(chunk.documentId()))
                .collect(Collectors.toList());
        Map<Long, Double> feedbackScores = loadFeedbackScores(chunks);
        return chunks.stream()
                .map(chunk -> {
                    double feedbackScore = feedbackScores.getOrDefault(chunk.chunkId(), 0.0);
                    return new RagChunk(
                            chunk.chunkId(),
                            chunk.documentId(),
                            chunk.chunkIndex(),
                            chunk.content(),
                            RetrievalFeedbackScoring.finalScore(chunk.finalScore(), feedbackScore),
                            feedbackScore
                    );
                })
                .sorted(java.util.Comparator.comparingDouble(RagChunk::finalScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Map<Long, Double> loadFeedbackScores(List<RagChunk> chunks) {
        if (chunks.isEmpty()) {
            return Map.of();
        }
        List<Long> chunkIds = chunks.stream().map(RagChunk::chunkId).distinct().toList();
        return jdbcTemplate.query("""
                select id, feedback_score
                from document_chunk
                where id in (:chunkIds)
                """, Map.of("chunkIds", chunkIds), resultSet -> {
            java.util.Map<Long, Double> scores = new java.util.LinkedHashMap<>();
            while (resultSet.next()) {
                scores.put(resultSet.getLong("id"), resultSet.getDouble("feedback_score"));
            }
            return scores;
        });
    }
}
