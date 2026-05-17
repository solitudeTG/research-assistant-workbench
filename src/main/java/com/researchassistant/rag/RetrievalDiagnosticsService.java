package com.researchassistant.rag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RetrievalDiagnosticsService {

    private static final List<String> BACKENDS = List.of("keyword", "vector", "metadata");
    private static final int MAX_TOP_CHUNKS = 5;
    private static final int MAX_SNIPPET_LENGTH = 280;

    private final RetrievalTraceRepository retrievalTraceRepository;

    public RetrievalDiagnosticsService(RetrievalTraceRepository retrievalTraceRepository) {
        this.retrievalTraceRepository = retrievalTraceRepository;
    }

    public RetrievalDiagnosticsResponse sessionDiagnostics(String projectId, String sessionId) {
        List<RetrievalTraceView> traces = retrievalTraceRepository.findBySessionKey(sessionId);
        SummaryAccumulator summary = new SummaryAccumulator();
        List<RetrievalDiagnosticsResponse.RetrievalDiagnosticItem> retrievals = new ArrayList<>();
        Map<String, AnswerRunAccumulator> answerRuns = new LinkedHashMap<>();

        for (RetrievalTraceView trace : traces) {
            Map<String, Object> filters = trace.filters() == null ? Map.of() : trace.filters();
            Map<String, Object> rerank = asMap(trace.rerankResult());
            Map<String, Object> observation = asMap(rerank.get("observation"));
            Map<String, Object> backendStats = asMap(observation.get("backendStats"));
            String rewriteStrategy = stringValue(
                    firstNonNull(observation.get("rewriteStrategy"), filters.get("rewriteStrategy")),
                    "unknown"
            );
            List<String> retrievalQueries = stringList(firstNonNull(
                    observation.get("retrievalQueries"),
                    filters.get("retrievalQueries"),
                    filters.get("rewrittenQueries")
            ));
            List<String> keywords = stringList(firstNonNull(observation.get("keywords"), filters.get("keywords")));
            int returnedScopedChunkCount = intValue(observation.get("returnedScopedChunkCount"));
            String zeroHitReason = nullableString(observation.get("zeroHitReason"));
            List<Map<String, Object>> topChunks = boundedTopChunks(trace.topChunks());
            String runId = nullableString(firstNonNull(filters.get("runId"), observation.get("runId")));
            String answerId = nullableString(firstNonNull(filters.get("answerId"), observation.get("answerId")));
            String messageId = nullableString(firstNonNull(filters.get("messageId"), observation.get("messageId")));
            String question = stringValue(firstNonNull(
                    filters.get("answerQuestion"),
                    filters.get("question"),
                    observation.get("answerQuestion"),
                    observation.get("question")
            ), trace.queryText());
            int toolCallIndex = intValue(firstNonNull(filters.get("toolCallIndex"), observation.get("toolCallIndex")));
            String answerRunKey = answerRunKey(runId, answerId, messageId, trace.id());

            summary.accept(rewriteStrategy, backendStats, returnedScopedChunkCount, zeroHitReason);
            RetrievalDiagnosticsResponse.RetrievalDiagnosticItem item = new RetrievalDiagnosticsResponse.RetrievalDiagnosticItem(
                    trace.id(),
                    trace.queryText(),
                    trace.createdAt(),
                    answerRunKey,
                    runId,
                    answerId,
                    messageId,
                    question,
                    toolCallIndex,
                    observation,
                    rewriteStrategy,
                    retrievalQueries,
                    keywords,
                    backendStats,
                    returnedScopedChunkCount,
                    zeroHitReason,
                    topChunks
            );
            retrievals.add(item);
            answerRuns.computeIfAbsent(
                    answerRunKey,
                    key -> new AnswerRunAccumulator(key, runId, answerId, messageId, question)
            ).accept(item);
        }
        Map<String, Object> summaryMap = summary.toMap();
        summaryMap.put("answerRunCount", answerRuns.size());

        return new RetrievalDiagnosticsResponse(
                projectId,
                sessionId,
                summaryMap,
                retrievals,
                answerRuns.values().stream().map(AnswerRunAccumulator::toResponse).toList(),
                List.of()
        );
    }

    private String answerRunKey(String runId, String answerId, String messageId, long traceId) {
        if (runId != null && !runId.isBlank()) {
            return runId;
        }
        if (answerId != null && !answerId.isBlank()) {
            return answerId;
        }
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        return "trace-" + traceId;
    }

    private List<Map<String, Object>> boundedTopChunks(Object value) {
        if (!(value instanceof List<?> chunks)) {
            return List.of();
        }
        List<Map<String, Object>> bounded = new ArrayList<>();
        for (Object chunk : chunks) {
            if (bounded.size() >= MAX_TOP_CHUNKS) {
                break;
            }
            Map<String, Object> source = asMap(chunk);
            if (source.isEmpty()) {
                continue;
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            putIfPresent(projected, "chunkId", firstNonNull(source.get("chunkId"), source.get("id")));
            putIfPresent(projected, "documentId", source.get("documentId"));
            putIfPresent(projected, "sourceId", source.get("sourceId"));
            putIfPresent(projected, "chunkIndex", source.get("chunkIndex"));
            putIfPresent(projected, "score", firstNonNull(source.get("score"), source.get("finalScore")));
            putIfPresent(projected, "feedbackScore", source.get("feedbackScore"));
            if (source.containsKey("feedbackScore")) {
                projected.put("feedbackScoreAdjustment", feedbackScoreAdjustment(source.get("feedbackScore")));
            }
            putIfPresent(projected, "retrievalModes", source.get("retrievalModes"));
            projected.put("snippet", boundedSnippet(firstNonNull(source.get("snippet"), source.get("content"))));
            bounded.add(projected);
        }
        return bounded;
    }

    private String boundedSnippet(Object value) {
        String text = nullableString(value);
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
    }

    private double feedbackScoreAdjustment(Object value) {
        double adjustment = RetrievalFeedbackScoring.finalScore(0.0, doubleValue(value));
        return BigDecimal.valueOf(adjustment)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .doubleValue();
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return 0.0;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    copy.put(key.toString(), mapValue);
                }
            });
            return copy;
        }
        return Map.of();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::nullableString)
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private String stringValue(Object value, String fallback) {
        String text = nullableString(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String nullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return 0;
    }

    private static class AnswerRunAccumulator {
        private final String answerRunKey;
        private final String runId;
        private final String answerId;
        private final String messageId;
        private final String question;
        private java.time.OffsetDateTime firstRetrievedAt;
        private java.time.OffsetDateTime lastRetrievedAt;
        private int retrievalCalls;
        private int zeroHitCalls;
        private int returnedScopedChunks;

        private AnswerRunAccumulator(String answerRunKey, String runId, String answerId, String messageId, String question) {
            this.answerRunKey = answerRunKey;
            this.runId = runId;
            this.answerId = answerId;
            this.messageId = messageId;
            this.question = question;
        }

        private void accept(RetrievalDiagnosticsResponse.RetrievalDiagnosticItem item) {
            retrievalCalls++;
            if (item.zeroHitReason() != null && !item.zeroHitReason().isBlank()) {
                zeroHitCalls++;
            }
            returnedScopedChunks += item.returnedScopedChunkCount();
            if (firstRetrievedAt == null || isBefore(item.createdAt(), firstRetrievedAt)) {
                firstRetrievedAt = item.createdAt();
            }
            if (lastRetrievedAt == null || isAfter(item.createdAt(), lastRetrievedAt)) {
                lastRetrievedAt = item.createdAt();
            }
        }

        private RetrievalDiagnosticsResponse.AnswerRunDiagnostic toResponse() {
            return new RetrievalDiagnosticsResponse.AnswerRunDiagnostic(
                    answerRunKey,
                    runId,
                    answerId,
                    messageId,
                    question,
                    firstRetrievedAt,
                    lastRetrievedAt,
                    retrievalCalls,
                    zeroHitCalls,
                    returnedScopedChunks
            );
        }

        private boolean isBefore(java.time.OffsetDateTime candidate, java.time.OffsetDateTime current) {
            return candidate != null && (current == null || candidate.isBefore(current));
        }

        private boolean isAfter(java.time.OffsetDateTime candidate, java.time.OffsetDateTime current) {
            return candidate != null && (current == null || candidate.isAfter(current));
        }
    }

    private static class SummaryAccumulator {
        private int retrievalCalls;
        private int zeroHitCalls;
        private int returnedScopedChunks;
        private final Map<String, BackendTotals> backendTotals = new LinkedHashMap<>();
        private final Map<String, Integer> zeroHitReasonCounts = new LinkedHashMap<>();
        private final Map<String, Integer> strategyCounts = new LinkedHashMap<>();

        private SummaryAccumulator() {
            for (String backend : BACKENDS) {
                backendTotals.put(backend, new BackendTotals());
            }
        }

        private void accept(
                String rewriteStrategy,
                Map<String, Object> backendStats,
                int returnedScopedChunkCount,
                String zeroHitReason
        ) {
            retrievalCalls++;
            returnedScopedChunks += returnedScopedChunkCount;
            strategyCounts.merge(rewriteStrategy, 1, Integer::sum);
            if (zeroHitReason != null && !zeroHitReason.isBlank()) {
                zeroHitCalls++;
                zeroHitReasonCounts.merge(zeroHitReason, 1, Integer::sum);
            }
            for (String backend : BACKENDS) {
                backendTotals.get(backend).accept(asStatsMap(backendStats.get(backend)));
            }
        }

        private Map<String, Object> toMap() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("retrievalCalls", retrievalCalls);
            summary.put("zeroHitCalls", zeroHitCalls);
            summary.put("returnedScopedChunks", returnedScopedChunks);
            summary.put("avgReturnedScopedChunks", averageReturnedScopedChunks());
            summary.put("backendTotals", backendTotalsMap());
            summary.put("zeroHitReasonCounts", zeroHitReasonCounts);
            summary.put("strategyCounts", strategyCounts);
            return summary;
        }

        private double averageReturnedScopedChunks() {
            if (retrievalCalls == 0) {
                return 0.0;
            }
            return BigDecimal.valueOf(returnedScopedChunks)
                    .divide(BigDecimal.valueOf(retrievalCalls), 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        private Map<String, Object> backendTotalsMap() {
            Map<String, Object> totals = new LinkedHashMap<>();
            backendTotals.forEach((backend, total) -> totals.put(backend, total.toMap()));
            return totals;
        }

        private Map<String, Object> asStatsMap(Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    copy.put(key.toString(), mapValue);
                }
            });
            return copy;
        }
    }

    private static class BackendTotals {
        private int queryCount;
        private int preScopeHits;
        private int postScopeHits;
        private long durationMs;

        private void accept(Map<String, Object> stats) {
            queryCount += intValue(stats, "queryCount");
            preScopeHits += intValue(stats, "preScopeHits");
            postScopeHits += intValue(stats, "postScopeHits");
            durationMs += longValue(stats, "durationMs");
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("queryCount", queryCount);
            map.put("preScopeHits", preScopeHits);
            map.put("postScopeHits", postScopeHits);
            map.put("durationMs", durationMs);
            return map;
        }

        private int intValue(Map<String, Object> map, String key) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                return Integer.parseInt(text);
            }
            return 0;
        }

        private long longValue(Map<String, Object> map, String key) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                return Long.parseLong(text);
            }
            return 0L;
        }
    }
}
