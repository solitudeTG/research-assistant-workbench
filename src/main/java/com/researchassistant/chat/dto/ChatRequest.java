package com.researchassistant.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChatRequest(
        @NotBlank String sessionKey,
        @NotBlank String question,
        List<Long> documentIds
) {
}
