package com.researchassistant.rag;

public record RetrievalBackendStats(
        int queryCount,
        int preScopeHits,
        int postScopeHits,
        long durationMs
) {
    public static RetrievalBackendStats of(int queryCount, int hits, long durationMs) {
        return new RetrievalBackendStats(queryCount, hits, hits, durationMs);
    }
}
