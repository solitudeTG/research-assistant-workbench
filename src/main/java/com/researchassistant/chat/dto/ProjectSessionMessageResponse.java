package com.researchassistant.chat.dto;

import java.time.OffsetDateTime;

public record ProjectSessionMessageResponse(
        String id,
        String sessionId,
        String role,
        String content,
        String answerId,
        String runId,
        String answerMode,
        OffsetDateTime createdAt
) {
}
