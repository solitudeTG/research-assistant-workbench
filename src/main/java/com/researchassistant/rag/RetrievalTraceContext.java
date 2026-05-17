package com.researchassistant.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public record RetrievalTraceContext(
        String projectId,
        String sessionId,
        String runId,
        String messageId,
        String answerId,
        String answerQuestion,
        int toolCallIndex
) {

    public static RetrievalTraceContext empty() {
        return new RetrievalTraceContext(null, null, null, null, null, null, 0);
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "projectId", projectId);
        putIfPresent(metadata, "sessionKey", sessionId);
        putIfPresent(metadata, "runId", runId);
        putIfPresent(metadata, "messageId", messageId);
        putIfPresent(metadata, "answerId", answerId);
        putIfPresent(metadata, "answerQuestion", answerQuestion);
        if (toolCallIndex > 0) {
            metadata.put("toolCallIndex", toolCallIndex);
        }
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }
}
