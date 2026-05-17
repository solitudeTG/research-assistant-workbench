package com.researchassistant.evidence;

import java.time.OffsetDateTime;
import java.util.Map;

public record EvidenceSourceRecord(
        String id,
        String projectId,
        String answerId,
        String sourceType,
        String sourceId,
        String snippet,
        String strength,
        double relevanceScore,
        double feedbackScore,
        Map<String, Object> citationMeta,
        OffsetDateTime createdAt
) {
}
