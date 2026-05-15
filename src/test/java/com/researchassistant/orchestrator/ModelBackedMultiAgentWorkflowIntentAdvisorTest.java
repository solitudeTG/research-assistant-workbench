package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelBackedMultiAgentWorkflowIntentAdvisorTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private final MultiAgentWorkflowDecision fallbackDecision = new MultiAgentWorkflowDecision(
            MultiAgentExecutionMode.REACT,
            "simple_react_request",
            false,
            false,
            false
    );

    @Test
    void parsesStrictSemanticWorkflowJson() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        {
                          "mode": "PLAN_EXECUTE",
                          "reason": "semantic_document_research",
                          "requiresDeepResearch": true,
                          "requiresEvidenceAudit": true,
                          "requiresDocumentComposer": true,
                          "confidence": 0.91
                        }
                        """);

        ModelBackedMultiAgentWorkflowIntentAdvisor advisor = new ModelBackedMultiAgentWorkflowIntentAdvisor(
                chatClient,
                new ObjectMapper()
        );

        Optional<SemanticWorkflowAdvice> advice = advisor.advise("prepare a markdown report", true, fallbackDecision);

        assertThat(advice).isPresent();
        assertThat(advice.get().mode()).isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
        assertThat(advice.get().reason()).isEqualTo("semantic_document_research");
        assertThat(advice.get().requiresDeepResearch()).isTrue();
        assertThat(advice.get().requiresEvidenceAudit()).isTrue();
        assertThat(advice.get().requiresDocumentComposer()).isTrue();
        assertThat(advice.get().confidence()).isEqualTo(0.91);
    }

    @Test
    void invalidModelOutputReturnsEmptyAdviceForFallback() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("not-json");

        ModelBackedMultiAgentWorkflowIntentAdvisor advisor = new ModelBackedMultiAgentWorkflowIntentAdvisor(
                chatClient,
                new ObjectMapper()
        );

        assertThat(advisor.advise("hello", false, fallbackDecision)).isEmpty();
    }

    @Test
    void unknownModeReturnsEmptyAdviceForFallback() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        {
                          "mode": "PARALLEL_SWARM",
                          "reason": "unknown_mode",
                          "confidence": 0.99
                        }
                        """);

        ModelBackedMultiAgentWorkflowIntentAdvisor advisor = new ModelBackedMultiAgentWorkflowIntentAdvisor(
                chatClient,
                new ObjectMapper()
        );

        assertThat(advisor.advise("hello", false, fallbackDecision)).isEmpty();
    }

    @Test
    void chatClientFailureReturnsEmptyAdviceForFallback() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new IllegalStateException("provider unavailable"));

        ModelBackedMultiAgentWorkflowIntentAdvisor advisor = new ModelBackedMultiAgentWorkflowIntentAdvisor(
                chatClient,
                new ObjectMapper()
        );

        assertThat(advisor.advise("hello", false, fallbackDecision)).isEmpty();
    }
}
