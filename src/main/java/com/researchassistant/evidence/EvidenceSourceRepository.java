package com.researchassistant.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.rag.RagChunk;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceSourceRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EvidenceSourceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<EvidenceSourceRecord> insertPaperSources(
            String projectId,
            String answerId,
            List<RagChunk> chunks,
            Map<Long, String> sourceIdByIndexedDocumentId) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .filter(chunk -> sourceIdByIndexedDocumentId != null
                        && sourceIdByIndexedDocumentId.containsKey(chunk.documentId()))
                .map(chunk -> insertPaperSource(
                        projectId,
                        answerId,
                        chunk,
                        sourceIdByIndexedDocumentId.get(chunk.documentId())))
                .toList();
    }

    public List<EvidenceSourceRecord> findByAnswer(String projectId, String answerId) {
        return jdbcTemplate.query("""
                select id, project_id, answer_id, source_type, source_id, snippet, strength,
                       relevance_score, feedback_score, citation_meta_json, created_at
                from evidence_source
                where project_id = ?
                  and answer_id = ?
                order by created_at, id
                """, (resultSet, rowNum) -> new EvidenceSourceRecord(
                resultSet.getString("id"),
                resultSet.getString("project_id"),
                resultSet.getString("answer_id"),
                resultSet.getString("source_type"),
                resultSet.getString("source_id"),
                resultSet.getString("snippet"),
                resultSet.getString("strength"),
                resultSet.getDouble("relevance_score"),
                resultSet.getDouble("feedback_score"),
                fromJsonMap(resultSet.getString("citation_meta_json")),
                resultSet.getObject("created_at", OffsetDateTime.class)
        ), projectId, answerId);
    }

    private EvidenceSourceRecord insertPaperSource(String projectId, String answerId, RagChunk chunk, String sourceId) {
        String evidenceId = UUID.randomUUID().toString();
        String snippet = snippet(chunk.content());
        String strength = strength(chunk.finalScore());
        Map<String, Object> citationMeta = new LinkedHashMap<>();
        citationMeta.put("documentId", chunk.documentId());
        citationMeta.put("chunkId", chunk.chunkId());
        citationMeta.put("chunkIndex", chunk.chunkIndex());

        return jdbcTemplate.queryForObject("""
                insert into evidence_source(
                    id, project_id, answer_id, source_type, source_id, quote, snippet,
                    strength, confidence, relevance_score, feedback_score, citation_meta_json
                )
                values (?, ?, ?, 'paper', ?, ?, ?, ?, ?, ?, 0, ?::jsonb)
                returning id, project_id, answer_id, source_type, source_id, snippet, strength,
                          relevance_score, feedback_score, citation_meta_json, created_at
                """, (resultSet, rowNum) -> new EvidenceSourceRecord(
                resultSet.getString("id"),
                resultSet.getString("project_id"),
                resultSet.getString("answer_id"),
                resultSet.getString("source_type"),
                resultSet.getString("source_id"),
                resultSet.getString("snippet"),
                resultSet.getString("strength"),
                resultSet.getDouble("relevance_score"),
                resultSet.getDouble("feedback_score"),
                fromJsonMap(resultSet.getString("citation_meta_json")),
                resultSet.getObject("created_at", OffsetDateTime.class)
        ), evidenceId, projectId, answerId, sourceId, snippet, snippet, strength, strength, chunk.finalScore(), toJson(citationMeta));
    }

    private String strength(double score) {
        if (score >= 0.75) {
            return "strong";
        }
        if (score >= 0.35) {
            return "medium";
        }
        return "weak";
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.strip();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize evidence source metadata", e);
        }
    }

    private Map<String, Object> fromJsonMap(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize evidence source metadata", e);
        }
    }
}
