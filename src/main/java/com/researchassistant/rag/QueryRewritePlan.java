package com.researchassistant.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record QueryRewritePlan(
        String originalQuestion,
        List<String> retrievalQueries,
        List<String> keywords,
        String strategy,
        String fallbackReason
) {

    public static QueryRewritePlan from(String originalQuestion, String englishQuestion, List<String> keywords) {
        return from(originalQuestion, englishQuestion, keywords, "cjk_llm_rewrite", null);
    }

    public static QueryRewritePlan from(
            String originalQuestion,
            String englishQuestion,
            List<String> keywords,
            String strategy,
            String fallbackReason) {
        List<String> safeKeywords = keywords == null ? List.of() : keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        Set<String> retrievalQueries = new LinkedHashSet<>();
        addIfPresent(retrievalQueries, originalQuestion);
        addIfPresent(retrievalQueries, englishQuestion);
        if (!safeKeywords.isEmpty()) {
            addIfPresent(retrievalQueries, String.join(" ", safeKeywords));
        }

        return new QueryRewritePlan(
                originalQuestion,
                new ArrayList<>(retrievalQueries),
                safeKeywords,
                strategy == null || strategy.isBlank() ? "original_only" : strategy,
                fallbackReason
        );
    }

    public static QueryRewritePlan originalOnly(String originalQuestion) {
        return originalOnly(originalQuestion, null);
    }

    public static QueryRewritePlan originalOnly(String originalQuestion, String fallbackReason) {
        return from(originalQuestion, null, List.of(), "original_only", fallbackReason);
    }

    private static void addIfPresent(Set<String> values, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            values.add(candidate.trim());
        }
    }
}
