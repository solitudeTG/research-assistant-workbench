package com.researchassistant.orchestrator;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AgentIntentRouter {

    public AgentRoutingDecision route(String question, boolean allowWebSupplement) {
        String normalized = normalize(question);

        if (containsAny(normalized,
                "计划",
                "方案",
                "路线",
                "研究计划",
                "璁″垝",
                "鏂规",
                "璺嚎",
                "plan",
                "roadmap",
                "step by step")) {
            return new AgentRoutingDecision(
                    AgentIntent.PLANNING,
                    true,
                    false,
                    false,
                    "planning request"
            );
        }

        if (containsAny(normalized,
                "我们之前讨论",
                "之前讨论",
                "历史记忆",
                "鎴戜滑涔嬪墠璁ㄨ",
                "涔嬪墠璁ㄨ",
                "鍘嗗彶璁板繂",
                "previous discussion",
                "what did we discuss",
                "discussed before",
                "history")) {
            return new AgentRoutingDecision(
                    AgentIntent.MEMORY_RECALL,
                    true,
                    false,
                    false,
                    "historical memory requested"
            );
        }

        boolean local = containsAny(normalized,
                "论文",
                "项目资料",
                "本地资料",
                "璁烘枃",
                "椤圭洰璧勬枡",
                "鏈湴璧勬枡",
                "paper",
                "source");
        boolean projectResearch = containsAny(normalized,
                "方法",
                "证据",
                "主张",
                "支持",
                "结果",
                "发现",
                "局限",
                "贡献",
                "method",
                "evidence",
                "claim",
                "imply",
                "support",
                "result",
                "finding",
                "limitation",
                "contribution",
                "approach");
        boolean explicitWeb = containsAny(normalized, "联网", "搜索", "鑱旂綉", "鎼滅储", "search");
        boolean freshnessWeb = containsAny(normalized,
                "最新",
                "现在",
                "近期",
                "鏈€鏂?",
                "鐜板湪",
                "杩戞湡",
                "latest",
                "today",
                "current");
        boolean web = explicitWeb || (allowWebSupplement && freshnessWeb);

        if ((local || projectResearch) && web) {
            return new AgentRoutingDecision(
                    AgentIntent.PROJECT_RAG_WITH_WEB,
                    true,
                    true,
                    true,
                    "local evidence plus external freshness"
            );
        }

        if (web) {
            return new AgentRoutingDecision(
                    AgentIntent.WEB_SEARCH,
                    false,
                    false,
                    true,
                    "external or fresh information requested"
            );
        }

        if (local || projectResearch) {
            return new AgentRoutingDecision(
                    AgentIntent.PROJECT_RAG,
                    false,
                    true,
                    false,
                    local ? "local project evidence requested" : "project research question"
            );
        }

        if (isGreetingOrSimpleChat(normalized)) {
            return new AgentRoutingDecision(
                    AgentIntent.SIMPLE_CHAT,
                    false,
                    false,
                    false,
                    "simple conversational input"
            );
        }

        return new AgentRoutingDecision(
                AgentIntent.SIMPLE_CHAT,
                false,
                false,
                false,
                "no retrieval signal"
        );
    }

    private boolean isGreetingOrSimpleChat(String normalized) {
        return normalized.equals("你好")
                || normalized.equals("您好")
                || normalized.equals("浣犲ソ")
                || normalized.equals("鎮ㄥソ")
                || normalized.equals("hi")
                || normalized.equals("hello")
                || normalized.equals("hey")
                || normalized.contains("浣犳槸璋?")
                || normalized.contains("浣犺兘鍋氫粈涔?")
                || normalized.contains("who are you")
                || normalized.contains("what can you do");
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String question) {
        return question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
    }
}
