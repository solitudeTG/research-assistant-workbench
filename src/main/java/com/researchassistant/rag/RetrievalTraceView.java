package com.researchassistant.rag;

import java.time.OffsetDateTime;
import java.util.Map;

public record RetrievalTraceView(
        long id,
        long sessionId,
        String queryText,
        Map<String, Object> filters,
        Object topChunks,
        Object rerankResult,
        OffsetDateTime createdAt
) {
}
