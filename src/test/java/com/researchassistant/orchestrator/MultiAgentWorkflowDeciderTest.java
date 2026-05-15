package com.researchassistant.orchestrator;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAgentWorkflowDeciderTest {

    private final MultiAgentWorkflowDecider decider = new MultiAgentWorkflowDecider();

    @Test
    void simpleQuestionUsesReact() {
        assertThat(decider.decide("你好", false).mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decider.decide("这篇论文的标题是什么", false).mode()).isEqualTo(MultiAgentExecutionMode.REACT);
    }

    @Test
    void complexResearchUsesPlanExecute() {
        MultiAgentWorkflowDecision decision = decider.decide(
                "对比这几篇论文关于多 Agent 协作架构的观点，并给出可引用结论",
                true
        );

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
        assertThat(decision.requiresDeepResearch()).isTrue();
        assertThat(decision.requiresEvidenceAudit()).isTrue();
        assertThat(decision.requiresDocumentComposer()).isFalse();
    }

    @Test
    void documentRequestsUsePlanExecute() {
        MultiAgentWorkflowDecision decision = decider.decide("基于资料生成一份 Markdown 综述报告", true);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
        assertThat(decision.requiresDocumentComposer()).isTrue();
    }

    @Test
    void outputOneMarkdownReportUsesDocumentComposer() {
        MultiAgentWorkflowDecision decision = decider.decide(
                "请对近邻星干涉相关论文做系统分析和对比，判断当前研究路线是否成立，并输出一份 markdown 研究报告，要求说明证据不足之处。",
                true
        );

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
        assertThat(decision.requiresDeepResearch()).isTrue();
        assertThat(decision.requiresEvidenceAudit()).isTrue();
        assertThat(decision.requiresDocumentComposer()).isTrue();
    }

    @Test
    void explicitReviewWritingRequestUsesPlanExecute() {
        MultiAgentWorkflowDecision decision = decider.decide("请基于这些论文写一篇综述", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
        assertThat(decision.requiresDocumentComposer()).isTrue();
    }

    @Test
    void explicitReportWritingRequestUsesPlanExecute() {
        MultiAgentWorkflowDecision decision = decider.decide("帮我写个报告", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
        assertThat(decision.requiresDocumentComposer()).isTrue();
    }

    @Test
    void bareFormatQuestionUsesReact() {
        MultiAgentWorkflowDecision decision = decider.decide("Markdown 语法是什么", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.requiresDocumentComposer()).isFalse();
    }

    @Test
    void bareDocumentQuestionUsesReact() {
        MultiAgentWorkflowDecision decision = decider.decide("这份文档的标题是什么", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.requiresDocumentComposer()).isFalse();
    }

    @Test
    void documentReadingQuestionWithWriteVerbUsesReact() {
        MultiAgentWorkflowDecision decision = decider.decide("这份文档写了什么", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.requiresDocumentComposer()).isFalse();
    }

    @Test
    void documentReadingConclusionQuestionUsesReact() {
        MultiAgentWorkflowDecision decision = decider.decide("这份文档里写了哪些结论", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.requiresDocumentComposer()).isFalse();
    }

    @Test
    void textGenerationMethodQuestionUsesReact() {
        MultiAgentWorkflowDecision decision = decider.decide("基于文档解释文本生成方法是什么", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.requiresDocumentComposer()).isFalse();
    }

    @Test
    void outputDiagnosticQuestionUsesReact() {
        MultiAgentWorkflowDecision decision = decider.decide("这份文档输出为什么为空", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.requiresDocumentComposer()).isFalse();
    }
    @Test
    void semanticAdvisorCanPromoteNaturalDocumentResearchRequestWithoutKeywordRules() {
        MultiAgentWorkflowDecider semanticDecider = new MultiAgentWorkflowDecider(
                (question, allowWebSupplement, fallbackDecision) -> Optional.of(new SemanticWorkflowAdvice(
                        MultiAgentExecutionMode.PLAN_EXECUTE,
                        "semantic_document_research",
                        true,
                        true,
                        true,
                        0.92
                ))
        );

        MultiAgentWorkflowDecision decision = semanticDecider.decide(
                "Please evaluate whether this line of work holds and prepare a polished markdown deliverable with evidence caveats.",
                true
        );

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
        assertThat(decision.requiresDeepResearch()).isTrue();
        assertThat(decision.requiresEvidenceAudit()).isTrue();
        assertThat(decision.requiresDocumentComposer()).isTrue();
        assertThat(decision.reason()).isEqualTo("semantic_document_research");
        assertThat(decision.decisionSource()).isEqualTo("semantic");
        assertThat(decision.fallbackReason()).isEqualTo("simple_react_request");
        assertThat(decision.semanticConfidence()).isEqualTo(0.92);
    }

    @Test
    void semanticAdvisorCanKeepSimpleRequestOnReact() {
        MultiAgentWorkflowDecider semanticDecider = new MultiAgentWorkflowDecider(
                (question, allowWebSupplement, fallbackDecision) -> Optional.of(new SemanticWorkflowAdvice(
                        MultiAgentExecutionMode.REACT,
                        "semantic_simple_chat",
                        false,
                        false,
                        false,
                        0.88
                ))
        );

        MultiAgentWorkflowDecision decision = semanticDecider.decide("hello", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.requiresDeepResearch()).isFalse();
        assertThat(decision.requiresEvidenceAudit()).isFalse();
        assertThat(decision.requiresDocumentComposer()).isFalse();
        assertThat(decision.decisionSource()).isEqualTo("semantic");
    }

    @Test
    void unavailableSemanticAdvisorFallsBackToDeterministicDecision() {
        MultiAgentWorkflowDecider semanticDecider = new MultiAgentWorkflowDecider(
                (question, allowWebSupplement, fallbackDecision) -> Optional.empty()
        );

        MultiAgentWorkflowDecision decision = semanticDecider.decide("hello", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.reason()).isEqualTo("simple_react_request");
        assertThat(decision.decisionSource()).isEqualTo("fallback");
        assertThat(decision.fallbackReason()).isEqualTo("simple_react_request");
        assertThat(decision.semanticConfidence()).isZero();
    }

    @Test
    void lowConfidenceSemanticAdvisorFallsBackToDeterministicDecision() {
        MultiAgentWorkflowDecider semanticDecider = new MultiAgentWorkflowDecider(
                (question, allowWebSupplement, fallbackDecision) -> Optional.of(new SemanticWorkflowAdvice(
                        MultiAgentExecutionMode.PLAN_EXECUTE,
                        "low_confidence_plan",
                        true,
                        true,
                        true,
                        0.50
                ))
        );

        MultiAgentWorkflowDecision decision = semanticDecider.decide("hello", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.decisionSource()).isEqualTo("fallback");
        assertThat(decision.fallbackReason()).isEqualTo("simple_react_request");
    }

    @Test
    void nonFiniteSemanticConfidenceFallsBackToDeterministicDecision() {
        MultiAgentWorkflowDecider semanticDecider = new MultiAgentWorkflowDecider(
                (question, allowWebSupplement, fallbackDecision) -> Optional.of(new SemanticWorkflowAdvice(
                        MultiAgentExecutionMode.PLAN_EXECUTE,
                        "nan_confidence_plan",
                        true,
                        true,
                        true,
                        Double.NaN
                ))
        );

        MultiAgentWorkflowDecision decision = semanticDecider.decide("hello", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.decisionSource()).isEqualTo("fallback");
    }

    @Test
    void outOfRangeSemanticConfidenceFallsBackToDeterministicDecision() {
        MultiAgentWorkflowDecider semanticDecider = new MultiAgentWorkflowDecider(
                (question, allowWebSupplement, fallbackDecision) -> Optional.of(new SemanticWorkflowAdvice(
                        MultiAgentExecutionMode.PLAN_EXECUTE,
                        "overconfident_plan",
                        true,
                        true,
                        true,
                        2.0
                ))
        );

        MultiAgentWorkflowDecision decision = semanticDecider.decide("hello", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.decisionSource()).isEqualTo("fallback");
    }

    @Test
    void inconsistentSemanticAdviceFallsBackToDeterministicDecision() {
        MultiAgentWorkflowDecider semanticDecider = new MultiAgentWorkflowDecider(
                (question, allowWebSupplement, fallbackDecision) -> Optional.of(new SemanticWorkflowAdvice(
                        MultiAgentExecutionMode.PLAN_EXECUTE,
                        "plan_without_subagent_boundary",
                        false,
                        false,
                        false,
                        0.90
                ))
        );

        MultiAgentWorkflowDecision decision = semanticDecider.decide("hello", false);

        assertThat(decision.mode()).isEqualTo(MultiAgentExecutionMode.REACT);
        assertThat(decision.decisionSource()).isEqualTo("fallback");
    }
}
