package com.researchassistant.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public List<EvidenceSourceRecord> insertWebSources(
            String projectId,
            String answerId,
            WebSearchResult webSearchResult) {
        if (webSearchResult == null
                || webSearchResult.degraded()
                || webSearchResult.hits() == null
                || webSearchResult.hits().isEmpty()) {
            return List.of();
        }
        List<EvidenceSourceRecord> records = new ArrayList<>();
        int rank = 1;
        for (WebSearchHit hit : webSearchResult.hits()) {
            if (hit != null) {
                records.add(insertWebSource(projectId, answerId, webSearchResult, hit, rank));
                rank++;
            }
        }
        return List.copyOf(records);
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

    public FeedbackApplication applyFeedbackToProjectEvidence(
            String projectId,
            String answerId,
            List<String> evidenceSourceIds,
            int delta) {
        List<String> safeEvidenceSourceIds = distinct(evidenceSourceIds);
        if (safeEvidenceSourceIds.isEmpty()) {
            return new FeedbackApplication(0, 0, List.of(), List.of());
        }

        String placeholders = placeholders(safeEvidenceSourceIds.size());
        List<Object> selectedParams = new ArrayList<>();
        selectedParams.add(projectId);
        selectedParams.add(answerId);
        selectedParams.addAll(safeEvidenceSourceIds);
        List<String> appliedEvidenceSourceIds = jdbcTemplate.queryForList("""
                select id
                from evidence_source
                where project_id = ?
                  and answer_id = ?
                  and id in (%s)
                """.formatted(placeholders), String.class, selectedParams.toArray());

        if (appliedEvidenceSourceIds.isEmpty()) {
            return new FeedbackApplication(0, 0, List.of(), List.of());
        }

        String appliedPlaceholders = placeholders(appliedEvidenceSourceIds.size());
        List<Object> evidenceParams = new ArrayList<>();
        evidenceParams.add((double) delta);
        evidenceParams.add(projectId);
        evidenceParams.add(answerId);
        evidenceParams.addAll(appliedEvidenceSourceIds);

        int evidenceCount = jdbcTemplate.update("""
                update evidence_source
                set feedback_score = feedback_score + ?
                where project_id = ?
                  and answer_id = ?
                  and id in (%s)
                """.formatted(appliedPlaceholders), evidenceParams.toArray());

        List<Object> chunkParams = new ArrayList<>();
        chunkParams.add(projectId);
        chunkParams.add(answerId);
        chunkParams.addAll(appliedEvidenceSourceIds);
        List<Long> chunkIds = jdbcTemplate.queryForList("""
                with selected_evidence as (
                    select es.project_id,
                           es.source_id,
                           es.source_type,
                           es.citation_meta_json ->> 'chunkId' as chunk_id_text
                    from evidence_source es
                    where es.project_id = ?
                      and es.answer_id = ?
                      and es.id in (%s)
                ),
                parsed_evidence as (
                    select project_id,
                           source_id,
                           source_type,
                           case
                               when chunk_id_text ~ '^[0-9]+$'
                                and (
                                    length(chunk_id_text) < 19
                                    or (
                                        length(chunk_id_text) = 19
                                        and chunk_id_text <= '9223372036854775807'
                                    )
                                )
                               then chunk_id_text::bigint
                               else null
                           end as chunk_id
                    from selected_evidence
                )
                select distinct dc.id
                from parsed_evidence es
                join source_document sd
                  on sd.project_id = es.project_id
                 and sd.id = es.source_id
                join document_chunk dc
                  on dc.document_id = sd.indexed_document_id
                 and dc.id = es.chunk_id
                where es.source_type = 'paper'
                  and es.chunk_id is not null
                """.formatted(appliedPlaceholders), Long.class, chunkParams.toArray());

        int chunkCount = 0;
        if (!chunkIds.isEmpty()) {
            List<Object> updateChunkParams = new ArrayList<>();
            updateChunkParams.add((double) delta);
            updateChunkParams.addAll(chunkIds);
            chunkCount = jdbcTemplate.update("""
                    update document_chunk
                    set feedback_score = feedback_score + ?
                    where id in (%s)
                    """.formatted(placeholders(chunkIds.size())), updateChunkParams.toArray());
        }

        return new FeedbackApplication(evidenceCount, chunkCount, appliedEvidenceSourceIds, chunkIds);
    }

    public record FeedbackApplication(
            int updatedEvidenceSourceCount,
            int updatedChunkCount,
            List<String> appliedEvidenceSourceIds,
            List<Long> appliedChunkIds) {
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

    private EvidenceSourceRecord insertWebSource(
            String projectId,
            String answerId,
            WebSearchResult webSearchResult,
            WebSearchHit hit,
            int rank) {
        String evidenceId = UUID.randomUUID().toString();
        String snippet = snippet(hit.snippet());
        String strength = strength(hit.score());
        Map<String, Object> citationMeta = new LinkedHashMap<>();
        citationMeta.put("title", safe(hit.title()));
        citationMeta.put("url", safe(hit.url()));
        citationMeta.put("provider", safe(webSearchResult.provider()));
        citationMeta.put("snippet", snippet);
        citationMeta.put("query", safe(webSearchResult.query()));
        citationMeta.put("rank", rank);

        return jdbcTemplate.queryForObject("""
                insert into evidence_source(
                    id, project_id, answer_id, source_type, source_id, quote, snippet,
                    strength, confidence, relevance_score, feedback_score, citation_meta_json
                )
                values (?, ?, ?, 'web', null, ?, ?, ?, ?, ?, 0, ?::jsonb)
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
        ), evidenceId, projectId, answerId, snippet, snippet, strength, strength, hit.score(), toJson(citationMeta));
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> distinctValues = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                distinctValues.add(value);
            }
        }
        return List.copyOf(distinctValues);
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
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
