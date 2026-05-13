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

            summary.accept(rewriteStrategy, backendStats, returnedScopedChunkCount, zeroHitReason);
            retrievals.add(new RetrievalDiagnosticsResponse.RetrievalDiagnosticItem(
                    trace.id(),
                    trace.queryText(),
                    trace.createdAt(),
                    observation,
                    rewriteStrategy,
                    retrievalQueries,
                    keywords,
                    backendStats,
                    returnedScopedChunkCount,
                    zeroHitReason,
                    topChunks
            ));
        }

        return new RetrievalDiagnosticsResponse(
                projectId,
                sessionId,
                summary.toMap(),
                retrievals,
                List.of()
        );
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
