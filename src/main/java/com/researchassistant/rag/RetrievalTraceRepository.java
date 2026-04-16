package com.researchassistant.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RetrievalTraceRepository {

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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize retrieval trace", e);
        }
    }
}
