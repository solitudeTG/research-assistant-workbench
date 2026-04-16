package com.researchassistant.ingest;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIngestService documentIngestService;

    @Test
    void uploadReturnsServiceUnavailableWhenSubmissionIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "paper.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "research notes".getBytes()
        );

        when(documentIngestService.registerUpload(any()))
                .thenReturn(Map.of(
                        "documentId", 7L,
                        "status", "FAILED",
                        "title", "paper.txt"
                ));

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.documentId").value(7));
    }
}
