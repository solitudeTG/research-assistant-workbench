package com.researchassistant.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ModelBackedMultiAgentWorkflowIntentAdvisor implements MultiAgentWorkflowIntentAdvisor {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ModelBackedMultiAgentWorkflowIntentAdvisor(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Optional<SemanticWorkflowAdvice> advise(
            String question,
            boolean allowWebSupplement,
            MultiAgentWorkflowDecision fallbackDecision
    ) {
        try {
            String response = chatClient.prompt()
                    .system("""
                            You are the semantic workflow router for a research assistant.
                            Choose REACT for simple direct answers and PLAN_EXECUTE for complex research, evidence review, comparison, or requested document deliverables.
                            Do not infer hidden tools. Return strict JSON only with:
                            {"mode":"REACT|PLAN_EXECUTE","reason":"short_snake_case","requiresDeepResearch":false,"requiresEvidenceAudit":false,"requiresDocumentComposer":false,"confidence":0.0}
                            """)
                    .user(prompt(question, allowWebSupplement, fallbackDecision))
                    .call()
                    .content();
            return parse(response);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String prompt(String question, boolean allowWebSupplement, MultiAgentWorkflowDecision fallbackDecision) {
        return "Question:\n" + (question == null ? "" : question)
                + "\n\nWeb supplement allowed: " + allowWebSupplement
                + "\n\nDeterministic fallback:"
                + "\nmode=" + fallbackDecision.mode()
                + "\nreason=" + fallbackDecision.reason()
                + "\nrequiresDeepResearch=" + fallbackDecision.requiresDeepResearch()
                + "\nrequiresEvidenceAudit=" + fallbackDecision.requiresEvidenceAudit()
                + "\nrequiresDocumentComposer=" + fallbackDecision.requiresDocumentComposer();
    }

    private Optional<SemanticWorkflowAdvice> parse(String response) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response == null ? "" : response);
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
        MultiAgentExecutionMode mode = parseMode(root.path("mode").asText(""));
        if (mode == null) {
            return Optional.empty();
        }
        return Optional.of(new SemanticWorkflowAdvice(
                mode,
                root.path("reason").asText("semantic_intent"),
                root.path("requiresDeepResearch").asBoolean(false),
                root.path("requiresEvidenceAudit").asBoolean(false),
                root.path("requiresDocumentComposer").asBoolean(false),
                root.path("confidence").asDouble(0.0)
        ));
    }

    private MultiAgentExecutionMode parseMode(String value) {
        try {
            return MultiAgentExecutionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
