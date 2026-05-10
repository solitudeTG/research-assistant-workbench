package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.support.PostgresIntegrationTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.root=target/test-storage",
        "app.f006.project-run-event-flow-test=true"
})
class ProjectRunEventFlowTest extends PostgresIntegrationTest {

    private static final List<String> REQUIRED_EVENT_ORDER = List.of(
            "run.started",
            "agent.plan.created",
            "agent.step.started",
            "retrieval.started",
            "retrieval.completed",
            "evidence.evaluated",
            "answer.delta",
            "answer.completed",
            "run.completed"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private WorkbenchEventPublisher eventPublisher;

    @SpyBean
    private SupervisorService supervisorService;

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private org.springframework.ai.chat.client.ChatClient chatClient;

    @Test
    void projectScopedMessageCreatesRunResponseAndOrderedWorkbenchEvents() throws Exception {
        ResearchSessionRecord session = createResearchSession();

        JsonNode response = postProjectMessage(session);
        String runId = response.get("streamRunId").asText();

        assertThat(runId).isNotBlank();
        assertThat(response.get("messageId").asText()).isNotBlank();
        assertThat(response.get("answerId").asText()).isNotBlank();
        assertThat(response.get("sseUrl").asText())
                .isEqualTo("/api/projects/" + session.projectId()
                        + "/sessions/" + session.id()
                        + "/runs/" + runId
                        + "/events");
        assertThat(assistantAnswerCount(response.get("answerId").asText(), session.projectId(), session.id()))
                .isEqualTo(1);

        List<WorkbenchEvent> events = eventPublisher.readRunEventsAfter(runId, null);
        assertThat(events)
                .extracting(event -> event.eventType().wireName())
                .containsExactlyElementsOf(REQUIRED_EVENT_ORDER);
        assertThat(events)
                .allSatisfy(event -> {
                    assertThat(event.projectId()).isEqualTo(session.projectId());
                    assertThat(event.sessionId()).isEqualTo(session.id());
                    assertThat(event.runId()).isEqualTo(runId);
                    assertThat(event.actor()).isNotBlank();
                    assertThat(event.payload()).isNotNull();
                });
        WorkbenchEvent evidenceEvent = events.stream()
                .filter(event -> "evidence.evaluated".equals(event.eventType().wireName()))
                .findFirst()
                .orElseThrow();
        assertThat(evidenceEvent.payload())
                .containsKeys("evidenceState", "outputMode", "citationCount")
                .doesNotContainKey("answerMode");
    }

    @Test
    void rejectsCrossProjectSessionBeforePublishingEventsOrAppendingLegacyMemory() throws Exception {
        ResearchSessionRecord session = createResearchSession();
        ProjectRecord otherProject = projectRepository.createProject("Other project", "boundary");
        clearInvocations(eventPublisher, supervisorService);

        mockMvc.perform(post("/api/projects/{projectId}/sessions/{sessionId}/messages",
                        otherProject.id(), session.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Should not run"
                                }
                                """))
                .andExpect(status().isNotFound());

        verify(supervisorService, never()).answerProject(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
        assertThat(legacyChatSessionCount(session.id())).isZero();
    }

    @Test
    void rejectsSourceFiltersBeforePublishingEventsOrInvokingAnswerPath() throws Exception {
        ResearchSessionRecord session = createResearchSession();
        clearInvocations(eventPublisher, supervisorService);

        mockMvc.perform(post("/api/projects/{projectId}/sessions/{sessionId}/messages",
                        session.projectId(), session.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Should not run",
                                  "sourceFilters": ["7b34f6e8-7f02-4bb4-95b5-0da71dd40393"]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(supervisorService, never()).answerProject(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
        assertThat(legacyChatSessionCount(session.id())).isZero();
    }

    @Test
    void projectRunSseReplayUsesF004EventFormatAndSameEventNames() throws Exception {
        ResearchSessionRecord session = createResearchSession();
        String runId = postProjectMessage(session).get("streamRunId").asText();

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events",
                        session.projectId(), session.id(), runId))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sseBody = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(sseBody).contains("id:");
        assertThat(sseBody).contains("\"eventType\":\"run.started\"");
        assertThat(sseBody).contains("\"payload\":");

        AtomicReference<Integer> previousIndex = new AtomicReference<>(-1);
        REQUIRED_EVENT_ORDER.forEach(eventName -> {
            int index = sseBody.indexOf("event:" + eventName);
            assertThat(index)
                    .as("SSE event %s should be present", eventName)
                    .isGreaterThan(previousIndex.get());
            previousIndex.set(index);
        });
    }

    private JsonNode postProjectMessage(ResearchSessionRecord session) throws Exception {
        String requestBody = """
                {
                  "question": "What should we inspect next?",
                  "answerMode": "local_first"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/sessions/{sessionId}/messages",
                        session.projectId(), session.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").isNotEmpty())
                .andExpect(jsonPath("$.answerId").isNotEmpty())
                .andExpect(jsonPath("$.streamRunId").isNotEmpty())
                .andExpect(jsonPath("$.sseUrl").isNotEmpty())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResearchSessionRecord createResearchSession() {
        ProjectRecord project = projectRepository.createProject("F006 test project", "event flow");
        return projectRepository.createSession(project.id(), "F006 session");
    }

    private int assistantAnswerCount(String answerId, String projectId, String sessionId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from assistant_answer
                where id = ?
                  and project_id = ?
                  and session_id = ?
                """, Integer.class, answerId, projectId, sessionId);
        return count == null ? 0 : count;
    }

    private int legacyChatSessionCount(String sessionKey) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from chat_session
                where session_key = ?
                """, Integer.class, sessionKey);
        return count == null ? 0 : count;
    }
}
