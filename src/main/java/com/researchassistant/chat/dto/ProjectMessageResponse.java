package com.researchassistant.chat.dto;

public record ProjectMessageResponse(
        String messageId,
        String answerId,
        String streamRunId,
        String sseUrl
) {
}
