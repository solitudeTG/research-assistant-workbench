package com.researchassistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Test
    void rewriteReturnsEnglishAndKeywordQueriesForChineseQuestion() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        {
                          "englishQuestion": "What problem does this paper study?",
                          "keywords": ["satellite selection", "beamforming"]
                        }
                        """);

        QueryRewriteService service = new QueryRewriteService(chatClient, new ObjectMapper());

        QueryRewritePlan plan = service.rewrite("这篇论文研究了什么？");

        assertThat(plan.originalQuestion()).isEqualTo("这篇论文研究了什么？");
        assertThat(plan.retrievalQueries())
                .contains("这篇论文研究了什么？", "What problem does this paper study?", "satellite selection beamforming");
        assertThat(plan.keywords()).containsExactly("satellite selection", "beamforming");
        assertThat(plan.strategy()).isEqualTo("cjk_llm_rewrite");
        assertThat(plan.fallbackReason()).isNull();
    }

    @Test
    void rewriteFallsBackToOriginalQuestionWhenModelOutputIsInvalid() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("not-json");

        QueryRewriteService service = new QueryRewriteService(chatClient, new ObjectMapper());

        QueryRewritePlan plan = service.rewrite("这篇论文研究了什么？");

        assertThat(plan.retrievalQueries()).containsExactly("这篇论文研究了什么？");
        assertThat(plan.keywords()).isEmpty();
        assertThat(plan.strategy()).isEqualTo("original_only");
        assertThat(plan.fallbackReason()).isEqualTo("rewrite_failed");
    }

    @Test
    void rewriteUsesOriginalOnlyForNonCjkQuestion() {
        QueryRewriteService service = new QueryRewriteService(chatClient, new ObjectMapper());

        QueryRewritePlan plan = service.rewrite("How does attention help sequence modeling?");

        assertThat(plan.retrievalQueries()).containsExactly("How does attention help sequence modeling?");
        assertThat(plan.keywords()).isEmpty();
        assertThat(plan.strategy()).isEqualTo("original_only");
        assertThat(plan.fallbackReason()).isNull();
    }

    @Test
    void rewriteUsesNoRetrievalQueriesForBlankQuestion() {
        QueryRewriteService service = new QueryRewriteService(chatClient, new ObjectMapper());

        QueryRewritePlan plan = service.rewrite("   ");

        assertThat(plan.retrievalQueries()).isEmpty();
        assertThat(plan.keywords()).isEmpty();
        assertThat(plan.strategy()).isEqualTo("original_only");
        assertThat(plan.fallbackReason()).isNull();
    }
}
