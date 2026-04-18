package com.researchassistant.memory;

import java.util.List;

public record MemoryEntryDraft(
        Long sessionId,
        String sourceKind,
        String topic,
        String summary,
        List<String> keyFindings,
        List<String> openQuestions,
        List<String> keywords,
        long sourceMessageStartId,
        long sourceMessageEndId
) {
}
