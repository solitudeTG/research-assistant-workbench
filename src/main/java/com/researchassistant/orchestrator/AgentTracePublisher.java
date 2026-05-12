package com.researchassistant.orchestrator;

import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentTracePublisher {

    private final WorkbenchEventPublisher eventPublisher;

    public AgentTracePublisher(WorkbenchEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public WorkbenchEvent publish(
            AgentTraceContext context,
            WorkbenchEventType eventType,
            String agentRole,
            String displayName,
            String stepId,
            String parentStepId,
            String status,
            Map<String, Object> data) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("agentId", agentRole);
        actor.put("agentRole", agentRole);
        actor.put("displayName", displayName);

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stepId", stepId);
        if (parentStepId != null && !parentStepId.isBlank()) {
            step.put("parentStepId", parentStepId);
        }
        step.put("label", displayName);
        step.put("status", status);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", context.messageId());
        payload.put("answerId", context.answerId());
        payload.put("actor", Collections.unmodifiableMap(actor));
        payload.put("step", Collections.unmodifiableMap(step));
        payload.put("data", data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data)));

        return eventPublisher.publish(new WorkbenchEvent(
                null,
                eventType,
                context.projectId(),
                context.sessionId(),
                context.runId(),
                agentRole,
                0,
                null,
                context.answerId(),
                context.messageId(),
                null,
                payload
        ));
    }
}
