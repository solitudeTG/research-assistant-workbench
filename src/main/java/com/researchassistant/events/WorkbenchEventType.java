package com.researchassistant.events;

public enum WorkbenchEventType {
    RUN_STARTED("run.started"),
    RUN_COMPLETED("run.completed"),
    RUN_FAILED("run.failed"),
    AGENT_PLAN_CREATED("agent.plan.created"),
    AGENT_STEP_STARTED("agent.step.started"),
    AGENT_STEP_COMPLETED("agent.step.completed"),
    RETRIEVAL_STARTED("retrieval.started"),
    RETRIEVAL_COMPLETED("retrieval.completed"),
    EVIDENCE_EVALUATED("evidence.evaluated"),
    ANSWER_DELTA("answer.delta"),
    ANSWER_COMPLETED("answer.completed"),
    CANDIDATE_CREATED("candidate.created"),
    KNOWLEDGE_ENTRY_CREATED("knowledge.entry.created"),
    SOURCE_STATUS_CHANGED("source.status.changed"),
    MEMORY_FLUSH_STARTED("memory.flush.started"),
    MEMORY_FLUSH_COMPLETED("memory.flush.completed"),
    FEEDBACK_APPLIED("feedback.applied");

    private final String wireName;

    WorkbenchEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static WorkbenchEventType fromWireName(String wireName) {
        for (WorkbenchEventType eventType : values()) {
            if (eventType.wireName.equals(wireName)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Unknown workbench event type wire name: " + wireName);
    }
}
