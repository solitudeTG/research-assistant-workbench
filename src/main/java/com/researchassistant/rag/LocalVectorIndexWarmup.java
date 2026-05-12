package com.researchassistant.rag;

import com.researchassistant.ingest.DocumentChunkRepository;
import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.model.ChunkRecord;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.ResearchDocument;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LocalVectorIndexWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalVectorIndexWarmup.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ObjectProvider<LocalVectorSearchPort> vectorSearchPortProvider;

    public LocalVectorIndexWarmup(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            ObjectProvider<LocalVectorSearchPort> vectorSearchPortProvider) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorSearchPortProvider = vectorSearchPortProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalVectorSearchPort vectorSearchPort = vectorSearchPortProvider.getIfAvailable();
        if (vectorSearchPort == null) {
            return;
        }
        int documents = 0;
        int chunks = 0;
        for (ResearchDocument document : documentRepository.findAll()) {
            if (document.status() != DocumentStatus.INDEXED || document.id() == null) {
                continue;
            }
            List<RagChunk> ragChunks = chunkRepository.findByDocumentId(document.id()).stream()
                    .filter(chunk -> chunk.id() != null)
                    .map(this::toRagChunk)
                    .toList();
            if (ragChunks.isEmpty()) {
                continue;
            }
            vectorSearchPort.reindexDocument(document.id(), ragChunks);
            documents++;
            chunks += ragChunks.size();
        }
        if (documents > 0) {
            log.info("Warmed local vector index from {} indexed document(s), {} chunk(s)", documents, chunks);
        }
    }

    private RagChunk toRagChunk(ChunkRecord chunk) {
        return new RagChunk(
                chunk.id(),
                chunk.documentId(),
                chunk.chunkIndex(),
                chunk.content(),
                0.0
        );
    }
}
