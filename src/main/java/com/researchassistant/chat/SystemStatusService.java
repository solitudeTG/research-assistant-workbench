package com.researchassistant.chat;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final String aiBaseUrl;
    private final String aiChatModel;
    private final String aiEmbeddingModel;
    private final String aiVectorstoreType;

    public SystemStatusService(JdbcTemplate jdbcTemplate,
                               @Value("${AI_BASE_URL:${spring.ai.openai.base-url}}") String aiBaseUrl,
                               @Value("${AI_CHAT_MODEL:${spring.ai.openai.chat.options.model}}") String aiChatModel,
                               @Value("${AI_EMBEDDING_MODEL:${spring.ai.openai.embedding.options.model}}") String aiEmbeddingModel,
                               @Value("${AI_VECTORSTORE_TYPE:${spring.ai.vectorstore.type}}") String aiVectorstoreType) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiBaseUrl = aiBaseUrl;
        this.aiChatModel = aiChatModel;
        this.aiEmbeddingModel = aiEmbeddingModel;
        this.aiVectorstoreType = aiVectorstoreType;
    }

    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    public Map<String, Object> modelConfig() {
        return Map.of(
                "baseUrl", aiBaseUrl,
                "chatModel", aiChatModel,
                "embeddingModel", aiEmbeddingModel,
                "vectorstoreType", aiVectorstoreType
        );
    }

    public Map<String, Object> workspaceMetrics() {
        return Map.of(
                "documents", count("research_document"),
                "indexedDocuments", countWhere("research_document", "status = 'INDEXED'"),
                "sessions", count("chat_session"),
                "messages", count("chat_message"),
                "memoryEntries", count("memory_entry"),
                "retrievalTraces", count("retrieval_trace"),
                "feedbackEvents", count("message_feedback")
        );
    }

    private long count(String tableName) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        return count == null ? 0L : count;
    }

    private long countWhere(String tableName, String clause) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + clause, Long.class);
        return count == null ? 0L : count;
    }
}
