package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.events.InMemoryWorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiAgentPlanExecuteLoopTest {

    private final DeepResearchAgent deepResearchAgent = mock(DeepResearchAgent.class);
    private final EvidenceAuditAgent evidenceAuditAgent = mock(EvidenceAuditAgent.class);
    private final DocumentComposerAgent documentComposerAgent = mock(DocumentComposerAgent.class);
    private final MultiAgentPlanExecuteLoop loop = new MultiAgentPlanExecuteLoop(
            deepResearchAgent,
            new EvidenceCurator(),
            evidenceAuditAgent,
            documentComposerAgent,
            new AgentTracePublisher(new InMemoryWorkbenchEventPublisher())
    );

    @Test
    void complexResearchRunsDeepResearchBeforeEvidenceAuditWithoutComposer() {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "complex_research",
                true,
                true,
                false
        );
        ResearchPacket packet = packet();
        AuditVerdict verdict = passVerdict();
        when(deepResearchAgent.research(42L, "question", scope, true)).thenReturn(packet);
        when(evidenceAuditAgent.audit(eq("question"), eq("Claim A is supported."), eq(packet))).thenReturn(verdict);

        MultiAgentPlanExecuteResult result = loop.run(42L, "question", scope, true, decision);

        InOrder order = inOrder(deepResearchAgent, evidenceAuditAgent, documentComposerAgent);
        order.verify(deepResearchAgent).research(42L, "question", scope, true);
        order.verify(evidenceAuditAgent).audit("question", "Claim A is supported.", packet);
        verify(documentComposerAgent, never()).compose(any(), any(), any(), any());
        assertThat(result.researchPacket()).isEqualTo(packet);
        assertThat(result.auditVerdict()).isSameAs(verdict);
        assertThat(result.documentDraft()).isNull();
        assertThat(result.plan().steps()).extracting(MultiAgentPlan.Step::agentRole)
                .containsExactly("Deep Research Agent", "Evidence Audit Agent");
        assertThat(result.plan().steps()).extracting(MultiAgentPlan.Step::status)
                .containsExactly("completed", "completed");
    }

    @Test
    void publishesSubagentStartedBeforeCallingDeepResearchAndCompletedAfterItReturns() {
        InMemoryWorkbenchEventPublisher eventPublisher = new InMemoryWorkbenchEventPublisher();
        MultiAgentPlanExecuteLoop tracedLoop = tracedLoop(eventPublisher);
        AgentTraceContext traceContext = traceContext();
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "complex_research",
                true,
                false,
                false
        );
        ResearchPacket packet = packet();
        doAnswer(invocation -> {
            List<WorkbenchEvent> liveEvents = eventPublisher.readRunEventsAfter("run-1", null);
            assertThat(liveEvents)
                    .extracting(event -> event.eventType().wireName())
                    .containsSubsequence("agent.plan.created", "agent.step.started")
                    .doesNotContain("agent.step.completed");
            WorkbenchEvent started = firstTraceStep(liveEvents, "deep-research", "running");
            assertThat(started.actor()).isEqualTo("deep_research_agent");
            assertThat(dataOf(started)).containsEntry("execution", "serial");
            return packet;
        }).when(deepResearchAgent).research(42L, "question", scope, true);

        tracedLoop.run(42L, "question", scope, true, decision, traceContext);

        List<WorkbenchEvent> finalEvents = eventPublisher.readRunEventsAfter("run-1", null);
        assertThat(finalEvents)
                .extracting(event -> event.eventType().wireName())
                .containsSubsequence("agent.plan.created", "agent.step.started", "agent.step.completed");
        WorkbenchEvent completed = firstTraceStep(finalEvents, "deep-research", "completed");
        assertThat(completed.actor()).isEqualTo("deep_research_agent");
        assertThat(dataOf(completed))
                .containsEntry("paperEvidenceCount", 1)
                .containsEntry("webEvidenceCount", 0)
                .containsEntry("memoryContextCount", 1);
    }

    @Test
    void publishesKnownSubagentFailureBeforeRethrowing() {
        InMemoryWorkbenchEventPublisher eventPublisher = new InMemoryWorkbenchEventPublisher();
        MultiAgentPlanExecuteLoop tracedLoop = tracedLoop(eventPublisher);
        AgentTraceContext traceContext = traceContext();
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "complex_research",
                true,
                false,
                false
        );
        doAnswer(invocation -> {
            assertThat(firstTraceStep(eventPublisher.readRunEventsAfter("run-1", null), "deep-research", "running"))
                    .extracting(WorkbenchEvent::actor)
                    .isEqualTo("deep_research_agent");
            throw new IllegalStateException("paper retrieval timeout");
        }).when(deepResearchAgent).research(42L, "question", scope, true);

        assertThatThrownBy(() -> tracedLoop.run(42L, "question", scope, true, decision, traceContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paper retrieval timeout");

        List<WorkbenchEvent> events = eventPublisher.readRunEventsAfter("run-1", null);
        assertThat(events)
                .extracting(event -> event.eventType().wireName())
                .containsSubsequence("agent.plan.created", "agent.step.started", "agent.step.failed");
        WorkbenchEvent failed = firstTraceStep(events, "deep-research", "failed");
        assertThat(failed.actor()).isEqualTo("deep_research_agent");
        assertThat(dataOf(failed))
                .containsEntry("execution", "serial")
                .containsEntry("error", "paper retrieval timeout");
    }

    @Test
    void emptyClaimsUseEmptyAuditDraftSoGroundedPacketsCanPass() {
        EvidenceAuditAgent realAuditAgent = spy(new EvidenceAuditAgent());
        MultiAgentPlanExecuteLoop loopWithRealAudit = new MultiAgentPlanExecuteLoop(
                deepResearchAgent,
                new EvidenceCurator(),
                realAuditAgent,
                documentComposerAgent,
                new AgentTracePublisher(new InMemoryWorkbenchEventPublisher())
        );
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "complex_research",
                true,
                true,
                false
        );
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of(),
                List.of("paper: content=Grounded evidence that does not repeat the user question."),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
        when(deepResearchAgent.research(42L, "question", scope, true)).thenReturn(packet);

        MultiAgentPlanExecuteResult result = loopWithRealAudit.run(42L, "question", scope, true, decision);

        verify(realAuditAgent).audit("question", "", packet);
        assertThat(result.auditVerdict().isPassing()).isTrue();
        assertThat(result.auditVerdict().verdict()).isEqualTo("pass");
    }

    @Test
    void documentRequestRunsComposerAfterAudit() {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "document_request",
                false,
                true,
                true
        );
        ResearchPacket packet = packet();
        AuditVerdict verdict = passVerdict();
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "question",
                "# question",
                List.of(new DocumentDraft.Section("Answer Mode", "Recommended answer mode: LOCAL_EVIDENCE"))
        );
        when(deepResearchAgent.research(42L, "question", scope, false)).thenReturn(packet);
        when(evidenceAuditAgent.audit(eq("question"), eq("Claim A is supported."), eq(packet))).thenReturn(verdict);
        when(documentComposerAgent.compose("markdown", "question", packet, verdict)).thenReturn(draft);

        MultiAgentPlanExecuteResult result = loop.run(42L, "question", scope, false, decision);

        InOrder order = inOrder(deepResearchAgent, evidenceAuditAgent, documentComposerAgent);
        order.verify(deepResearchAgent).research(42L, "question", scope, false);
        order.verify(evidenceAuditAgent).audit("question", "Claim A is supported.", packet);
        order.verify(documentComposerAgent).compose("markdown", "question", packet, verdict);
        assertThat(result.documentDraft()).isSameAs(draft);
        assertThat(result.plan().steps()).extracting(MultiAgentPlan.Step::agentRole)
                .containsExactly("Deep Research Agent", "Evidence Audit Agent", "Document Composer Agent");
        assertThat(result.finalSynthesisContext()).isEqualTo("# question");
    }

    @Test
    void documentRequestAuditsAndComposesOnlyCuratedEvidence() {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "document_request",
                false,
                true,
                true
        );
        ResearchPacket rawPacket = new ResearchPacket(
                "Assess LEO satellite interference and beamforming research route.",
                List.of(),
                List.of("paper: content=Simulation results show adaptive beamforming reduces inter-satellite interference in LEO constellations."),
                List.of("web: title=Ukraine update snippet=The latest news discussed Russia, Trump, and European diplomatic pressure."),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.WEB_SUPPLEMENT.name()
        );
        AuditVerdict verdict = passVerdict();
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "Assess LEO satellite interference and beamforming research route.",
                "# curated",
                List.of()
        );
        when(deepResearchAgent.research(42L, rawPacket.question(), scope, true)).thenReturn(rawPacket);
        when(evidenceAuditAgent.audit(eq(rawPacket.question()), eq(""), any(ResearchPacket.class))).thenReturn(verdict);
        when(documentComposerAgent.compose(eq("markdown"), eq(rawPacket.question()), any(ResearchPacket.class), eq(verdict)))
                .thenReturn(draft);

        MultiAgentPlanExecuteResult result = loop.run(42L, rawPacket.question(), scope, true, decision);

        org.mockito.ArgumentCaptor<ResearchPacket> auditPacket = org.mockito.ArgumentCaptor.forClass(ResearchPacket.class);
        org.mockito.ArgumentCaptor<ResearchPacket> composerPacket = org.mockito.ArgumentCaptor.forClass(ResearchPacket.class);
        verify(evidenceAuditAgent).audit(eq(rawPacket.question()), eq(""), auditPacket.capture());
        verify(documentComposerAgent).compose(eq("markdown"), eq(rawPacket.question()), composerPacket.capture(), eq(verdict));
        assertThat(auditPacket.getValue().paperEvidence())
                .containsExactly("paper: content=Simulation results show adaptive beamforming reduces inter-satellite interference in LEO constellations.");
        assertThat(auditPacket.getValue().webEvidence()).isEmpty();
        assertThat(auditPacket.getValue().evidenceGaps()).contains("Rejected 1 raw evidence candidate during curation.");
        assertThat(composerPacket.getValue()).isEqualTo(auditPacket.getValue());
        assertThat(result.researchPacket()).isEqualTo(auditPacket.getValue());
    }

    @Test
    void reactDecisionIsRejected() {
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.REACT,
                "simple",
                false,
                false,
                false
        );

        assertThatThrownBy(() -> loop.run(42L, "question", new ProjectEvidenceScope(List.of(), Map.of()), false, decision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLAN_EXECUTE");
    }

    @Test
    void finalSynthesisContextUsesDocumentBodyWhenPresent() {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "document_request",
                false,
                false,
                true
        );
        ResearchPacket packet = packet();
        AuditVerdict verdict = new AuditVerdict(
                "pass_with_cautions",
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                List.of(),
                List.of("Use local evidence only."),
                List.of("Address evidence gap.")
        );
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "question",
                "# question",
                List.of(new DocumentDraft.Section("Cautions", "Address evidence gap."))
        );
        when(deepResearchAgent.research(42L, "question", scope, true)).thenReturn(packet);
        when(evidenceAuditAgent.audit(eq("question"), eq("Claim A is supported."), eq(packet))).thenReturn(verdict);
        when(documentComposerAgent.compose("markdown", "question", packet, verdict)).thenReturn(draft);

        MultiAgentPlanExecuteResult result = loop.run(42L, "question", scope, true, decision);

        assertThat(result.finalSynthesisContext()).isEqualTo("# question");
    }

    @Test
    void nonDocumentFinalSynthesisIsUserFacingWeakEvidenceAnswer() {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "complex_research",
                true,
                true,
                false
        );
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of(),
                List.of("paper: documentId=10 chunkIndex=0 score=0.90 content=Paper evidence supports a cautious conclusion."),
                List.of("web: title=Recent result url=https://example.test score=0.70 snippet=Recent web context is consistent."),
                List.of(),
                List.of(),
                List.of("Citation sources are not persisted from Plan-Execute yet."),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass_with_cautions",
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                List.of(),
                List.of("Use as weak evidence only."),
                List.of("Add structured citations before stronger use.")
        );
        when(deepResearchAgent.research(42L, "question", scope, true)).thenReturn(packet);
        when(evidenceAuditAgent.audit(eq("question"), eq(""), eq(packet))).thenReturn(verdict);

        MultiAgentPlanExecuteResult result = loop.run(42L, "question", scope, true, decision);

        assertThat(result.finalSynthesisContext())
                .contains("Based on the audited research context")
                .contains("Paper evidence supports a cautious conclusion.")
                .contains("Recent web context is consistent.")
                .contains("Evidence gaps: Citation sources are not persisted from Plan-Execute yet.")
                .contains("Source policy cautions: Use as weak evidence only.")
                .doesNotContain("paper evidence count")
                .doesNotContain("audit verdict");
    }

    private ResearchPacket packet() {
        return new ResearchPacket(
                "question",
                List.of("Claim A is supported."),
                List.of("paper: content=Claim A is supported."),
                List.of(),
                List.of("memory context"),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
    }

    private AuditVerdict passVerdict() {
        return new AuditVerdict(
                "pass",
                AnswerMode.LOCAL_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private MultiAgentPlanExecuteLoop tracedLoop(InMemoryWorkbenchEventPublisher eventPublisher) {
        return new MultiAgentPlanExecuteLoop(
                deepResearchAgent,
                new EvidenceCurator(),
                evidenceAuditAgent,
                documentComposerAgent,
                new AgentTracePublisher(eventPublisher)
        );
    }

    private AgentTraceContext traceContext() {
        return new AgentTraceContext(
                "project-1",
                "session-1",
                "run-1",
                "message-1",
                "answer-1"
        );
    }

    private WorkbenchEvent firstTraceStep(List<WorkbenchEvent> events, String stepId, String status) {
        return events.stream()
                .filter(event -> event.payload().containsKey("step"))
                .filter(event -> {
                    Object stepValue = event.payload().get("step");
                    if (!(stepValue instanceof Map<?, ?> step)) {
                        return false;
                    }
                    return stepId.equals(step.get("stepId")) && status.equals(step.get("status"));
                })
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(WorkbenchEvent event) {
        return (Map<String, Object>) event.payload().get("data");
    }
}
