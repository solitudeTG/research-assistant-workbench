package com.researchassistant.orchestrator;

import java.util.List;
import java.util.Locale;

public record AuditVerdict(
        String verdict,
        String recommendedAnswerMode,
        List<String> unsupportedClaims,
        List<String> sourcePolicyIssues,
        List<String> requiredRevisions
) {

    public AuditVerdict {
        unsupportedClaims = List.copyOf(unsupportedClaims);
        sourcePolicyIssues = List.copyOf(sourcePolicyIssues);
        requiredRevisions = List.copyOf(requiredRevisions);
    }

    public boolean isPassing() {
        String normalizedVerdict = verdict.toLowerCase(Locale.ROOT);
        return "pass".equals(normalizedVerdict) || "pass_with_cautions".equals(normalizedVerdict);
    }
}
