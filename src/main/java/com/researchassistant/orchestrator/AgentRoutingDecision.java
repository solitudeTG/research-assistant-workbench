package com.researchassistant.orchestrator;

public record AgentRoutingDecision(
        AgentIntent intent,
        boolean needsMemoryRecall,
        boolean needsPaperRag,
        boolean needsWebSearch,
        String reason
) {
}
