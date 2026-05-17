package com.researchassistant.feedback;

public record ProjectAnswerFeedbackResult(
        String projectId,
        String answerId,
        String rating,
        String reason,
        int feedbackScore,
        int updatedEvidenceSourceCount,
        int updatedChunkCount,
        String status
) {
}
