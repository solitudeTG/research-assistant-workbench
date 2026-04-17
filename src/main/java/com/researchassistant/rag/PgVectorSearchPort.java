package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean({VectorStore.class, EmbeddingModel.class})
public class PgVectorSearchPort implements VectorSearchPort {

    private final VectorStore vectorStore;

    public PgVectorSearchPort(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void reindexDocument(long documentId, List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(
                        chunk.content(),
                        Map.of(
                                "documentId", Long.toString(chunk.documentId()),
                                "chunkId", Long.toString(chunk.chunkId()),
                                "chunkIndex", Integer.toString(chunk.chunkIndex())
                        )
                ))
                .toList();
        vectorStore.add(documents);
    }

    @Override
    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(Math.max(limit * 3, limit))
                .build());
        if (documents == null) {
            return List.of();
        }

        return documents.stream()
                .map(document -> new RagChunk(
                        Long.parseLong(document.getMetadata().get("chunkId").toString()),
                        Long.parseLong(document.getMetadata().get("documentId").toString()),
                        Integer.parseInt(document.getMetadata().get("chunkIndex").toString()),
                        document.getText(),
                        document.getScore() == null ? 0.0 : document.getScore()
                ))
                .filter(chunk -> allowedDocumentIds == null || allowedDocumentIds.isEmpty() || allowedDocumentIds.contains(chunk.documentId()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
