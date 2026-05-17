package com.researchassistant.ingest;

import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import com.researchassistant.support.PostgresIntegrationTest;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class ProjectSourceStatusMachineTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private WorkbenchEventPublisher eventPublisher;

    @Test
    void importsPdfSourceThroughExactStatusChainAndListsOnlyProjectSources() throws Exception {
        String projectId = createProject("PDF project");
        String otherProjectId = createProject("Other project");
        insertSource(otherProjectId, "Other source");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "paper.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfBytes("paper content")
        );

        var result = mockMvc.perform(multipart("/api/projects/{projectId}/sources", projectId).file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.type").value("pdf"))
                .andExpect(jsonPath("$.title").value("paper.pdf"))
                .andExpect(jsonPath("$.status").value("deposited"))
                .andExpect(jsonPath("$.failureStage").doesNotExist())
                .andReturn();

        String sourceId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/projects/{projectId}/sources", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sourceId))
                .andExpect(jsonPath("$[0].sourceId").doesNotExist())
                .andExpect(jsonPath("$[0].failureStage").value(nullValue()))
                .andExpect(jsonPath("$[0].errorMessage").value(nullValue()));

        mockMvc.perform(get("/api/projects/{projectId}/sources/{sourceId}", otherProjectId, sourceId))
                .andExpect(status().isNotFound());

        assertThat(sourceStatusEventsFor(sourceId))
                .extracting(event -> event.payload().get("status"))
                .containsExactly("uploaded", "parsing", "indexing", "extracting", "indexed", "depositing", "deposited");

        Map<String, Object> mapping = jdbcTemplate.queryForMap("""
                select s.indexed_document_id, d.title, d.original_file_name, d.status
                from source_document s
                join research_document d on d.id = s.indexed_document_id
                where s.project_id = ?
                  and s.id = ?
                """, projectId, sourceId);
        assertThat(mapping.get("indexed_document_id")).isNotNull();
        assertThat(mapping)
                .containsEntry("title", "paper.pdf")
                .containsEntry("original_file_name", "paper.pdf")
                .containsEntry("status", "INDEXED");
    }

    @Test
    void importsWebSourceThroughExactStatusChain() throws Exception {
        String projectId = createProject("Web project");

        var result = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "web_page",
                                  "title": "Example",
                                  "uri": "https://example.com/research"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("web_page"))
                .andExpect(jsonPath("$.status").value("deposited"))
                .andReturn();

        String sourceId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        assertThat(sourceStatusEventsFor(sourceId))
                .extracting(event -> event.payload().get("status"))
                .containsExactly("submitted", "fetching", "extracting", "indexed", "depositing", "deposited");
    }

    @Test
    void importsJsonNoteWithDefaultTitleThroughExactStatusChain() throws Exception {
        String projectId = createProject("Note project");

        var result = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "note",
                                  "content": "Research note body"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("note"))
                .andExpect(jsonPath("$.title").value("Untitled note"))
                .andExpect(jsonPath("$.status").value("deposited"))
                .andReturn();

        String sourceId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        assertThat(sourceStatusEventsFor(sourceId))
                .extracting(event -> event.payload().get("status"))
                .containsExactly("uploaded", "parsing", "indexing", "extracting", "indexed", "depositing", "deposited");
    }

    @Test
    void parsingFailureRecordsConcreteFailureStage() throws Exception {
        String projectId = createProject("Failure project");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]
        );

        var result = mockMvc.perform(multipart("/api/projects/{projectId}/sources", projectId).file(file))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.failureStage").value("parsing"))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty())
                .andReturn();

        String sourceId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        var row = jdbcTemplate.queryForMap("""
                select status, failure_stage, error_message
                from source_document
                where id = ?
                """, sourceId);
        assertThat(row.get("status")).isEqualTo("failed");
        assertThat(row.get("failure_stage")).isEqualTo("parsing");
        assertThat(row.get("error_message")).isNotNull();
    }

    @Test
    void fetchingFailureRecordsConcreteFailureStage() throws Exception {
        String projectId = createProject("Fetching failure project");

        mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "web_page",
                                  "title": "Missing URI"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.failureStage").value("fetching"))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty());
    }

    @Test
    void jsonImportRejectsUnsupportedSourceTypeBeforeInsert() throws Exception {
        String projectId = createProject("Validation project");

        mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "video",
                                  "title": "Unsupported",
                                  "uri": "https://example.com/video"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_source_type"));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from source_document where project_id = ?",
                Integer.class,
                projectId
        );
        assertThat(count).isZero();
    }

    @Test
    void jsonImportRejectsMissingOrBlankSourceTypeBeforeInsert() throws Exception {
        String projectId = createProject("Required type project");

        for (String body : List.of(
                """
                        {
                          "title": "Missing type",
                          "uri": "https://example.com/missing-type"
                        }
                        """,
                """
                        {
                          "type": "  ",
                          "title": "Blank type",
                          "uri": "https://example.com/blank-type"
                        }
                        """
        )) {
            mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("unsupported_source_type"));
        }

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from source_document where project_id = ?",
                Integer.class,
                projectId
        );
        assertThat(count).isZero();
    }

    @Test
    void retryFailedSourceResumesAndPublishesAnotherStatusChangedEvent() throws Exception {
        String projectId = createProject("Retry project");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]
        );

        var failed = mockMvc.perform(multipart("/api/projects/{projectId}/sources", projectId).file(file))
                .andExpect(status().isServiceUnavailable())
                .andReturn();
        String sourceId = com.jayway.jsonpath.JsonPath.read(failed.getResponse().getContentAsString(), "$.id");
        int eventsBeforeRetry = sourceStatusEventsFor(sourceId).size();

        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/retry", projectId, sourceId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.id").value(sourceId))
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.failureStage").value("parsing"));

        List<WorkbenchEvent> eventsAfterRetry = sourceStatusEventsFor(sourceId);
        assertThat(eventsAfterRetry).hasSizeGreaterThan(eventsBeforeRetry);
        assertThat(eventsAfterRetry.subList(eventsBeforeRetry, eventsAfterRetry.size()))
                .extracting(event -> event.payload().get("status"))
                .contains("parsing", "failed");
    }

    private String createProject(String topic) {
        String projectId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into research_project(id, topic, summary)
                values (?, ?, '')
                """, projectId, topic);
        return projectId;
    }

    private void insertSource(String projectId, String title) {
        jdbcTemplate.update("""
                insert into source_document(id, project_id, type, title, status)
                values (?, ?, 'note', ?, 'uploaded')
                """, java.util.UUID.randomUUID().toString(), projectId, title);
    }

    private List<WorkbenchEvent> sourceStatusEventsFor(String sourceId) {
        ArgumentCaptor<WorkbenchEvent> eventCaptor = ArgumentCaptor.forClass(WorkbenchEvent.class);
        verify(eventPublisher, atLeast(0)).publish(eventCaptor.capture());
        return eventCaptor.getAllValues().stream()
                .filter(event -> event.eventType() == WorkbenchEventType.SOURCE_STATUS_CHANGED)
                .filter(event -> sourceId.equals(event.sourceId()))
                .toList();
    }

    private byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
