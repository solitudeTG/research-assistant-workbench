package com.researchassistant.ingest.model;

import java.time.OffsetDateTime;

public record SourceDocument(
        String id,
        String projectId,
        String type,
        String title,
        String uri,
        String status,
        String failureStage,
        String errorMessage,
        int depositedKnowledgeCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
