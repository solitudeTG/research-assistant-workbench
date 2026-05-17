package com.researchassistant.feedback;

public record ProjectAnswerFeedbackResult(
        String projectId,
        String answerId,
        String rating,
        int feedbackScore,
        int updatedEvidenceSourceCount,
        int updatedChunkCount,
        String status
) {
}
