package com.researchassistant.candidates;

import com.jayway.jsonpath.JsonPath;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.project.AssistantAnswerRepository;
import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class KnowledgeCandidateControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AssistantAnswerRepository assistantAnswerRepository;

    @Autowired
    private KnowledgeCandidateRepository candidateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private WorkbenchEventPublisher eventPublisher;

    @Test
    void listsCandidatesByAnswerAndProjectWithoutCreatingKnowledgeEntries() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        String otherAnswerId = insertAnswer(session);
        ProjectRecord otherProject = projectRepository.createProject("Other F008 project", "candidate isolation");
        clearInvocations(eventPublisher);

        KnowledgeCandidateRecord candidate = candidateRepository.createCandidate(
                session.projectId(),
                session.id(),
                answerId,
                "Bounded context",
                "Bounded context reduces unsupported synthesis drift.",
                "core_concept",
                List.of("assistant_answer", "paper_evidence"),
                List.of()
        );
        KnowledgeCandidateRecord otherAnswerCandidate = candidateRepository.createCandidate(
                session.projectId(),
                session.id(),
                otherAnswerId,
                "Other answer candidate",
                "Other answer statement.",
                "open_question",
                List.of("assistant_answer"),
                List.of()
        );
        KnowledgeCandidateRecord otherProjectCandidate = candidateRepository.createCandidate(
                otherProject.id(),
                null,
                null,
                "Other project candidate",
                "Must not leak.",
                "confirmed_finding",
                List.of("user_note"),
                List.of()
        );

        assertThat(knowledgeEntryCount(session.projectId())).isZero();
        assertPublishedProjectEvent(session.projectId(), "candidate.created", candidate.id());

        mockMvc.perform(get("/api/projects/{projectId}/answers/{answerId}/candidates",
                        session.projectId(), answerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(candidate.id()))
                .andExpect(jsonPath("$[0].projectId").value(session.projectId()))
                .andExpect(jsonPath("$[0].answerId").value(answerId))
                .andExpect(jsonPath("$[0].title").value("Bounded context"))
                .andExpect(jsonPath("$[0].statement").value("Bounded context reduces unsupported synthesis drift."))
                .andExpect(jsonPath("$[0].suggestedSection").value("core_concept"))
                .andExpect(jsonPath("$[0].sourceTypes", containsInAnyOrder("assistant_answer", "paper_evidence")))
                .andExpect(jsonPath("$[0].status").value("pending"));

        mockMvc.perform(get("/api/projects/{projectId}/candidates", session.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(candidate.id())))
                .andExpect(jsonPath("$[*].id", hasItem(otherAnswerCandidate.id())))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(hasItem(otherProjectCandidate.id()))));
    }

    @Test
    void acceptsCandidateIntoKnowledgeEntryAndPublishesKnowledgeEntryCreated() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord candidate = candidateRepository.createCandidate(
                session.projectId(),
                session.id(),
                answerId,
                "Triangulation route",
                "Compare paper evidence with project memory before synthesizing.",
                "method_route",
                List.of("assistant_answer"),
                List.of()
        );
        clearInvocations(eventPublisher);

        String responseBody = mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/accept",
                        session.projectId(), candidate.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.projectId").value(session.projectId()))
                .andExpect(jsonPath("$.section").value("method_route"))
                .andExpect(jsonPath("$.title").value("Triangulation route"))
                .andExpect(jsonPath("$.content").value("Compare paper evidence with project memory before synthesizing."))
                .andExpect(jsonPath("$.evidenceStatus").value("confirmed"))
                .andExpect(jsonPath("$.sourceCandidateId").value(candidate.id()))
                .andExpect(jsonPath("$.archived").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String entryId = JsonPath.read(responseBody, "$.id");
        assertThat(candidateStatus(candidate.id())).isEqualTo("accepted");
        assertThat(knowledgeEntryCount(session.projectId())).isEqualTo(1);
        assertPublishedProjectEvent(session.projectId(), "knowledge.entry.created", entryId);
    }

    @Test
    void editAndAcceptUsesEditedFieldsAndMarksCandidateEditedAccepted() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord candidate = candidateRepository.createCandidate(
                session.projectId(),
                session.id(),
                answerId,
                "Raw draft",
                "Raw draft statement.",
                "open_question",
                List.of("assistant_answer"),
                List.of()
        );

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/edit-and-accept",
                        session.projectId(), candidate.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Evidence gap",
                                  "content": "The causal mechanism remains unverified.",
                                  "section": "open_question",
                                  "evidenceStatus": "needs_review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Evidence gap"))
                .andExpect(jsonPath("$.content").value("The causal mechanism remains unverified."))
                .andExpect(jsonPath("$.section").value("open_question"))
                .andExpect(jsonPath("$.evidenceStatus").value("needs_review"))
                .andExpect(jsonPath("$.sourceCandidateId").value(candidate.id()));

        assertThat(candidateStatus(candidate.id())).isEqualTo("edited_accepted");
    }

    @Test
    void markUnverifiedAndIgnoreChangeOnlyCandidateStatus() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord unverified = candidateRepository.createCandidate(
                session.projectId(),
                session.id(),
                answerId,
                "Needs proof",
                "Needs more proof before deposit.",
                "confirmed_finding",
                List.of("assistant_answer"),
                List.of()
        );
        KnowledgeCandidateRecord ignored = candidateRepository.createCandidate(
                session.projectId(),
                session.id(),
                answerId,
                "Low value",
                "This should be ignored.",
                "confirmed_finding",
                List.of("assistant_answer"),
                List.of()
        );

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/mark-unverified",
                        session.projectId(), unverified.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("marked_unverified"));

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/ignore",
                        session.projectId(), ignored.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));

        assertThat(knowledgeEntryCount(session.projectId())).isZero();
    }

    @Test
    void repeatedAcceptReturnsConflictWithoutDuplicateEntryOrEvent() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord candidate = createCandidate(session, answerId, "Repeat accept", "Accept once only.");

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/accept",
                        session.projectId(), candidate.id()))
                .andExpect(status().isOk());
        clearInvocations(eventPublisher);

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/accept",
                        session.projectId(), candidate.id()))
                .andExpect(status().isConflict());

        assertThat(candidateStatus(candidate.id())).isEqualTo("accepted");
        assertThat(knowledgeEntryCount(session.projectId())).isEqualTo(1);
        assertNoKnowledgeEntryCreatedEvent();
    }

    @Test
    void acceptThenIgnoreOrMarkUnverifiedReturnsConflictAndKeepsBoardEntry() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord ignored = createCandidate(session, answerId, "Accepted ignore", "Cannot ignore after accept.");
        KnowledgeCandidateRecord unverified = createCandidate(session, answerId, "Accepted unverified", "Cannot relabel after accept.");

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/accept",
                        session.projectId(), ignored.id()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/accept",
                        session.projectId(), unverified.id()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/ignore",
                        session.projectId(), ignored.id()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/mark-unverified",
                        session.projectId(), unverified.id()))
                .andExpect(status().isConflict());

        assertThat(candidateStatus(ignored.id())).isEqualTo("accepted");
        assertThat(candidateStatus(unverified.id())).isEqualTo("accepted");
        assertThat(knowledgeEntryCount(session.projectId())).isEqualTo(2);
    }

    @Test
    void ignoreThenAcceptReturnsConflictWithoutKnowledgeEntry() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord candidate = createCandidate(session, answerId, "Ignored candidate", "Ignored cannot reopen.");

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/ignore",
                        session.projectId(), candidate.id()))
                .andExpect(status().isOk());
        clearInvocations(eventPublisher);

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/accept",
                        session.projectId(), candidate.id()))
                .andExpect(status().isConflict());

        assertThat(candidateStatus(candidate.id())).isEqualTo("ignored");
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
        assertNoKnowledgeEntryCreatedEvent();
    }

    @Test
    void repeatedEditAndAcceptReturnsConflictWithoutDuplicateEntryOrEvent() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord candidate = createCandidate(session, answerId, "Edit once", "Edit once only.");

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/edit-and-accept",
                        session.projectId(), candidate.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editAndAcceptBody("Edited once", "Edited content.")))
                .andExpect(status().isOk());
        clearInvocations(eventPublisher);

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/edit-and-accept",
                        session.projectId(), candidate.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editAndAcceptBody("Edited twice", "Should not write.")))
                .andExpect(status().isConflict());

        assertThat(candidateStatus(candidate.id())).isEqualTo("edited_accepted");
        assertThat(knowledgeEntryCount(session.projectId())).isEqualTo(1);
        assertNoKnowledgeEntryCreatedEvent();
    }

    @Test
    void editAndAcceptRejectsInvalidSectionAndEvidenceStatusAsBadRequest() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        KnowledgeCandidateRecord candidate = createCandidate(session, answerId, "Invalid edit", "Invalid edit request.");

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/edit-and-accept",
                        session.projectId(), candidate.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editAndAcceptBody("Edited", "Edited content.", "not_a_section", "confirmed")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/edit-and-accept",
                        session.projectId(), candidate.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editAndAcceptBody("Edited", "Edited content.", "confirmed_finding", "not_a_status")))
                .andExpect(status().isBadRequest());

        assertThat(candidateStatus(candidate.id())).isEqualTo("pending");
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
    }

    @Test
    void editAndAcceptRejectsEvidenceSourceIdsFromAnotherProject() throws Exception {
        ResearchSessionRecord session = createSession();
        String answerId = insertAnswer(session);
        ProjectRecord otherProject = projectRepository.createProject("Other evidence project", "isolation");
        ResearchSessionRecord otherSession = projectRepository.createSession(otherProject.id(), "Other session");
        String otherEvidenceId = insertEvidenceSource(otherProject.id(), insertAnswer(otherSession));
        String candidateId = insertCandidateWithEvidenceIds(session, answerId, List.of(otherEvidenceId));

        mockMvc.perform(post("/api/projects/{projectId}/candidates/{candidateId}/edit-and-accept",
                        session.projectId(), candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editAndAcceptBody("Edited", "Edited content.", "confirmed_finding", "confirmed")))
                .andExpect(status().isBadRequest());

        assertThat(candidateStatus(candidateId)).isEqualTo("pending");
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
    }

    private ResearchSessionRecord createSession() {
        ProjectRecord project = projectRepository.createProject("F008 project", "candidate confirmation");
        return projectRepository.createSession(project.id(), "F008 session");
    }

    private String insertAnswer(ResearchSessionRecord session) {
        String answerId = java.util.UUID.randomUUID().toString();
        assistantAnswerRepository.insert(
                answerId,
                session.projectId(),
                session.id(),
                "What should become durable knowledge?",
                "Candidate answer",
                "LOCAL_EVIDENCE",
                "SUFFICIENT"
        );
        return answerId;
    }

    private int knowledgeEntryCount(String projectId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from knowledge_entry where project_id = ?",
                Integer.class,
                projectId
        );
        return count == null ? 0 : count;
    }

    private KnowledgeCandidateRecord createCandidate(
            ResearchSessionRecord session,
            String answerId,
            String title,
            String statement) {
        return candidateRepository.createCandidate(
                session.projectId(),
                session.id(),
                answerId,
                title,
                statement,
                "confirmed_finding",
                List.of("assistant_answer"),
                List.of()
        );
    }

    private String editAndAcceptBody(String title, String content) {
        return editAndAcceptBody(title, content, "confirmed_finding", "confirmed");
    }

    private String editAndAcceptBody(String title, String content, String section, String evidenceStatus) {
        return """
                {
                  "title": "%s",
                  "content": "%s",
                  "section": "%s",
                  "evidenceStatus": "%s"
                }
                """.formatted(title, content, section, evidenceStatus);
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

    private String insertCandidateWithEvidenceIds(
            ResearchSessionRecord session,
            String answerId,
            List<String> evidenceSourceIds) {
        String candidateId = java.util.UUID.randomUUID().toString();
        String json = "[\"" + String.join("\",\"", evidenceSourceIds) + "\"]";
        jdbcTemplate.update("""
                insert into knowledge_candidate(
                    id, project_id, session_id, answer_id, status, content, title, statement,
                    suggested_section, source_types_json, evidence_source_ids_json
                )
                values (?, ?, ?, ?, 'pending', 'candidate', 'Candidate', 'Candidate statement',
                        'confirmed_finding', '["assistant_answer"]'::jsonb, ?::jsonb)
                """, candidateId, session.projectId(), session.id(), answerId, json);
        return candidateId;
    }

    private void assertNoKnowledgeEntryCreatedEvent() {
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(WorkbenchEvent.class);
        try {
            verify(eventPublisher, times(0)).publish(eventCaptor.capture());
        } catch (org.mockito.exceptions.base.MockitoAssertionError assertionError) {
            throw assertionError;
        }
    }

    private String candidateStatus(String candidateId) {
        return jdbcTemplate.queryForObject(
                "select status from knowledge_candidate where id = ?",
                String.class,
                candidateId
        );
    }

    private void assertPublishedProjectEvent(String projectId, String eventType, String resourceId) {
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(WorkbenchEvent.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publish(eventCaptor.capture());
        WorkbenchEvent event = eventCaptor.getAllValues().stream()
                .filter(published -> eventType.equals(published.eventType().wireName()))
                .filter(published -> resourceId.equals(published.payload().get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(event.projectId()).isEqualTo(projectId);
    }
}
