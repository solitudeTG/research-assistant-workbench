package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.ProjectEvidenceScope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
            evidenceAuditAgent,
            documentComposerAgent
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
        assertThat(result.researchPacket()).isSameAs(packet);
        assertThat(result.auditVerdict()).isSameAs(verdict);
        assertThat(result.documentDraft()).isNull();
        assertThat(result.plan().steps()).extracting(MultiAgentPlan.Step::agentRole)
                .containsExactly("Deep Research Agent", "Evidence Audit Agent");
        assertThat(result.plan().steps()).extracting(MultiAgentPlan.Step::status)
                .containsExactly("completed", "completed");
    }

    @Test
    void emptyClaimsUseEmptyAuditDraftSoGroundedPacketsCanPass() {
        EvidenceAuditAgent realAuditAgent = spy(new EvidenceAuditAgent());
        MultiAgentPlanExecuteLoop loopWithRealAudit = new MultiAgentPlanExecuteLoop(
                deepResearchAgent,
                realAuditAgent,
                documentComposerAgent
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
        assertThat(result.finalSynthesisContext()).contains("audit verdict: pass");
        assertThat(result.finalSynthesisContext()).contains("document format: markdown");
        assertThat(result.finalSynthesisContext()).contains("document title: question");
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
    void finalSynthesisContextIncludesAuditVerdictAndDocumentInfoWhenPresent() {
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

        assertThat(result.finalSynthesisContext()).contains("audit verdict: pass_with_cautions");
        assertThat(result.finalSynthesisContext()).contains("recommended answer mode: LOCAL_WEAK_EVIDENCE");
        assertThat(result.finalSynthesisContext()).contains("source policy issues: Use local evidence only.");
        assertThat(result.finalSynthesisContext()).contains("required revisions: Address evidence gap.");
        assertThat(result.finalSynthesisContext()).contains("document format: markdown");
        assertThat(result.finalSynthesisContext()).contains("document sections: Cautions");
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
}
