package com.researchassistant.orchestrator;

public record MultiAgentWorkflowDecision(
        MultiAgentExecutionMode mode,
        String reason,
        boolean requiresDeepResearch,
        boolean requiresEvidenceAudit,
        boolean requiresDocumentComposer
) {
}
