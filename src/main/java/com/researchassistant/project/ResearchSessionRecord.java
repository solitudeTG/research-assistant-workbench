package com.researchassistant.project;

import java.time.OffsetDateTime;

public record ResearchSessionRecord(
        String id,
        String projectId,
        String title,
        String status,
        OffsetDateTime lastMessageAt
) {
}
