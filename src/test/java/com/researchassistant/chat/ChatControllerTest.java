package com.researchassistant.chat;

import com.researchassistant.support.PostgresIntegrationTest;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RetrievalSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class ChatControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private org.springframework.ai.chat.client.ChatClient chatClient;

    private Long documentId;

    @BeforeEach
    void seedChunks() {
        documentId = jdbcTemplate.queryForObject("""
                insert into research_document(title, original_file_name, storage_path, status)
                values ('paper.pdf', 'paper.pdf', 'ignored', 'INDEXED')
                returning id
                """, Long.class);
        jdbcTemplate.update("""
                insert into document_chunk(document_id, chunk_index, content, token_count, metadata_json)
                values (?, 0, 'Attention computes weighted token interactions for sequence modeling.', 12, '{}'::jsonb)
                """, documentId);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Attention computes weighted token interactions for sequence modeling.");
        when(vectorSearchPort.searchWithStats(anyString(), org.mockito.ArgumentMatchers.eq(java.util.List.of(documentId)), org.mockito.ArgumentMatchers.eq(5)))
                .thenReturn(RetrievalSearchResult.scoped(java.util.List.of(new RagChunk(
                        1L,
                        documentId,
                        0,
                        "Attention computes weighted token interactions for sequence modeling.",
                        2.0
                ))));
    }

    @Test
    void chatReturnsEvidenceBackedAnswerWithCitations() throws Exception {
        String requestBody = """
                {
                  "sessionKey": "session-42",
                  "question": "What does attention do?",
                  "documentIds": [%d]
                }
                """.formatted(documentId);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerMode").value("LOCAL_EVIDENCE"))
                .andExpect(jsonPath("$.citations[0].documentId").value(documentId.intValue()));
    }
}
