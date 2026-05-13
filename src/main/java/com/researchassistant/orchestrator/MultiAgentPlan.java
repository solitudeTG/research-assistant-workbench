package com.researchassistant.orchestrator;

import java.util.List;
import java.util.Objects;

public record MultiAgentPlan(
        MultiAgentExecutionMode mode,
        String summary,
        List<Step> steps
) {

    public MultiAgentPlan {
        mode = Objects.requireNonNull(mode, "mode");
        summary = Objects.requireNonNull(summary, "summary");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }

    public record Step(
            String stepId,
            String label,
            String agentRole,
            String status
    ) {

        public Step {
            stepId = Objects.requireNonNull(stepId, "stepId");
            label = Objects.requireNonNull(label, "label");
            agentRole = Objects.requireNonNull(agentRole, "agentRole");
            status = Objects.requireNonNull(status, "status");
        }

        public Step completed() {
            return new Step(stepId, label, agentRole, "completed");
        }
    }
}
