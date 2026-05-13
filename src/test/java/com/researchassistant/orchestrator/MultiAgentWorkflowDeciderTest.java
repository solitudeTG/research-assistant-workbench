package com.researchassistant.orchestrator;

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
}
