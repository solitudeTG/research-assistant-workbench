package com.researchassistant.orchestrator;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MultiAgentWorkflowDecider {

    public MultiAgentWorkflowDecision decide(String question, boolean allowWebSupplement) {
        String normalized = normalize(question);

        boolean documentRequest = containsAny(normalized,
                "报告",
                "综述",
                "markdown",
                "文档",
                "对比表",
                "论文笔记",
                "生成一份",
                "写一份",
                "整理成",
                "输出为");
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

        return new MultiAgentWorkflowDecision(
                planExecute ? MultiAgentExecutionMode.PLAN_EXECUTE : MultiAgentExecutionMode.REACT,
                reason(documentRequest, complexResearch, multiSource, externalSupplement),
                planExecute && !documentRequest,
                planExecute,
                documentRequest
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

    private String normalize(String question) {
        return question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
    }
}
