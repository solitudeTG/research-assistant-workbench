package com.researchassistant.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.ingest.DocumentChunkRepository;
import com.researchassistant.orchestrator.FeedbackPort;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService implements FeedbackPort {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentChunkRepository documentChunkRepository;
    private final ObjectMapper objectMapper;

    public FeedbackService(JdbcTemplate jdbcTemplate,
                           DocumentChunkRepository documentChunkRepository,
                           ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentChunkRepository = documentChunkRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recordMessageFeedback(long messageId, List<Long> chunkIds, int score, String note) {
        List<Long> safeChunkIds = chunkIds == null ? List.of() : chunkIds;
        jdbcTemplate.update("""
                insert into message_feedback(message_id, feedback_score, note, chunk_ids_json)
                values (?, ?, ?, ?::jsonb)
                """,
                messageId,
                score,
                note,
                toJson(safeChunkIds)
        );
        for (Long chunkId : safeChunkIds) {
            documentChunkRepository.applyFeedback(chunkId, score);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize feedback payload", e);
        }
    }
}
