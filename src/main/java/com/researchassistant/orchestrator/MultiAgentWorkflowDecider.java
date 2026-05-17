package com.researchassistant.orchestrator;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MultiAgentWorkflowDecider {

    private static final double SEMANTIC_CONFIDENCE_THRESHOLD = 0.75;

    private final MultiAgentWorkflowIntentAdvisor intentAdvisor;

    public MultiAgentWorkflowDecider() {
        this((question, allowWebSupplement, fallbackDecision) -> java.util.Optional.empty());
    }

    @Autowired
    public MultiAgentWorkflowDecider(MultiAgentWorkflowIntentAdvisor intentAdvisor) {
        this.intentAdvisor = intentAdvisor;
    }

    public MultiAgentWorkflowDecision decide(String question, boolean allowWebSupplement) {
        String normalized = normalize(question);

        boolean documentOutputIntent = containsAny(normalized,
                "生成一份",
                "生成报告",
                "生成综述",
                "写一篇",
                "写个",
                "写一份",
                "写成",
                "整理成",
                "做成",
                "输出一份",
                "输出一个",
                "输出成",
                "撰写")
                || containsSafeOutputAs(normalized);
        boolean documentProduct = containsAny(normalized,
                "报告",
                "综述",
                "markdown",
                "文档",
                "对比表",
                "表格",
                "论文笔记");
        boolean documentRequest = documentOutputIntent && documentProduct;
        boolean complexResearch = containsAny(normalized,
                "对比",
                "比较",
                "研究路线",
                "系统分析",
                "可引用",
                "判断是否成立",
                "审查",
                "严谨",
                "多 agent",
                "多agent",
                "协作架构");
        boolean multiSource = containsAny(normalized,
                "几篇",
                "多篇",
                "多个来源",
                "多份",
                "这些论文",
                "这几篇",
                "资料");
        boolean externalSupplement = allowWebSupplement && containsAny(normalized,
                "联网",
                "web",
                "最新",
                "近期",
                "当前");

        boolean planExecute = documentRequest || complexResearch || (multiSource && externalSupplement);

        MultiAgentWorkflowDecision fallback = new MultiAgentWorkflowDecision(
                planExecute ? MultiAgentExecutionMode.PLAN_EXECUTE : MultiAgentExecutionMode.REACT,
                reason(documentRequest, complexResearch, multiSource, externalSupplement),
                planExecute,
                planExecute,
                documentRequest
        );
        return intentAdvisor.advise(question, allowWebSupplement, fallback)
                .filter(advice -> shouldUseSemanticAdvice(advice))
                .map(advice -> fromSemanticAdvice(advice, fallback))
                .orElse(fallback);
    }

    private boolean shouldUseSemanticAdvice(SemanticWorkflowAdvice advice) {
        if (advice == null || advice.mode() == null) {
            return false;
        }
        if (!Double.isFinite(advice.confidence())
                || advice.confidence() < SEMANTIC_CONFIDENCE_THRESHOLD
                || advice.confidence() > 1.0) {
            return false;
        }
        if (advice.mode() == MultiAgentExecutionMode.REACT) {
            return !advice.requiresDeepResearch()
                    && !advice.requiresEvidenceAudit()
                    && !advice.requiresDocumentComposer();
        }
        return advice.requiresDeepResearch()
                || advice.requiresEvidenceAudit()
                || advice.requiresDocumentComposer();
    }

    private MultiAgentWorkflowDecision fromSemanticAdvice(
            SemanticWorkflowAdvice advice,
            MultiAgentWorkflowDecision fallback
    ) {
        return new MultiAgentWorkflowDecision(
                advice.mode(),
                advice.reason(),
                advice.requiresDeepResearch(),
                advice.requiresEvidenceAudit(),
                advice.requiresDocumentComposer(),
                "semantic",
                fallback.reason(),
                advice.confidence()
        );
    }

    private String reason(
            boolean documentRequest,
            boolean complexResearch,
            boolean multiSource,
            boolean externalSupplement
    ) {
        if (documentRequest) {
            return "document_composition_request";
        }
        if (complexResearch) {
            return "complex_research_request";
        }
        if (multiSource && externalSupplement) {
            return "multi_source_external_research_request";
        }
        return "simple_react_request";
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSafeOutputAs(String text) {
        return text.contains("输出为")
                && !containsAny(text, "输出为什么", "输出为何", "输出为啥");
    }

    private String normalize(String question) {
        return question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
    }
}
