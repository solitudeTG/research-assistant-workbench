package com.researchassistant.orchestrator;

import com.researchassistant.events.InMemoryWorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventType;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTracePublisherTest {

    @Test
    void publishesCommonActorStepAndDataPayloadShapeWithImmutableNestedMaps() {
        InMemoryWorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();
        AgentTracePublisher trace = new AgentTracePublisher(publisher);
        AgentTraceContext context = new AgentTraceContext(
                "project-1",
                "session-1",
                "run-1",
                "msg-1",
                "ans-1"
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", "paper_rag");

        trace.publish(
                context,
                WorkbenchEventType.TOOL_CALLED,
                "retrieval_worker",
                "Retrieval step",
                "step_retrieval",
                "step_plan",
                "running",
                data
        );

        data.put("toolName", "mutated_after_publish");

        WorkbenchEvent event = publisher.readRunEventsAfter("run-1", null).get(0);
        assertThat(event.actor()).isEqualTo("retrieval_worker");
        assertThat(event.answerId()).isEqualTo("ans-1");
        assertThat(event.turnId()).isEqualTo("msg-1");
        assertThat(event.payload())
                .containsEntry("messageId", "msg-1")
                .containsEntry("answerId", "ans-1")
                .containsKeys("actor", "step", "data");

        Map<String, Object> actor = dataMap(event.payload().get("actor"));
        Map<String, Object> step = dataMap(event.payload().get("step"));
        Map<String, Object> eventData = dataMap(event.payload().get("data"));

        assertThat(actor)
                .containsEntry("agentId", "retrieval_worker")
                .containsEntry("agentRole", "retrieval_worker")
                .containsEntry("displayName", "Retrieval step");
        assertThat(step)
                .containsEntry("stepId", "step_retrieval")
                .containsEntry("parentStepId", "step_plan")
                .containsEntry("label", "Retrieval step")
                .containsEntry("status", "running");
        assertThat(eventData).containsEntry("toolName", "paper_rag");

        assertUnmodifiable(actor);
        assertUnmodifiable(step);
        assertUnmodifiable(eventData);
    }

    @Test
    void publishesPlanExecuteTraceHelperPayloadsWithStableActorStepAndDataShape() {
        InMemoryWorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();
        AgentTracePublisher trace = new AgentTracePublisher(publisher);
        AgentTraceContext context = new AgentTraceContext(
                "project-1",
                "session-1",
                "run-plan",
                "msg-1",
                "ans-1"
        );
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "complex_research_request",
                true,
                true,
                true
        );
        MultiAgentPlan plan = new MultiAgentPlan(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "Serial plan-execute workflow: complex_research_request",
                List.of(
                        new MultiAgentPlan.Step("deep-research", "Collect and separate grounded evidence", "Deep Research Agent", "completed"),
                        new MultiAgentPlan.Step("evidence-audit", "Audit claims against gathered evidence", "Evidence Audit Agent", "completed"),
                        new MultiAgentPlan.Step("document-composer", "Compose requested document from audited packet", "Document Composer Agent", "completed")
                )
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass_with_cautions",
                "LOCAL_WEAK_EVIDENCE",
                List.of("A long unsupported claim that must not leak through the trace payload."),
                List.of("Use local evidence only."),
                List.of("Qualify the answer.")
        );
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "Audited report",
                "# Audited report\n\nLong body should not be copied into trace payload.",
                List.of(
                        new DocumentDraft.Section("Summary", "Body"),
                        new DocumentDraft.Section("Evidence", "Body")
                )
        );

        trace.modeSelected(context, decision);
        trace.planCreated(context, plan);
        trace.subagentStarted(context, plan.steps().get(0));
        trace.subagentCompleted(context, plan.steps().get(0), Map.of("paperEvidenceCount", 2));
        trace.subagentFailed(context, plan.steps().get(0), "Timeout while retrieving local evidence.");
        trace.auditVerdict(context, verdict);
        trace.composerCompleted(context, draft);

        List<WorkbenchEvent> events = publisher.readRunEventsAfter("run-plan", null);
        assertThat(events).extracting(event -> event.eventType().wireName())
                .containsExactly(
                        "agent.step.completed",
                        "agent.plan.created",
                        "agent.step.started",
                        "agent.step.completed",
                        "agent.step.failed",
                        "agent.step.completed",
                        "agent.step.completed"
                );

        assertThat(assertTraceEvent(events.get(0), "supervisor", "mode-selection", "completed"))
                .containsEntry("mode", "PLAN_EXECUTE")
                .containsEntry("reason", "complex_research_request");

        Map<String, Object> planData = assertTraceEvent(events.get(1), "supervisor", "plan-execute-plan", "completed");
        assertThat(planData)
                .containsEntry("mode", "PLAN_EXECUTE")
                .containsEntry("summary", "Serial plan-execute workflow: complex_research_request")
                .containsEntry("execution", "serial");
        assertThat((List<?>) planData.get("steps")).hasSize(3);

        assertThat(assertTraceEvent(events.get(2), "deep_research_agent", "deep-research", "running"))
                .containsEntry("execution", "serial");
        assertThat(assertTraceEvent(events.get(3), "deep_research_agent", "deep-research", "completed"))
                .containsEntry("paperEvidenceCount", 2);
        assertThat(assertTraceEvent(events.get(4), "deep_research_agent", "deep-research", "failed"))
                .containsEntry("error", "Timeout while retrieving local evidence.");

        Map<String, Object> auditData = assertTraceEvent(events.get(5), "evidence_audit_agent", "evidence-audit", "completed");
        assertThat(auditData)
                .containsEntry("verdict", "pass_with_cautions")
                .containsEntry("recommendedAnswerMode", "LOCAL_WEAK_EVIDENCE")
                .containsEntry("unsupportedClaimCount", 1)
                .containsEntry("sourcePolicyIssueCount", 1)
                .containsEntry("requiredRevisionCount", 1)
                .doesNotContainKey("unsupportedClaims");

        Map<String, Object> composerData = assertTraceEvent(events.get(6), "document_composer_agent", "document-composer", "completed");
        assertThat(composerData)
                .containsEntry("format", "markdown")
                .containsEntry("title", "Audited report")
                .containsEntry("sectionCount", 2)
                .doesNotContainKey("body");
    }

    private Map<String, Object> assertTraceEvent(
            WorkbenchEvent event,
            String expectedAgentRole,
            String expectedStepId,
            String expectedStatus
    ) {
        assertThat(event.actor()).isEqualTo(expectedAgentRole);
        Map<String, Object> actor = dataMap(event.payload().get("actor"));
        Map<String, Object> step = dataMap(event.payload().get("step"));
        Map<String, Object> eventData = dataMap(event.payload().get("data"));
        assertThat(actor).containsEntry("agentRole", expectedAgentRole);
        assertThat(step)
                .containsEntry("stepId", expectedStepId)
                .containsEntry("status", expectedStatus);
        return eventData;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void assertUnmodifiable(Map<?, ?> payloadMap) {
        assertThatThrownBy(() -> ((Map) payloadMap).put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
