package com.researchassistant.ingest.model;

public record ChunkRecord(
        Long id,
        long documentId,
        int chunkIndex,
        String content,
        int tokenCount,
        String metadataJson
) {
}
