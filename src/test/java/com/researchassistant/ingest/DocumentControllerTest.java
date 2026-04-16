package com.researchassistant.ingest;

import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.isA;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class DocumentControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DocumentProcessingJob documentProcessingJob;

    @MockBean
    private com.researchassistant.rag.VectorSearchPort vectorSearchPort;

    @Test
    void uploadRegistersDocumentPersistsRowAndStoresFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "nested/path/paper.txt",
                "text/plain",
                "research notes".getBytes()
        );

        when(documentProcessingJob.processDocument(anyLong())).thenReturn(CompletableFuture.completedFuture(null));

        var result = mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.title").value("paper.txt"))
                .andExpect(jsonPath("$.documentId", isA(Number.class)))
                .andReturn();

        long documentId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.documentId"
        )).longValue();

        var row = jdbcTemplate.queryForMap("""
                select title, original_file_name, storage_path, status
                from research_document
                where id = ?
                """, documentId);

        Path testStorageRoot = Path.of("target/test-storage").toAbsolutePath().normalize();
        Path savedFile = Path.of((String) row.get("storage_path"));

        org.assertj.core.api.Assertions.assertThat(row.get("title")).isEqualTo("paper.txt");
        org.assertj.core.api.Assertions.assertThat(row.get("original_file_name")).isEqualTo("paper.txt");
        org.assertj.core.api.Assertions.assertThat(savedFile).startsWith(testStorageRoot.resolve("uploads"));
        org.assertj.core.api.Assertions.assertThat(savedFile.getFileName().toString()).endsWith("_paper.txt");
        org.assertj.core.api.Assertions.assertThat(Files.exists(savedFile)).isTrue();
    }
}
