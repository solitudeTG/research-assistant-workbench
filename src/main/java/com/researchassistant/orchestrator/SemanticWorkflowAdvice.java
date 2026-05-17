package com.researchassistant.orchestrator;

public record SemanticWorkflowAdvice(
        MultiAgentExecutionMode mode,
        String reason,
        boolean requiresDeepResearch,
        boolean requiresEvidenceAudit,
        boolean requiresDocumentComposer,
        double confidence
) {
}
