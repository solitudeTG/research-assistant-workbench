package com.researchassistant.rag;

public record RagChunk(
        long chunkId,
        long documentId,
        int chunkIndex,
        String content,
        double finalScore,
        double feedbackScore
) {

    public RagChunk(long chunkId, long documentId, int chunkIndex, String content, double finalScore) {
        this(chunkId, documentId, chunkIndex, content, finalScore, 0.0);
    }
}
