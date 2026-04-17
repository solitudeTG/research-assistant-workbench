package com.researchassistant.rag;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(VectorStore.class)
public class NoOpVectorSearchPort implements VectorSearchPort {

    @Override
    public void reindexDocument(long documentId, List<RagChunk> chunks) {
        // Vector search is intentionally disabled for providers without embedding support.
    }

    @Override
    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        return List.of();
    }
}
