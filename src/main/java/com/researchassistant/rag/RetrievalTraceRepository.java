package com.researchassistant.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RetrievalTraceRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RetrievalTraceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(long sessionId, String query, Object filters, Object topChunks, Object rerankResult) {
        jdbcTemplate.update("""
                insert into retrieval_trace(session_id, query_text, filters_json, top_chunks_json, rerank_result_json)
                values (?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                """,
                sessionId,
                query,
                toJson(filters),
                toJson(topChunks),
                toJson(rerankResult)
        );
    }

    public List<RetrievalTraceView> findBySessionId(long sessionId) {
        return jdbcTemplate.query("""
                select id, session_id, query_text, filters_json, top_chunks_json, rerank_result_json, created_at
                from retrieval_trace
                where session_id = ?
                order by id desc
                """,
                (resultSet, rowNum) -> new RetrievalTraceView(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getString("query_text"),
                        fromJsonMap(resultSet.getString("filters_json")),
                        fromJsonObject(resultSet.getString("top_chunks_json")),
                        fromJsonObject(resultSet.getString("rerank_result_json")),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class)
                ),
                sessionId
        );
    }

    public List<RetrievalTraceView> findBySessionKey(String sessionKey) {
        return jdbcTemplate.query("""
                select t.id, t.session_id, t.query_text, t.filters_json, t.top_chunks_json, t.rerank_result_json, t.created_at
                from retrieval_trace t
                join chat_session s on s.id = t.session_id
                where s.session_key = ?
                order by t.id desc
                """,
                (resultSet, rowNum) -> new RetrievalTraceView(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getString("query_text"),
                        fromJsonMap(resultSet.getString("filters_json")),
                        fromJsonObject(resultSet.getString("top_chunks_json")),
                        fromJsonObject(resultSet.getString("rerank_result_json")),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class)
                ),
                sessionKey
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize retrieval trace", e);
        }
    }

    private Map<String, Object> fromJsonMap(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize retrieval trace map", e);
        }
    }

    private Object fromJsonObject(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize retrieval trace payload", e);
        }
    }
}
