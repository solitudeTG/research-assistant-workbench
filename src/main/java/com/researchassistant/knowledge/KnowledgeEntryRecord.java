package com.researchassistant.knowledge;

import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeEntryRecord(
        String id,
        String projectId,
        String section,
        String title,
        String content,
        String evidenceStatus,
        String sourceCandidateId,
        List<String> evidenceSourceIds,
        boolean archived,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
