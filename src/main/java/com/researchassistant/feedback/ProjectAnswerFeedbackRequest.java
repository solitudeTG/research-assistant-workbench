package com.researchassistant.feedback;

import java.util.List;

public record ProjectAnswerFeedbackRequest(
        String rating,
        String reason,
        String note,
        List<String> evidenceSourceIds
) {

    public ProjectAnswerFeedbackRequest {
        if (evidenceSourceIds == null || evidenceSourceIds.isEmpty()) {
            evidenceSourceIds = List.of();
        } else {
            evidenceSourceIds = evidenceSourceIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
        }
    }

    String effectiveNote() {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        if (note != null && !note.isBlank()) {
            return note;
        }
        return null;
    }
}
