package com.researchassistant.rag;

public record RagChunk(
        long chunkId,
        long documentId,
        int chunkIndex,
        String content,
        double finalScore
) {
}
