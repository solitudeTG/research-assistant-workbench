package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
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
        if (draft != null) {
            return draft.body();
        }
        if (verdict != null && AnswerMode.REFUSAL.name().equals(verdict.recommendedAnswerMode())) {
            return refusalAnswer(packet, verdict);
        }
        if (packet == null) {
            return "I could not produce a supported answer from the Plan-Execute workflow.";
        }
        if (!packet.claims().isEmpty()) {
            return claimsAnswer(packet, verdict);
        }
        if (!packet.paperEvidence().isEmpty() || !packet.webEvidence().isEmpty()) {
            return weakEvidenceAnswer(packet, verdict);
        }
        return noEvidenceAnswer(packet);
    }

    private String join(List<String> values) {
        return values.isEmpty() ? "none" : String.join("; ", values);
    }

    private String claimsAnswer(ResearchPacket packet, AuditVerdict verdict) {
        StringBuilder answer = new StringBuilder(
                "Audited summary based on the available research packet. Treat this as weak evidence unless final citations are shown separately."
        );
        for (String claim : packet.claims()) {
            answer.append("\n- ").append(claim);
        }
        appendCautions(answer, packet, verdict);
        return answer.toString();
    }

    private String weakEvidenceAnswer(ResearchPacket packet, AuditVerdict verdict) {
        StringBuilder answer = new StringBuilder(
                "Based on the audited research context, I can offer only a weak-evidence summary. The workflow did not produce a claim-level conclusion that can be treated as final citation evidence:"
        );
        List<String> evidence = new ArrayList<>();
        evidence.addAll(packet.paperEvidence());
        evidence.addAll(packet.webEvidence());
        evidence.stream()
                .map(this::userFacingEvidence)
                .filter(value -> !value.isBlank())
                .limit(3)
                .forEach(value -> answer.append("\n- ").append(value));
        appendCautions(answer, packet, verdict);
        return answer.toString();
    }

    private String noEvidenceAnswer(ResearchPacket packet) {
        StringBuilder answer = new StringBuilder(
                "I could not find enough paper or web evidence to give a supported Plan-Execute answer."
        );
        if (packet != null && !packet.evidenceGaps().isEmpty()) {
            answer.append("\nEvidence gaps: ").append(join(packet.evidenceGaps()));
        }
        return answer.toString();
    }

    private String refusalAnswer(ResearchPacket packet, AuditVerdict verdict) {
        StringBuilder answer = new StringBuilder(
                "I cannot provide a supported answer from the current evidence."
        );
        if (verdict != null && !verdict.sourcePolicyIssues().isEmpty()) {
            answer.append("\nSource policy issues: ").append(join(verdict.sourcePolicyIssues()));
        }
        if (verdict != null && !verdict.requiredRevisions().isEmpty()) {
            answer.append("\nRequired revisions: ").append(join(verdict.requiredRevisions()));
        }
        if (packet != null && !packet.evidenceGaps().isEmpty()) {
            answer.append("\nEvidence gaps: ").append(join(packet.evidenceGaps()));
        }
        return answer.toString();
    }

    private void appendCautions(StringBuilder answer, ResearchPacket packet, AuditVerdict verdict) {
        if (packet != null && !packet.evidenceGaps().isEmpty()) {
            answer.append("\nEvidence gaps: ").append(join(packet.evidenceGaps()));
        }
        if (verdict != null && !verdict.sourcePolicyIssues().isEmpty()) {
            answer.append("\nSource policy cautions: ").append(join(verdict.sourcePolicyIssues()));
        }
        if (verdict != null && !verdict.requiredRevisions().isEmpty()) {
            answer.append("\nRequired revisions before stronger use: ").append(join(verdict.requiredRevisions()));
        }
    }

    private String userFacingEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "";
        }
        for (String marker : List.of("content=", "snippet=", "summary=")) {
            int index = evidence.indexOf(marker);
            if (index >= 0) {
                return evidence.substring(index + marker.length()).trim();
            }
        }
        return evidence.trim();
    }
}
