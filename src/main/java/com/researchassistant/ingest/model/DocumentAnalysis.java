package com.researchassistant.ingest.model;

import java.time.OffsetDateTime;
import java.util.List;

public record DocumentAnalysis(
        long documentId,
        String abstractText,
        String summary,
        List<String> methods,
        List<String> contributions,
        List<String> keywords,
        List<String> outline,
        OffsetDateTime generatedAt,
        OffsetDateTime updatedAt
) {
}
