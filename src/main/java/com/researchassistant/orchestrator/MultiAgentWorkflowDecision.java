package com.researchassistant.orchestrator;

public record MultiAgentWorkflowDecision(
        MultiAgentExecutionMode mode,
        String reason,
        boolean requiresDeepResearch,
        boolean requiresEvidenceAudit,
        boolean requiresDocumentComposer,
        String decisionSource,
        String fallbackReason,
        double semanticConfidence
) {
    public MultiAgentWorkflowDecision(
            MultiAgentExecutionMode mode,
            String reason,
            boolean requiresDeepResearch,
            boolean requiresEvidenceAudit,
            boolean requiresDocumentComposer
    ) {
        this(
                mode,
                reason,
                requiresDeepResearch,
                requiresEvidenceAudit,
                requiresDocumentComposer,
                "fallback",
                reason,
                0.0
        );
    }
}
