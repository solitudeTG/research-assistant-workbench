package com.researchassistant.rag;

import java.util.List;

public record RagResult(
        String query,
        List<Long> allowedDocumentIds,
        List<RagChunk> chunks,
        RetrievalObservation observation
) {
    public RagResult(String query, List<Long> allowedDocumentIds, List<RagChunk> chunks) {
        this(query, allowedDocumentIds, chunks, null);
    }
}
