package com.researchassistant.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RetrievalObservation(
        String originalQuery,
        List<Long> allowedDocumentIds,
        int boundedMaxResults,
        String rewriteStrategy,
        List<String> retrievalQueries,
        List<String> keywords,
        Map<String, RetrievalBackendStats> backendStats,
        int mergedCandidateCount,
        int rerankedChunkCount,
        int returnedScopedChunkCount,
        ZeroHitReason zeroHitReason
) {

    public Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("originalQuery", originalQuery);
        summary.put("boundedMaxResults", boundedMaxResults);
        summary.put("allowedDocumentCount", allowedDocumentIds == null ? 0 : allowedDocumentIds.size());
        summary.put("rewriteStrategy", rewriteStrategy);
        summary.put("retrievalQueryCount", retrievalQueries == null ? 0 : retrievalQueries.size());
        summary.put("backendStats", backendStats == null ? Map.of() : backendStats);
        summary.put("mergedCandidateCount", mergedCandidateCount);
        summary.put("rerankedChunkCount", rerankedChunkCount);
        summary.put("returnedScopedChunkCount", returnedScopedChunkCount);
        summary.put("zeroHitReason", zeroHitReason == null ? null : zeroHitReason.name());
        return summary;
    }

    public static Builder builder(String originalQuery, List<Long> allowedDocumentIds, int boundedMaxResults) {
        return new Builder(originalQuery, allowedDocumentIds, boundedMaxResults);
    }

    public static class Builder {
        private final String originalQuery;
        private final List<Long> allowedDocumentIds;
        private final int boundedMaxResults;
        private String rewriteStrategy = "original_only";
        private List<String> retrievalQueries = List.of();
        private List<String> keywords = List.of();
        private Map<String, RetrievalBackendStats> backendStats = Map.of();
        private int mergedCandidateCount;
        private int rerankedChunkCount;
        private int returnedScopedChunkCount;
        private ZeroHitReason zeroHitReason;

        private Builder(String originalQuery, List<Long> allowedDocumentIds, int boundedMaxResults) {
            this.originalQuery = originalQuery;
            this.allowedDocumentIds = allowedDocumentIds == null ? List.of() : List.copyOf(allowedDocumentIds);
            this.boundedMaxResults = boundedMaxResults;
        }

        public Builder rewritePlan(QueryRewritePlan rewritePlan) {
            if (rewritePlan != null) {
                this.rewriteStrategy = rewritePlan.strategy();
                this.retrievalQueries = rewritePlan.retrievalQueries();
                this.keywords = rewritePlan.keywords();
            }
            return this;
        }

        public Builder backendStats(Map<String, RetrievalBackendStats> backendStats) {
            this.backendStats = backendStats == null ? Map.of() : new LinkedHashMap<>(backendStats);
            return this;
        }

        public Builder mergedCandidateCount(int mergedCandidateCount) {
            this.mergedCandidateCount = mergedCandidateCount;
            return this;
        }

        public Builder rerankedChunkCount(int rerankedChunkCount) {
            this.rerankedChunkCount = rerankedChunkCount;
            return this;
        }

        public Builder returnedScopedChunkCount(int returnedScopedChunkCount) {
            this.returnedScopedChunkCount = returnedScopedChunkCount;
            return this;
        }

        public Builder zeroHitReason(ZeroHitReason zeroHitReason) {
            this.zeroHitReason = zeroHitReason;
            return this;
        }

        public RetrievalObservation build() {
            return new RetrievalObservation(
                    originalQuery,
                    allowedDocumentIds,
                    boundedMaxResults,
                    rewriteStrategy,
                    retrievalQueries,
                    keywords,
                    backendStats,
                    mergedCandidateCount,
                    rerankedChunkCount,
                    returnedScopedChunkCount,
                    zeroHitReason
            );
        }
    }
}
