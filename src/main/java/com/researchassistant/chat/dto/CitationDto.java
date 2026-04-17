package com.researchassistant.chat.dto;

public record CitationDto(
        long chunkId,
        long documentId,
        int chunkIndex,
        String excerpt
) {
}
