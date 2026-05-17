package com.researchassistant.rag;

import java.util.List;

public interface VectorSearchPort {

    void reindexDocument(long documentId, List<RagChunk> chunks);

    List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit);

    default void applyChunkFeedback(List<Long> chunkIds, double delta) {
    }
}
