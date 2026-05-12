package com.researchassistant.rag;

import com.researchassistant.ingest.DocumentChunkRepository;
import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.model.ChunkRecord;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.ResearchDocument;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalVectorIndexWarmupTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final LocalVectorSearchPort vectorSearchPort = mock(LocalVectorSearchPort.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<LocalVectorSearchPort> vectorSearchPortProvider = mock(ObjectProvider.class);
    private final ApplicationArguments arguments = mock(ApplicationArguments.class);

    @Test
    void warmsLocalVectorIndexFromPersistedIndexedChunks() {
        when(documentRepository.findAll()).thenReturn(List.of(
                document(1L, DocumentStatus.INDEXED),
                document(2L, DocumentStatus.FAILED)
        ));
        when(chunkRepository.findByDocumentId(1L)).thenReturn(List.of(
                new ChunkRecord(10L, 1L, 0, "space time beamforming", 12, "{}"),
                new ChunkRecord(11L, 1L, 1, "doppler shift", 8, "{}")
        ));
        when(vectorSearchPortProvider.getIfAvailable()).thenReturn(vectorSearchPort);

        new LocalVectorIndexWarmup(documentRepository, chunkRepository, vectorSearchPortProvider).run(arguments);

        verify(vectorSearchPort).reindexDocument(
                org.mockito.ArgumentMatchers.eq(1L),
                argThat(chunks -> chunks.size() == 2
                        && chunks.get(0).chunkId() == 10L
                        && chunks.get(1).content().equals("doppler shift"))
        );
        verify(chunkRepository, never()).findByDocumentId(2L);
    }

    private ResearchDocument document(long id, DocumentStatus status) {
        return new ResearchDocument(
                id,
                "paper-" + id,
                "paper-" + id + ".pdf",
                "storage/" + id,
                status,
                null,
                null,
                2,
                20,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
