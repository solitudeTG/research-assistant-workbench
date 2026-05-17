package com.researchassistant.knowledge;

import com.jayway.jsonpath.JsonPath;
import com.researchassistant.candidates.KnowledgeCandidateRecord;
import com.researchassistant.candidates.KnowledgeCandidateRepository;
import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class KnowledgeBoardControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private KnowledgeCandidateRepository candidateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsBoardSectionsAndEntriesWithoutLeakingArchivedOrOtherProjectEntries() throws Exception {
        ProjectRecord project = projectRepository.createProject("F008 board", "knowledge board");
        ProjectRecord otherProject = projectRepository.createProject("Other board", "isolation");

        String entryId = createManualEntry(project.id(), "core_concept", "Grounded answer", "Answers must cite project evidence.");
        createManualEntry(otherProject.id(), "core_concept", "Other project", "Must not leak.");
        String archivedId = createManualEntry(project.id(), "method_route", "Archived", "Hidden.");
        archiveRow(archivedId);

        mockMvc.perform(get("/api/projects/{projectId}/knowledge-board", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].section", contains(
                        "current_candidates",
                        "core_concept",
                        "method_route",
                        "confirmed_finding",
                        "open_question"
                )))
                .andExpect(jsonPath("$[1].entries[*].id", hasItem(entryId)))
                .andExpect(jsonPath("$[1].entries[*].title", hasItem("Grounded answer")))
                .andExpect(jsonPath("$[2].entries.length()").value(0));
    }

    @Test
    void manualCreateWritesKnowledgeEntryWithoutCandidate() throws Exception {
        ProjectRecord project = projectRepository.createProject("F008 manual", "knowledge board");

        mockMvc.perform(post("/api/projects/{projectId}/knowledge-board/entries", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "section": "confirmed_finding",
                                  "title": "Evidence is bounded",
                                  "content": "Only confirmed project evidence can ground durable findings.",
                                  "evidenceStatus": "confirmed"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.projectId").value(project.id()))
                .andExpect(jsonPath("$.section").value("confirmed_finding"))
                .andExpect(jsonPath("$.title").value("Evidence is bounded"))
                .andExpect(jsonPath("$.content").value("Only confirmed project evidence can ground durable findings."))
                .andExpect(jsonPath("$.evidenceStatus").value("confirmed"))
                .andExpect(jsonPath("$.sourceCandidateId").value(nullValue()))
                .andExpect(jsonPath("$.archived").value(false));

        assertThat(knowledgeEntryCount(project.id())).isEqualTo(1);
    }

    @Test
    void patchUpdatesAndMovesKnowledgeEntry() throws Exception {
        ProjectRecord project = projectRepository.createProject("F008 patch", "knowledge board");
        String entryId = createManualEntry(project.id(), "open_question", "Initial", "Initial content.");

        mockMvc.perform(patch("/api/projects/{projectId}/knowledge-board/entries/{entryId}", project.id(), entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "section": "method_route",
                                  "title": "Updated route",
                                  "content": "Use accepted candidates as the only deposit path.",
                                  "evidenceStatus": "needs_review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId))
                .andExpect(jsonPath("$.section").value("method_route"))
                .andExpect(jsonPath("$.title").value("Updated route"))
                .andExpect(jsonPath("$.content").value("Use accepted candidates as the only deposit path."))
                .andExpect(jsonPath("$.evidenceStatus").value("needs_review"));
    }

    @Test
    void manualCreateAndPatchRejectInvalidSectionAndEvidenceStatusAsBadRequest() throws Exception {
        ProjectRecord project = projectRepository.createProject("F008 invalid input", "knowledge board");
        String entryId = createManualEntry(project.id(), "open_question", "Initial", "Initial content.");

        mockMvc.perform(post("/api/projects/{projectId}/knowledge-board/entries", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryBody("not_a_section", "Invalid", "content", "confirmed")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/{projectId}/knowledge-board/entries", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryBody("confirmed_finding", "Invalid", "content", "not_a_status")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/projects/{projectId}/knowledge-board/entries/{entryId}", project.id(), entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryBody("not_a_section", "Invalid", "content", "confirmed")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/projects/{projectId}/knowledge-board/entries/{entryId}", project.id(), entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryBody("confirmed_finding", "Invalid", "content", "not_a_status")))
                .andExpect(status().isBadRequest());

        assertThat(knowledgeEntryCount(project.id())).isEqualTo(1);
    }

    @Test
    void manualCreateRejectsEvidenceSourceIdsFromAnotherProject() throws Exception {
        ProjectRecord project = projectRepository.createProject("F008 evidence isolation", "knowledge board");
        ProjectRecord otherProject = projectRepository.createProject("Other evidence project", "isolation");
        String otherAnswerId = insertAnswer(otherProject.id());
        String otherEvidenceId = insertEvidenceSource(otherProject.id(), otherAnswerId);

        mockMvc.perform(post("/api/projects/{projectId}/knowledge-board/entries", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "section": "confirmed_finding",
                                  "title": "Cross evidence",
                                  "content": "Should reject foreign evidence.",
                                  "evidenceStatus": "confirmed",
                                  "evidenceSourceIds": ["%s"]
                                }
                                """.formatted(otherEvidenceId)))
                .andExpect(status().isBadRequest());

        assertThat(knowledgeEntryCount(project.id())).isZero();
    }

    @Test
    void deleteArchivesKnowledgeEntryWithoutHardDelete() throws Exception {
        ProjectRecord project = projectRepository.createProject("F008 archive", "knowledge board");
        String entryId = createManualEntry(project.id(), "core_concept", "Archive me", "No longer active.");

        mockMvc.perform(delete("/api/projects/{projectId}/knowledge-board/entries/{entryId}", project.id(), entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "select archived from knowledge_entry where id = ?",
                Boolean.class,
                entryId
        )).isTrue();

        mockMvc.perform(get("/api/projects/{projectId}/knowledge-board", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].entries.length()").value(0));
    }

    @Test
    void candidateCreationDoesNotAddBoardEntryUntilAccept() throws Exception {
        ProjectRecord project = projectRepository.createProject("F008 separation", "knowledge board");
        KnowledgeCandidateRecord candidate = candidateRepository.createCandidate(
                project.id(),
                null,
                null,
                "Pending draft",
                "Drafts are not confirmed knowledge.",
                "core_concept",
                List.of("assistant_answer"),
                List.of()
        );

        mockMvc.perform(get("/api/projects/{projectId}/knowledge-board", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].entries.length()").value(0));

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/accept", project.id(), candidate.id()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{projectId}/knowledge-board", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].entries[0].sourceCandidateId").value(candidate.id()));
    }

    private String createManualEntry(String projectId, String section, String title, String content) throws Exception {
        String responseBody = mockMvc.perform(post("/api/projects/{projectId}/knowledge-board/entries", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryBody(section, title, content, "confirmed")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.id");
    }

    private String entryBody(String section, String title, String content, String evidenceStatus) {
        return """
                {
                  "section": "%s",
                  "title": "%s",
                  "content": "%s",
                  "evidenceStatus": "%s"
                }
                """.formatted(section, title, content, evidenceStatus);
    }

    private String insertAnswer(String projectId) {
        String answerId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into assistant_answer(id, project_id, question, answer, answer_mode, evidence_state)
                values (?, ?, 'question', 'answer', 'LOCAL_EVIDENCE', 'SUFFICIENT')
                """, answerId, projectId);
        return answerId;
    }

    private String insertEvidenceSource(String projectId, String answerId) {
        String evidenceId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into evidence_source(
                    id, project_id, answer_id, quote, source_type, snippet, strength, confidence
                )
                values (?, ?, ?, 'quote', 'paper', 'snippet', 'strong', 'strong')
                """, evidenceId, projectId, answerId);
        return evidenceId;
    }

    private void archiveRow(String entryId) {
        jdbcTemplate.update("update knowledge_entry set archived = true where id = ?", entryId);
    }

    private int knowledgeEntryCount(String projectId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from knowledge_entry where project_id = ?",
                Integer.class,
                projectId
        );
        return count == null ? 0 : count;
    }
}
