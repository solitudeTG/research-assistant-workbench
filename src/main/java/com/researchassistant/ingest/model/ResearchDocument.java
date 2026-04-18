package com.researchassistant.ingest.model;

import java.time.OffsetDateTime;

public record ResearchDocument(
        Long id,
        String title,
        String originalFileName,
        String storagePath,
        DocumentStatus status,
        FailureStage failureStage,
        String parseError,
        int totalChunks,
        int totalTokens,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
