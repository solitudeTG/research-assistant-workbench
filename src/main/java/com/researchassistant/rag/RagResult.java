package com.researchassistant.rag;

import java.util.List;

public record RagResult(
        String query,
        List<Long> allowedDocumentIds,
        List<RagChunk> chunks
) {
}
