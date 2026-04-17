package com.researchassistant.chat.dto;

import java.util.List;

public record ChatResponse(
        String sessionKey,
        String answerMode,
        String answer,
        List<CitationDto> citations
) {
}
