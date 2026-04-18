package com.researchassistant.chat.dto;

import java.util.List;

public record FeedbackRequest(
        int score,
        List<Long> chunkIds,
        String note
) {
}
