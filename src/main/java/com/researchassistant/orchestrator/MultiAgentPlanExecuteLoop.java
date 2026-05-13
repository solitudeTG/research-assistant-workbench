package com.researchassistant.orchestrator;

import com.researchassistant.evidence.ProjectEvidenceScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MultiAgentPlanExecuteLoop {

    private static final String PENDING = "pending";

    private final DeepResearchAgent deepResearchAgent;
    private final EvidenceAuditAgent evidenceAuditAgent;
    private final DocumentComposerAgent documentComposerAgent;
    private final AgentTracePublisher tracePublisher;

    public MultiAgentPlanExecuteLoop(
            DeepResearchAgent deepResearchAgent,
            EvidenceAuditAgent evidenceAuditAgent,
            DocumentComposerAgent documentComposerAgent,
            AgentTracePublisher tracePublisher
    ) {
        this.deepResearchAgent = Objects.requireNonNull(deepResearchAgent, "deepResearchAgent");
        this.evidenceAuditAgent = Objects.requireNonNull(evidenceAuditAgent, "evidenceAuditAgent");
        this.documentComposerAgent = Objects.requireNonNull(documentComposerAgent, "documentComposerAgent");
        this.tracePublisher = Objects.requireNonNull(tracePublisher, "tracePublisher");
    }

    public MultiAgentPlanExecuteResult run(
            long sessionId,
            String question,
            ProjectEvidenceScope evidenceScope,
            boolean allowWebSupplement,
            MultiAgentWorkflowDecision decision
    ) {
        return run(sessionId, question, evidenceScope, allowWebSupplement, decision, null);
    }

    public MultiAgentPlanExecuteResult run(
            long sessionId,
            String question,
            ProjectEvidenceScope evidenceScope,
            boolean allowWebSupplement,
            MultiAgentWorkflowDecision decision,
            AgentTraceContext traceContext
    ) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(decision, "decision");
        if (decision.mode() != MultiAgentExecutionMode.PLAN_EXECUTE) {
            throw new IllegalArgumentException("MultiAgentPlanExecuteLoop requires PLAN_EXECUTE decision");
        }

        List<MultiAgentPlan.Step> steps = createSteps(decision);
        publishPlanCreated(traceContext, decision, steps);
        ResearchPacket packet = null;
        AuditVerdict verdict = null;
        DocumentDraft draft = null;

        if (needsResearch(decision)) {
            MultiAgentPlan.Step step = stepById(steps, "deep-research");
            publishStarted(traceContext, step);
            try {
                packet = deepResearchAgent.research(sessionId, question, evidenceScope, allowWebSupplement);
            } catch (RuntimeException exception) {
                publishFailed(traceContext, step, exception);
                throw exception;
            }
            steps = completeStep(steps, "deep-research");
            publishCompleted(traceContext, stepById(steps, "deep-research"), researchPacketTraceData(packet));
        }

        if (decision.requiresEvidenceAudit() || decision.requiresDocumentComposer()) {
            ResearchPacket groundedPacket = Objects.requireNonNull(packet, "researchPacket");
            MultiAgentPlan.Step step = stepById(steps, "evidence-audit");
            publishStarted(traceContext, step);
            try {
                verdict = evidenceAuditAgent.audit(question, deterministicDraft(groundedPacket), groundedPacket);
            } catch (RuntimeException exception) {
                publishFailed(traceContext, step, exception);
                throw exception;
            }
            steps = completeStep(steps, "evidence-audit");
            publishAuditVerdict(traceContext, verdict);
        }

        if (decision.requiresDocumentComposer()) {
            MultiAgentPlan.Step step = stepById(steps, "document-composer");
            publishStarted(traceContext, step);
            try {
                draft = documentComposerAgent.compose("markdown", question, packet, verdict);
            } catch (RuntimeException exception) {
                publishFailed(traceContext, step, exception);
                throw exception;
            }
            steps = completeStep(steps, "document-composer");
            publishComposerCompleted(traceContext, draft);
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

    private void publishPlanCreated(
            AgentTraceContext traceContext,
            MultiAgentWorkflowDecision decision,
            List<MultiAgentPlan.Step> steps
    ) {
        if (traceContext == null) {
            return;
        }
        tracePublisher.planCreated(traceContext, new MultiAgentPlan(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "Serial plan-execute workflow: " + decision.reason(),
                steps
        ));
    }

    private void publishStarted(AgentTraceContext traceContext, MultiAgentPlan.Step step) {
        if (traceContext != null) {
            tracePublisher.subagentStarted(traceContext, step);
        }
    }

    private void publishCompleted(
            AgentTraceContext traceContext,
            MultiAgentPlan.Step step,
            Map<String, Object> data
    ) {
        if (traceContext != null) {
            tracePublisher.subagentCompleted(traceContext, step, data);
        }
    }

    private void publishAuditVerdict(AgentTraceContext traceContext, AuditVerdict verdict) {
        if (traceContext != null) {
            tracePublisher.auditVerdict(traceContext, verdict);
        }
    }

    private void publishComposerCompleted(AgentTraceContext traceContext, DocumentDraft draft) {
        if (traceContext != null) {
            tracePublisher.composerCompleted(traceContext, draft);
        }
    }

    private void publishFailed(
            AgentTraceContext traceContext,
            MultiAgentPlan.Step step,
            RuntimeException exception
    ) {
        if (traceContext != null) {
            tracePublisher.subagentFailed(traceContext, step, safeError(exception));
        }
    }

    private String safeError(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
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

    private MultiAgentPlan.Step stepById(List<MultiAgentPlan.Step> steps, String stepId) {
        return steps.stream()
                .filter(step -> step.stepId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing plan step: " + stepId));
    }

    private Map<String, Object> researchPacketTraceData(ResearchPacket packet) {
        if (packet == null) {
            return Map.of(
                    "claimCount", 0,
                    "paperEvidenceCount", 0,
                    "webEvidenceCount", 0,
                    "memoryContextCount", 0,
                    "conflictCount", 0,
                    "evidenceGapCount", 0
            );
        }
        return Map.of(
                "claimCount", packet.claims().size(),
                "paperEvidenceCount", packet.paperEvidence().size(),
                "webEvidenceCount", packet.webEvidence().size(),
                "memoryContextCount", packet.memoryContext().size(),
                "conflictCount", packet.conflicts().size(),
                "evidenceGapCount", packet.evidenceGaps().size(),
                "recommendedAnswerMode", packet.recommendedAnswerMode()
        );
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
