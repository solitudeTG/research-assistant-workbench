package com.researchassistant.project;

import com.jayway.jsonpath.JsonPath;
import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProjectControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsListsAndFetchesProjects() throws Exception {
        String responseBody = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Agentic research workbench",
                                  "summary": "Grounded long-running research"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.topic").value("Agentic research workbench"))
                .andExpect(jsonPath("$.summary").value("Grounded long-running research"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.stats.sourceCount").value(0))
                .andExpect(jsonPath("$.stats.sessionCount").value(0))
                .andExpect(jsonPath("$.stats.knowledgeEntryCount").value(0))
                .andExpect(jsonPath("$.stats.candidateCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String projectId = JsonPath.read(responseBody, "$.id");

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$[*].id", hasItem(projectId)));

        mockMvc.perform(get("/api/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.topic").value("Agentic research workbench"))
                .andExpect(jsonPath("$.stats.sourceCount").value(0));
    }

    @Test
    void createsAndListsSessionsUnderProject() throws Exception {
        String projectA = createProject("Project A");
        String projectB = createProject("Project B");

        String sessionA = createSession(projectA, "Evidence boundary design");
        String sessionB = createSession(projectB, "Other project session");

        mockMvc.perform(get("/api/projects/{projectId}/sessions", projectA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$[*].id", hasItem(sessionA)))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(hasItem(sessionB))))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].projectId", hasItem(projectA)))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].title", hasItem("Evidence boundary design")))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].status", hasItem("continue")))
                .andExpect(jsonPath("$[?(@.id == '" + sessionA + "')].lastMessageAt", hasItem(nullValue())));
    }

    @Test
    void renamesSessionUnderProject() throws Exception {
        String projectId = createProject("Project");
        String sessionId = createSession(projectId, "Initial title");

        mockMvc.perform(patch("/api/projects/{projectId}/sessions/{sessionId}", projectId, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Transformer literature review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.title").value("Transformer literature review"))
                .andExpect(jsonPath("$.status").value("continue"));

        mockMvc.perform(get("/api/projects/{projectId}/sessions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + sessionId + "')].title", hasItem("Transformer literature review")));
    }

    @Test
    void deletesSessionAndRemovesItFromProjectList() throws Exception {
        String projectId = createProject("Project");
        String sessionId = createSession(projectId, "Disposable session");
        createSession(projectId, "Remaining session");

        mockMvc.perform(delete("/api/projects/{projectId}/sessions/{sessionId}", projectId, sessionId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{projectId}/sessions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(hasItem(sessionId))));
    }

    @Test
    void deleteSessionRejectsCrossProjectSession() throws Exception {
        String projectA = createProject("Project A");
        String projectB = createProject("Project B");
        String sessionB = createSession(projectB, "Other project session");

        mockMvc.perform(delete("/api/projects/{projectId}/sessions/{sessionId}", projectA, sessionB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/{projectId}/sessions", projectB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(sessionB)));
    }

    @Test
    void deleteSessionRemovesSessionResearchTraceData() throws Exception {
        String projectId = createProject("Project");
        String sessionId = createSession(projectId, "Trace-heavy session");
        long legacySessionId = jdbcTemplate.queryForObject("""
                insert into chat_session(session_key)
                values (?)
                returning id
                """, Long.class, sessionId);
        jdbcTemplate.update("""
                insert into chat_message(session_id, role, content)
                values (?, 'USER', 'Question')
                """, legacySessionId);
        jdbcTemplate.update("""
                insert into retrieval_trace(session_id, query_text)
                values (?, 'query')
                """, legacySessionId);
        jdbcTemplate.update("""
                insert into memory_entry(
                    session_id, source_kind, topic, summary, source_message_start_id, source_message_end_id
                )
                values (?, 'session', 'Topic', 'Summary', 1, 1)
                """, legacySessionId);
        jdbcTemplate.update("""
                insert into assistant_answer(
                    id, project_id, session_id, question, answer, answer_mode, evidence_state
                )
                values ('answer-delete-test', ?, ?, 'Question', 'Answer', 'LOCAL_WEAK_EVIDENCE', 'WEAK')
                """, projectId, sessionId);
        jdbcTemplate.update("""
                insert into evidence_source(id, project_id, answer_id, quote, snippet, strength)
                values ('evidence-delete-test', ?, 'answer-delete-test', 'Quote', 'Snippet', 'weak')
                """, projectId);
        jdbcTemplate.update("""
                insert into knowledge_candidate(id, project_id, session_id, answer_id, content)
                values ('candidate-delete-test', ?, ?, 'answer-delete-test', 'Candidate')
                """, projectId, sessionId);
        jdbcTemplate.update("""
                insert into stream_event_record(id, project_id, session_id, run_id, event_type)
                values ('event-delete-test', ?, ?, 'run-delete-test', 'run.started')
                """, projectId, sessionId);

        mockMvc.perform(delete("/api/projects/{projectId}/sessions/{sessionId}", projectId, sessionId))
                .andExpect(status().isNoContent());

        assertTableCount("research_session", "id", sessionId, 0);
        assertTableCount("assistant_answer", "id", "answer-delete-test", 0);
        assertTableCount("evidence_source", "id", "evidence-delete-test", 0);
        assertTableCount("knowledge_candidate", "id", "candidate-delete-test", 0);
        assertTableCount("stream_event_record", "id", "event-delete-test", 0);
        assertNumericTableCount("chat_session", "id", legacySessionId, 0);
        assertNumericTableCount("chat_message", "session_id", legacySessionId, 0);
        assertNumericTableCount("retrieval_trace", "session_id", legacySessionId, 0);
        assertNumericTableCount("memory_entry", "session_id", legacySessionId, 0);
    }

    @Test
    void listsAssistantMessagesWithAnswerIdForFeedbackAfterRestart() throws Exception {
        String projectId = createProject("Project");
        String sessionId = createSession(projectId, "Feedback session");
        long legacySessionId = jdbcTemplate.queryForObject("""
                insert into chat_session(session_key)
                values (?)
                returning id
                """, Long.class, sessionId);
        jdbcTemplate.update("""
                insert into chat_message(session_id, role, content, answer_mode)
                values (?, 'USER', 'Question', null)
                """, legacySessionId);
        jdbcTemplate.update("""
                insert into chat_message(session_id, role, content, answer_mode)
                values (?, 'ASSISTANT', 'Answer with evidence', 'LOCAL_EVIDENCE')
                """, legacySessionId);
        jdbcTemplate.update("""
                insert into assistant_answer(
                    id, project_id, session_id, run_id, question, answer, answer_mode, evidence_state
                )
                values ('answer-feedback-history', ?, ?, 'run-feedback-history', 'Question', 'Answer with evidence', 'LOCAL_EVIDENCE', 'SUFFICIENT')
                """, projectId, sessionId);

        mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/messages", projectId, sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role == 'assistant')].answerId", hasItem("answer-feedback-history")))
                .andExpect(jsonPath("$[?(@.role == 'assistant')].runId", hasItem("run-feedback-history")));
    }

    private String createProject(String topic) throws Exception {
        String responseBody = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "%s",
                                  "summary": "summary"
                                }
                                """.formatted(topic)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.id");
    }

    private String createSession(String projectId, String title) throws Exception {
        String responseBody = mockMvc.perform(post("/api/projects/{projectId}/sessions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value("continue"))
                .andExpect(jsonPath("$.lastMessageAt").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.id");
    }

    private void assertTableCount(String tableName, String columnName, String value, int expected) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                value
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }

    private void assertNumericTableCount(String tableName, String columnName, long value, int expected) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                value
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }
}
