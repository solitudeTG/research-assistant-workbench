package com.researchassistant.candidates;

import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeCandidateRecord(
        String id,
        String projectId,
        String sessionId,
        String answerId,
        String title,
        String statement,
        String suggestedSection,
        List<String> sourceTypes,
        List<String> evidenceSourceIds,
        String status,
        String sourceKind,
        Long sourceMemoryEntryId,
        int promotionHitCount,
        double promotionLastScore,
        String promotionReason,
        String decayReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
