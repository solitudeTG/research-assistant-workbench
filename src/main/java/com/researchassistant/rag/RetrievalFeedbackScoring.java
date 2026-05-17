package com.researchassistant.rag;

public final class RetrievalFeedbackScoring {

    private static final double FEEDBACK_WEIGHT = 0.05;
    private static final double MAX_ADJUSTMENT = 0.2;
    private static final double MIN_ADJUSTMENT = -0.2;

    private RetrievalFeedbackScoring() {
    }

    public static double finalScore(double relevanceScore, double feedbackScore) {
        return relevanceScore + clamp(feedbackScore * FEEDBACK_WEIGHT);
    }

    private static double clamp(double value) {
        return Math.max(MIN_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, value));
    }
}
