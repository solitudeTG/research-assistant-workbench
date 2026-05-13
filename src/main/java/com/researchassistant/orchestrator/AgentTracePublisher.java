package com.researchassistant.orchestrator;

import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentTracePublisher {

    private final WorkbenchEventPublisher eventPublisher;

    public AgentTracePublisher(WorkbenchEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public WorkbenchEvent modeSelected(AgentTraceContext context, MultiAgentWorkflowDecision decision) {
        return publish(
                context,
                WorkbenchEventType.AGENT_STEP_COMPLETED,
                "supervisor",
                "Supervisor",
                "mode-selection",
                null,
                "completed",
                payload(
                        "mode", decision.mode().name(),
                        "reason", decision.reason()
                )
        );
    }

    public WorkbenchEvent planCreated(AgentTraceContext context, MultiAgentPlan plan) {
        return publish(
                context,
                WorkbenchEventType.AGENT_PLAN_CREATED,
                "supervisor",
                "Supervisor",
                "plan-execute-plan",
                null,
                "completed",
                payload(
                        "mode", plan.mode().name(),
                        "summary", plan.summary(),
                        "execution", "serial",
                        "steps", plan.steps().stream()
                                .map(this::stepData)
                                .toList()
                )
        );
    }

    public WorkbenchEvent subagentStarted(AgentTraceContext context, MultiAgentPlan.Step step) {
        return publish(
                context,
                WorkbenchEventType.AGENT_STEP_STARTED,
                agentRole(step),
                step.agentRole(),
                step.stepId(),
                "plan-execute-plan",
                "running",
                payload("execution", "serial")
        );
    }

    public WorkbenchEvent subagentCompleted(
            AgentTraceContext context,
            MultiAgentPlan.Step step,
            Map<String, Object> data
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("execution", "serial");
        if (data != null) {
            payload.putAll(data);
        }
        return publish(
                context,
                WorkbenchEventType.AGENT_STEP_COMPLETED,
                agentRole(step),
                step.agentRole(),
                step.stepId(),
                "plan-execute-plan",
                "completed",
                payload
        );
    }

    public WorkbenchEvent subagentFailed(AgentTraceContext context, MultiAgentPlan.Step step, String error) {
        return publish(
                context,
                WorkbenchEventType.AGENT_STEP_FAILED,
                agentRole(step),
                step.agentRole(),
                step.stepId(),
                "plan-execute-plan",
                "failed",
                payload(
                        "execution", "serial",
                        "error", error == null ? "" : error
                )
        );
    }

    public WorkbenchEvent auditVerdict(AgentTraceContext context, AuditVerdict verdict) {
        return subagentCompleted(
                context,
                new MultiAgentPlan.Step(
                        "evidence-audit",
                        "Audit claims against gathered evidence",
                        "Evidence Audit Agent",
                        "completed"
                ),
                payload(
                        "verdict", verdict.verdict(),
                        "recommendedAnswerMode", verdict.recommendedAnswerMode(),
                        "unsupportedClaimCount", verdict.unsupportedClaims().size(),
                        "sourcePolicyIssueCount", verdict.sourcePolicyIssues().size(),
                        "requiredRevisionCount", verdict.requiredRevisions().size()
                )
        );
    }

    public WorkbenchEvent composerCompleted(AgentTraceContext context, DocumentDraft draft) {
        return subagentCompleted(
                context,
                new MultiAgentPlan.Step(
                        "document-composer",
                        "Compose requested document from audited packet",
                        "Document Composer Agent",
                        "completed"
                ),
                payload(
                        "format", draft.format(),
                        "title", draft.title(),
                        "sectionCount", draft.sections().size()
                )
        );
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

    private Map<String, Object> stepData(MultiAgentPlan.Step step) {
        return Collections.unmodifiableMap(payload(
                "stepId", step.stepId(),
                "label", step.label(),
                "agentRole", step.agentRole(),
                "actorRole", agentRole(step),
                "status", step.status()
        ));
    }

    private String agentRole(MultiAgentPlan.Step step) {
        return switch (step.stepId()) {
            case "deep-research" -> "deep_research_agent";
            case "evidence-audit" -> "evidence_audit_agent";
            case "document-composer" -> "document_composer_agent";
            default -> stableRoleFromStepId(step.stepId());
        };
    }

    private String stableRoleFromStepId(String stepId) {
        String normalized = stepId
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "subagent" : normalized;
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            payload.put((String) keyValues[index], keyValues[index + 1]);
        }
        return payload;
    }
}
