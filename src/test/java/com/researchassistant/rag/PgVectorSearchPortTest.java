package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgVectorSearchPortTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void searchWithStatsCountsVectorStoreCandidatesBeforeAndAfterScopeFiltering() {
        PgVectorSearchPort port = new PgVectorSearchPort(vectorStore, jdbcTemplate);
        Document allowed = new Document("Allowed project chunk", Map.of(
                "documentId", "1",
                "chunkId", "11",
                "chunkIndex", "0"
        ));
        Document outsideScope = new Document("Outside project chunk", Map.of(
                "documentId", "2",
                "chunkId", "21",
                "chunkIndex", "0"
        ));

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(allowed, outsideScope));
        when(jdbcTemplate.query(anyString(), anyMap(), any(ResultSetExtractor.class))).thenReturn(Map.of());

        RetrievalSearchResult result = port.searchWithStats("project chunk", List.of(1L), 1);

        assertThat(result.preScopeHits()).isEqualTo(2);
        assertThat(result.postScopeHits()).isEqualTo(1);
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).chunkId()).isEqualTo(11L);
    }
}
