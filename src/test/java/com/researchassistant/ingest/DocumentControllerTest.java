package com.researchassistant.ingest;

import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class DocumentControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadRegistersDocumentAndReturnsAcceptedResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "paper.txt",
                "text/plain",
                "research notes".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.documentId", isA(Number.class)));
    }
}
