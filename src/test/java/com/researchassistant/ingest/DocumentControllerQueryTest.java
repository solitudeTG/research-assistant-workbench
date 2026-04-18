package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.DocumentAnalysis;
import com.researchassistant.ingest.model.ResearchDocument;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerQueryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIngestService documentIngestService;

    @Test
    void getDocumentStatusReturnsIndexedDocument() throws Exception {
        when(documentIngestService.findDocument(9L)).thenReturn(Optional.of(new ResearchDocument(
                9L,
                "paper.pdf",
                "paper.pdf",
                "/tmp/paper.pdf",
                DocumentStatus.INDEXED,
                null,
                null,
                12,
                820,
                OffsetDateTime.parse("2026-04-17T12:00:00+08:00"),
                OffsetDateTime.parse("2026-04-17T12:00:05+08:00")
        )));

        mockMvc.perform(get("/api/documents/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(9))
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.title").value("paper.pdf"))
                .andExpect(jsonPath("$.totalChunks").value(12))
                .andExpect(jsonPath("$.totalTokens").value(820))
                .andExpect(jsonPath("$.parseError").doesNotExist());
    }

    @Test
    void getDocumentStatusReturnsNotFoundWhenDocumentDoesNotExist() throws Exception {
        when(documentIngestService.findDocument(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listDocumentsReturnsWorkspacePayload() throws Exception {
        when(documentIngestService.listDocuments()).thenReturn(List.of(new ResearchDocument(
                5L,
                "assistant.pdf",
                "assistant.pdf",
                "/tmp/assistant.pdf",
                DocumentStatus.INDEXED,
                null,
                null,
                8,
                610,
                OffsetDateTime.parse("2026-04-17T12:00:00+08:00"),
                OffsetDateTime.parse("2026-04-17T12:00:05+08:00")
        )));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].documentId").value(5))
                .andExpect(jsonPath("$.documents[0].totalChunks").value(8))
                .andExpect(jsonPath("$.documents[0].totalTokens").value(610));
    }

    @Test
    void getDocumentAnalysisReturnsStructuredPayload() throws Exception {
        when(documentIngestService.findDocumentAnalysis(9L)).thenReturn(Optional.of(new DocumentAnalysis(
                9L,
                "Abstract text",
                "Summary text",
                List.of("Method one"),
                List.of("Contribution one"),
                List.of("memory", "retrieval"),
                List.of("Abstract", "Method"),
                OffsetDateTime.parse("2026-04-17T12:00:00+08:00"),
                OffsetDateTime.parse("2026-04-17T12:00:05+08:00")
        )));

        mockMvc.perform(get("/api/documents/9/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abstractText").value("Abstract text"))
                .andExpect(jsonPath("$.methods[0]").value("Method one"))
                .andExpect(jsonPath("$.keywords[0]").value("memory"));
    }
}
