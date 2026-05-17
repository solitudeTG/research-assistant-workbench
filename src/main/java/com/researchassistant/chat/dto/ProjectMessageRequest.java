package com.researchassistant.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ProjectMessageRequest(
        @NotBlank String question,
        List<String> sourceFilters,
        Boolean allowWebSupplement,
        Boolean extractKnowledgeCandidates,
        String answerMode
) {
}
