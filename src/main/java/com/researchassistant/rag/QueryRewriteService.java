package com.researchassistant.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class QueryRewriteService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public QueryRewriteService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public QueryRewritePlan rewrite(String question) {
        if (question == null || question.isBlank()) {
            return QueryRewritePlan.originalOnly(question);
        }

        if (!containsCjk(question)) {
            return QueryRewritePlan.originalOnly(question);
        }

        try {
            String content = chatClient.prompt()
                    .system("""
                            Rewrite the user's research question for retrieval.
                            Return JSON only with:
                            {
                              "englishQuestion": "...",
                              "keywords": ["...", "..."]
                            }
                            Keep keywords short, concrete, and paper-domain-friendly.
                            """)
                    .user(question)
                    .call()
                    .content();

            JsonNode node = objectMapper.readTree(extractJson(content));
            String englishQuestion = textValue(node.get("englishQuestion"));
            List<String> keywords = listValue(node.get("keywords"));
            return QueryRewritePlan.from(question, englishQuestion, keywords);
        } catch (Exception ignored) {
            return QueryRewritePlan.originalOnly(question);
        }
    }

    private boolean containsCjk(String question) {
        return question.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private List<String> listValue(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
