package com.researchassistant.orchestrator;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record AuditVerdict(
        String verdict,
        String recommendedAnswerMode,
        List<String> unsupportedClaims,
        List<String> sourcePolicyIssues,
        List<String> requiredRevisions
) {

    public AuditVerdict {
        verdict = Objects.requireNonNull(verdict, "verdict");
        recommendedAnswerMode = Objects.requireNonNull(recommendedAnswerMode, "recommendedAnswerMode");
        unsupportedClaims = List.copyOf(Objects.requireNonNull(unsupportedClaims, "unsupportedClaims"));
        sourcePolicyIssues = List.copyOf(Objects.requireNonNull(sourcePolicyIssues, "sourcePolicyIssues"));
        requiredRevisions = List.copyOf(Objects.requireNonNull(requiredRevisions, "requiredRevisions"));
    }

    public boolean isPassing() {
        String normalizedVerdict = verdict.toLowerCase(Locale.ROOT);
        return "pass".equals(normalizedVerdict) || "pass_with_cautions".equals(normalizedVerdict);
    }
}
