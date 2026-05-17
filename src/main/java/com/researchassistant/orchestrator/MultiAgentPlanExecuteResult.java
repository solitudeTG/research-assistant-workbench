package com.researchassistant.orchestrator;

import java.util.Objects;

public record MultiAgentPlanExecuteResult(
        MultiAgentPlan plan,
        ResearchPacket researchPacket,
        AuditVerdict auditVerdict,
        DocumentDraft documentDraft,
        String finalSynthesisContext
) {

    public MultiAgentPlanExecuteResult {
        plan = Objects.requireNonNull(plan, "plan");
        finalSynthesisContext = Objects.requireNonNull(finalSynthesisContext, "finalSynthesisContext");
    }
}
