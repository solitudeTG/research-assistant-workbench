package com.researchassistant.project;

import java.time.OffsetDateTime;

public record ProjectRecord(
        String id,
        String topic,
        String summary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProjectStats stats
) {
}
