package com.researchassistant.report;

import java.time.OffsetDateTime;

public record ReportExportResult(
        String sessionKey,
        String reportPath,
        String content,
        OffsetDateTime createdAt
) {
}
