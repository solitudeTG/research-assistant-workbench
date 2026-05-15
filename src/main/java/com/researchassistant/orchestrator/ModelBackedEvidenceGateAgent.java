package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ModelBackedEvidenceGateAgent implements EvidenceGateAgent {

    private static final String REJECTED_BY_MODEL = "SEMANTIC_OFF_TOPIC";
    private static final String GATE_UNAVAILABLE = "EVIDENCE_GATE_UNAVAILABLE";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ModelBackedEvidenceGateAgent(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public CuratedEvidenceSet gate(String question, CuratedEvidenceSet hygienicCandidates) {
        List<CuratedEvidenceItem> reviewable = hygienicCandidates.acceptedItems();
        if (reviewable.isEmpty()) {
            return hygienicCandidates;
        }
        try {
            Set<Integer> acceptedIndexes = acceptedIndexes(question, reviewable);
            return applyAcceptedIndexes(hygienicCandidates, reviewable, acceptedIndexes, REJECTED_BY_MODEL);
        } catch (RuntimeException exception) {
            return applyAcceptedIndexes(hygienicCandidates, reviewable, Set.of(), GATE_UNAVAILABLE);
        }
    }

    private Set<Integer> acceptedIndexes(String question, List<CuratedEvidenceItem> reviewable) {
        String response = chatClient.prompt()
                .system("""
                        You are an evidence gate for a research assistant.
                        Decide which candidate evidence items are directly relevant to the user's research question.
                        Accept only candidates that can support the requested research report.
                        Reject adjacent, generic, or unrelated scholarly content.
                        Return strict JSON only: {"acceptedIndexes":[0,2]}.
                        """)
                .user(prompt(question, reviewable))
                .call()
                .content();
        JsonNode root;
        try {
            root = objectMapper.readTree(response == null ? "" : response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Evidence gate returned invalid JSON", exception);
        }
        JsonNode accepted = root.get("acceptedIndexes");
        Set<Integer> indexes = new HashSet<>();
        if (accepted != null && accepted.isArray()) {
            accepted.forEach(node -> {
                if (node.canConvertToInt()) {
                    indexes.add(node.asInt());
                }
            });
        }
        return indexes;
    }

    private String prompt(String question, List<CuratedEvidenceItem> reviewable) {
        StringBuilder prompt = new StringBuilder("Question:\n")
                .append(question)
                .append("\n\nCandidate evidence:\n");
        for (int index = 0; index < reviewable.size(); index++) {
            CuratedEvidenceItem item = reviewable.get(index);
            prompt.append(index)
                    .append(". [")
                    .append(item.sourceType())
                    .append("] ")
                    .append(item.text())
                    .append("\n");
        }
        return prompt.toString();
    }

    private CuratedEvidenceSet applyAcceptedIndexes(
            CuratedEvidenceSet original,
            List<CuratedEvidenceItem> reviewable,
            Set<Integer> acceptedIndexes,
            String rejectionReason
    ) {
        List<CuratedEvidenceItem> gated = new ArrayList<>();
        int reviewableIndex = 0;
        for (CuratedEvidenceItem item : original.items()) {
            if (!item.accepted()) {
                gated.add(item);
                continue;
            }
            if (acceptedIndexes.contains(reviewableIndex)) {
                gated.add(item);
            } else {
                gated.add(new CuratedEvidenceItem(
                        item.sourceType(),
                        item.text(),
                        false,
                        rejectionReason,
                        List.of()
                ));
            }
            reviewableIndex++;
        }
        return new CuratedEvidenceSet(gated);
    }
}
