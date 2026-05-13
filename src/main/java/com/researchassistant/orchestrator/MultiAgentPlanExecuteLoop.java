package com.researchassistant.orchestrator;

import com.researchassistant.evidence.ProjectEvidenceScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MultiAgentPlanExecuteLoop {

    private static final String PENDING = "pending";

    private final DeepResearchAgent deepResearchAgent;
    private final EvidenceAuditAgent evidenceAuditAgent;
    private final DocumentComposerAgent documentComposerAgent;

    public MultiAgentPlanExecuteLoop(
            DeepResearchAgent deepResearchAgent,
            EvidenceAuditAgent evidenceAuditAgent,
            DocumentComposerAgent documentComposerAgent
    ) {
        this.deepResearchAgent = Objects.requireNonNull(deepResearchAgent, "deepResearchAgent");
        this.evidenceAuditAgent = Objects.requireNonNull(evidenceAuditAgent, "evidenceAuditAgent");
        this.documentComposerAgent = Objects.requireNonNull(documentComposerAgent, "documentComposerAgent");
    }

    public MultiAgentPlanExecuteResult run(
            long sessionId,
            String question,
            ProjectEvidenceScope evidenceScope,
            boolean allowWebSupplement,
            MultiAgentWorkflowDecision decision
    ) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(decision, "decision");
        if (decision.mode() != MultiAgentExecutionMode.PLAN_EXECUTE) {
            throw new IllegalArgumentException("MultiAgentPlanExecuteLoop requires PLAN_EXECUTE decision");
        }

        List<MultiAgentPlan.Step> steps = createSteps(decision);
        ResearchPacket packet = null;
        AuditVerdict verdict = null;
        DocumentDraft draft = null;

        if (needsResearch(decision)) {
            packet = deepResearchAgent.research(sessionId, question, evidenceScope, allowWebSupplement);
            steps = completeStep(steps, "deep-research");
        }

        if (decision.requiresEvidenceAudit() || decision.requiresDocumentComposer()) {
            ResearchPacket groundedPacket = Objects.requireNonNull(packet, "researchPacket");
            verdict = evidenceAuditAgent.audit(question, deterministicDraft(groundedPacket), groundedPacket);
            steps = completeStep(steps, "evidence-audit");
        }

        if (decision.requiresDocumentComposer()) {
            draft = documentComposerAgent.compose("markdown", question, packet, verdict);
            steps = completeStep(steps, "document-composer");
        }

        MultiAgentPlan plan = new MultiAgentPlan(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "Serial plan-execute workflow: " + decision.reason(),
                steps
        );
        return new MultiAgentPlanExecuteResult(
                plan,
                packet,
                verdict,
                draft,
                finalSynthesisContext(question, packet, verdict, draft)
        );
    }

    private boolean needsResearch(MultiAgentWorkflowDecision decision) {
        return decision.requiresDeepResearch()
                || decision.requiresEvidenceAudit()
                || decision.requiresDocumentComposer();
    }

    private List<MultiAgentPlan.Step> createSteps(MultiAgentWorkflowDecision decision) {
        List<MultiAgentPlan.Step> steps = new ArrayList<>();
        if (needsResearch(decision)) {
            steps.add(new MultiAgentPlan.Step(
                    "deep-research",
                    "Collect and separate grounded evidence",
                    "Deep Research Agent",
                    PENDING
            ));
        }
        if (decision.requiresEvidenceAudit() || decision.requiresDocumentComposer()) {
            steps.add(new MultiAgentPlan.Step(
                    "evidence-audit",
                    "Audit claims against gathered evidence",
                    "Evidence Audit Agent",
                    PENDING
            ));
        }
        if (decision.requiresDocumentComposer()) {
            steps.add(new MultiAgentPlan.Step(
                    "document-composer",
                    "Compose requested document from audited packet",
                    "Document Composer Agent",
                    PENDING
            ));
        }
        return steps;
    }

    private List<MultiAgentPlan.Step> completeStep(List<MultiAgentPlan.Step> steps, String stepId) {
        return steps.stream()
                .map(step -> step.stepId().equals(stepId) ? step.completed() : step)
                .toList();
    }

    private String deterministicDraft(ResearchPacket packet) {
        if (!packet.claims().isEmpty()) {
            return String.join("\n", packet.claims());
        }
        return "";
    }

    private String finalSynthesisContext(
            String question,
            ResearchPacket packet,
            AuditVerdict verdict,
            DocumentDraft draft
    ) {
        StringBuilder context = new StringBuilder("question: ").append(question);
        if (packet != null) {
            appendLine(context, "claims", join(packet.claims()));
            appendLine(context, "paper evidence count", Integer.toString(packet.paperEvidence().size()));
            appendLine(context, "web evidence count", Integer.toString(packet.webEvidence().size()));
            appendLine(context, "evidence gaps", join(packet.evidenceGaps()));
        }
        if (verdict != null) {
            appendLine(context, "audit verdict", verdict.verdict());
            appendLine(context, "recommended answer mode", verdict.recommendedAnswerMode());
            appendLine(context, "unsupported claims", join(verdict.unsupportedClaims()));
            appendLine(context, "source policy issues", join(verdict.sourcePolicyIssues()));
            appendLine(context, "required revisions", join(verdict.requiredRevisions()));
        }
        if (draft != null) {
            appendLine(context, "document format", draft.format());
            appendLine(context, "document title", draft.title());
            appendLine(context, "document sections", draft.sections().stream()
                    .map(DocumentDraft.Section::heading)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none"));
        }
        return context.toString();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        builder.append("\n").append(label).append(": ").append(value);
    }

    private String join(List<String> values) {
        return values.isEmpty() ? "none" : String.join("; ", values);
    }
}
