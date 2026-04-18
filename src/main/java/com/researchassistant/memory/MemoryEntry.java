package com.researchassistant.memory;

import java.time.OffsetDateTime;
import java.util.List;

public record MemoryEntry(
        long id,
        Long sessionId,
        String sourceKind,
        String topic,
        String summary,
        List<String> keyFindings,
        List<String> openQuestions,
        List<String> keywords,
        long sourceMessageStartId,
        long sourceMessageEndId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
