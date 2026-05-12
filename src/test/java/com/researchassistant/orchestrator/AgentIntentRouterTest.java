package com.researchassistant.orchestrator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIntentRouterTest {

    private final AgentIntentRouter router = new AgentIntentRouter();

    @Test
    void greetingRoutesToSimpleChat() {
        AgentRoutingDecision decision = router.route("你好", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.SIMPLE_CHAT);
        assertThat(decision.needsPaperRag()).isFalse();
        assertThat(decision.needsWebSearch()).isFalse();
        assertThat(decision.needsMemoryRecall()).isFalse();
    }

    @Test
    void explicitWebRequestRoutesToWebSearch() {
        AgentRoutingDecision decision = router.route("联网查一下 Tavily API 怎么接入", true);

        assertThat(decision.intent()).isEqualTo(AgentIntent.WEB_SEARCH);
        assertThat(decision.needsPaperRag()).isFalse();
        assertThat(decision.needsWebSearch()).isTrue();
    }

    @Test
    void localPaperQuestionRoutesToProjectRag() {
        AgentRoutingDecision decision = router.route("这篇论文的核心方法是什么？", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PROJECT_RAG);
        assertThat(decision.needsMemoryRecall()).isFalse();
        assertThat(decision.needsPaperRag()).isTrue();
        assertThat(decision.needsWebSearch()).isFalse();
    }

    @Test
    void naturalResearchMethodQuestionRoutesToProjectRagWithoutMemoryRecall() {
        AgentRoutingDecision decision = router.route("What does the method imply?", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PROJECT_RAG);
        assertThat(decision.needsMemoryRecall()).isFalse();
        assertThat(decision.needsPaperRag()).isTrue();
        assertThat(decision.needsWebSearch()).isFalse();
    }

    @Test
    void naturalEvidenceQuestionRoutesToProjectRagWithoutMemoryRecall() {
        AgentRoutingDecision decision = router.route("What evidence supports the claim?", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PROJECT_RAG);
        assertThat(decision.needsMemoryRecall()).isFalse();
        assertThat(decision.needsPaperRag()).isTrue();
        assertThat(decision.needsWebSearch()).isFalse();
    }

    @Test
    void localPaperLatestComparisonRoutesToProjectRagWithWeb() {
        AgentRoutingDecision decision = router.route("这篇论文的方法和最新工作相比有什么不足？", true);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PROJECT_RAG_WITH_WEB);
        assertThat(decision.needsPaperRag()).isTrue();
        assertThat(decision.needsWebSearch()).isTrue();
    }

    @Test
    void naturalFreshResearchQuestionRoutesToProjectRagWithWebWhenAllowed() {
        AgentRoutingDecision decision = router.route("What are the latest findings?", true);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PROJECT_RAG_WITH_WEB);
        assertThat(decision.needsMemoryRecall()).isTrue();
        assertThat(decision.needsPaperRag()).isTrue();
        assertThat(decision.needsWebSearch()).isTrue();
    }

    @Test
    void naturalFreshResearchQuestionRoutesToProjectRagWhenWebNotAllowed() {
        AgentRoutingDecision decision = router.route("What are the latest findings?", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PROJECT_RAG);
        assertThat(decision.needsMemoryRecall()).isFalse();
        assertThat(decision.needsPaperRag()).isTrue();
        assertThat(decision.needsWebSearch()).isFalse();
    }

    @Test
    void localPaperLatestComparisonWithoutWebPermissionRoutesToProjectRagOnly() {
        AgentRoutingDecision decision = router.route("paper latest comparison", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PROJECT_RAG);
        assertThat(decision.needsPaperRag()).isTrue();
        assertThat(decision.needsWebSearch()).isFalse();
    }

    @Test
    void explicitWebRequestIgnoresWebSupplementPermission() {
        AgentRoutingDecision decision = router.route("search Tavily API docs", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.WEB_SEARCH);
        assertThat(decision.needsPaperRag()).isFalse();
        assertThat(decision.needsWebSearch()).isTrue();
    }

    @Test
    void historyQuestionRoutesToMemoryRecall() {
        AgentRoutingDecision decision = router.route("\u6211\u4eec\u4e4b\u524d\u8ba8\u8bba\u8fc7\u4ec0\u4e48?", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.MEMORY_RECALL);
        assertThat(decision.needsMemoryRecall()).isTrue();
        assertThat(decision.needsPaperRag()).isFalse();
        assertThat(decision.needsWebSearch()).isFalse();
    }

    @Test
    void planningQuestionRoutesToPlanning() {
        AgentRoutingDecision decision = router.route("帮我制定一个研究计划", false);

        assertThat(decision.intent()).isEqualTo(AgentIntent.PLANNING);
        assertThat(decision.needsMemoryRecall()).isTrue();
        assertThat(decision.needsPaperRag()).isFalse();
        assertThat(decision.needsWebSearch()).isFalse();
    }
}
