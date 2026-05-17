package com.researchassistant.orchestrator;

public record AgentTraceContext(
        String projectId,
        String sessionId,
        String runId,
        String messageId,
        String answerId
) {
}
