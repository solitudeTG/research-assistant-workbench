package com.researchassistant.rag;

import java.util.List;

public record RetrievalSearchResult(
        List<RagChunk> chunks,
        int preScopeHits,
        int postScopeHits
) {

    public RetrievalSearchResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        preScopeHits = Math.max(preScopeHits, 0);
        postScopeHits = Math.max(postScopeHits, 0);
    }

    public static RetrievalSearchResult empty() {
        return new RetrievalSearchResult(List.of(), 0, 0);
    }

    public static RetrievalSearchResult scoped(List<RagChunk> chunks) {
        int size = chunks == null ? 0 : chunks.size();
        return new RetrievalSearchResult(chunks, size, size);
    }
}
