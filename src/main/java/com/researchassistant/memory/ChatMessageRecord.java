package com.researchassistant.memory;

import java.time.OffsetDateTime;

public record ChatMessageRecord(
        long id,
        long sessionId,
        String role,
        String content,
        String answerMode,
        OffsetDateTime createdAt
) {
}
