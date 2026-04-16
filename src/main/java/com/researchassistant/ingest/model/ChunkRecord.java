package com.researchassistant.ingest.model;

public record ChunkRecord(
        long documentId,
        int chunkIndex,
        String content,
        int tokenCount,
        String metadataJson
) {
}
