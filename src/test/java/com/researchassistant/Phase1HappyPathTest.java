package com.researchassistant;

import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.DocumentChunkRepository;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class Phase1HappyPathTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Test
    void uploadThenAskGroundedQuestion() throws Exception {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Multi-head attention improves representation capacity.");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "paper.txt",
                "text/plain",
                "Attention computes weighted token interactions. Multi-head attention improves representation capacity."
                        .getBytes()
        );

        String body = mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long documentId = Long.parseLong(body.replaceAll(".*\"documentId\":(\\d+).*", "$1"));

        for (int i = 0; i < 25; i++) {
            var document = documentRepository.findById(documentId).orElseThrow();
            if (document.status() == DocumentStatus.INDEXED) {
                break;
            }
            Thread.sleep(200);
        }

        assertThat(documentRepository.findById(documentId)).get()
                .extracting(document -> document.status())
                .isEqualTo(DocumentStatus.INDEXED);
        assertThat(documentChunkRepository.findByDocumentId(documentId)).isNotEmpty();
        when(vectorSearchPort.search(anyString(), eq(List.of(documentId)), eq(5)))
                .thenReturn(documentChunkRepository.findByDocumentId(documentId).stream()
                        .map(chunk -> new RagChunk(
                                chunk.id(),
                                chunk.documentId(),
                                chunk.chunkIndex(),
                                chunk.content(),
                                2.0))
                        .toList());

        String requestBody = """
                {
                  "sessionKey": "happy-path",
                  "question": "What does multi-head attention improve?",
                  "documentIds": [%d]
                }
                """.formatted(documentId);

        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].documentId").value(documentId));
    }
}
