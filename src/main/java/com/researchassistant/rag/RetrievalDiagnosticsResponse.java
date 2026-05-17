package com.researchassistant.rag;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record RetrievalDiagnosticsResponse(
        String projectId,
        String sessionId,
        Map<String, Object> summary,
        List<RetrievalDiagnosticItem> retrievals,
        List<AnswerRunDiagnostic> answerRuns,
        List<Map<String, Object>> answers
) {

    public record RetrievalDiagnosticItem(
            long traceId,
            String queryText,
            OffsetDateTime createdAt,
            String answerRunKey,
            String runId,
            String answerId,
            String messageId,
            String question,
            int toolCallIndex,
            Map<String, Object> observation,
            String rewriteStrategy,
            List<String> retrievalQueries,
            List<String> keywords,
            Map<String, Object> backendStats,
            int returnedScopedChunkCount,
            String zeroHitReason,
            List<Map<String, Object>> topChunks
    ) {
    }

    public record AnswerRunDiagnostic(
            String answerRunKey,
            String runId,
            String answerId,
            String messageId,
            String question,
            OffsetDateTime firstRetrievedAt,
            OffsetDateTime lastRetrievedAt,
            int retrievalCalls,
            int zeroHitCalls,
            int returnedScopedChunks
    ) {
    }
}
