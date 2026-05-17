package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import com.researchassistant.support.PostgresIntegrationTest;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
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

    @SpyBean
    private ProjectAgentToolLoop projectAgentToolLoop;

    @SpyBean
    private MultiAgentWorkflowDecider multiAgentWorkflowDecider;

    @SpyBean
    private MultiAgentPlanExecuteLoop multiAgentPlanExecuteLoop;

    @MockBean
    private DeepResearchAgent deepResearchAgent;

    @MockBean
    private EvidenceGateAgent evidenceGateAgent;

    @MockBean
    private EvidenceAuditAgent evidenceAuditAgent;

    @MockBean
    private DocumentComposerAgent documentComposerAgent;

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private org.springframework.ai.chat.client.ChatClient chatClient;

    @BeforeEach
    void stubDefaultToolLoopAnswer() {
        doReturn(new ProjectAgentRun(
                "Default project answer for event-flow tests.",
                new RagResult("What should we inspect next?", List.of(), List.of()),
                null,
                null,
                List.of()
        )).when(projectAgentToolLoop).run(any(ProjectAgentRequest.class));
        org.mockito.Mockito.when(evidenceGateAgent.gate(
                        org.mockito.ArgumentMatchers.anyString(),
                        any(CuratedEvidenceSet.class)
                ))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @AfterEach
    void resetToolLoopSpy() {
        reset(projectAgentToolLoop, multiAgentWorkflowDecider, multiAgentPlanExecuteLoop,
                deepResearchAgent, evidenceGateAgent, evidenceAuditAgent, documentComposerAgent);
    }

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
                .containsSubsequence(REQUIRED_EVENT_ORDER);
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
    void projectScopedMessagePublishesTraceDetailsFromAgentRunData() throws Exception {
        ResearchSessionRecord session = createResearchSession();
        insertIndexedSource(session.projectId(), "source-paper-1", 10L);
        doReturn(new ProjectAgentRun(
                "First paragraph from the current evidence.\n\nSecond paragraph uses a web supplement.",
                new RagResult(
                        "trace details",
                        List.of(10L),
                        List.of(new RagChunk(101L, 10L, 2, "Paper evidence chunk for trace details.", 0.87))
                ),
                new WebSearchResult(
                        "trace details",
                        List.of(new WebSearchHit(
                                "Trace provider result",
                                "https://example.test/trace",
                                "Web evidence snippet for trace details.",
                                0.76
                        )),
                        "tavily",
                        false,
                        ""
                ),
                new MemoryRecallResult(
                        "trace details",
                        List.of(
                                new MemoryRecallHit(memoryEntry("Trace memory", "Memory summary used by the trace event."), 0.66),
                                new MemoryRecallHit(memoryEntry("Second trace memory", "Second memory summary used by the trace event."), 0.44)
                        )
                ),
                List.of("paper_rag", "tavily_web_search", "memory_recall")
        )).when(projectAgentToolLoop).run(any(ProjectAgentRequest.class));

        String runId = postProjectMessage(session).get("streamRunId").asText();
        List<WorkbenchEvent> events = eventPublisher.readRunEventsAfter(runId, null);

        assertThat(events)
                .extracting(event -> event.eventType().wireName())
                .containsSubsequence(REQUIRED_EVENT_ORDER)
                .contains("retrieval.hit", "memory.hit", "memory.completed", "evidence.gap.detected", "answer.delta");

        WorkbenchEvent paperHit = firstEvent(events, "retrieval.hit", "paper");
        Map<String, Object> paperData = dataOf(paperHit);
        assertThat(paperHit.actor()).isEqualTo("retrieval-agent");
        assertThat(paperData)
                .containsEntry("sourceType", "paper")
                .containsEntry("sourceId", "source-paper-1")
                .containsEntry("rank", 1)
                .containsEntry("retrievalMode", "vector")
                .containsEntry("score", 0.87);
        assertThat((String) paperData.get("snippet")).contains("Paper evidence chunk");

        WorkbenchEvent webHit = firstEvent(events, "retrieval.hit", "web");
        Map<String, Object> webData = dataOf(webHit);
        assertThat(webData)
                .containsEntry("sourceType", "web")
                .containsEntry("url", "https://example.test/trace")
                .containsEntry("title", "Trace provider result")
                .containsEntry("provider", "tavily")
                .containsEntry("rank", 1)
                .containsEntry("retrievalMode", "web");

        List<WorkbenchEvent> memoryHits = events.stream()
                .filter(event -> "memory.hit".equals(event.eventType().wireName()))
                .toList();
        assertThat(memoryHits).hasSize(2);
        WorkbenchEvent memoryHit = memoryHits.get(0);
        assertThat(memoryHit.actor()).isEqualTo("memory_worker");
        assertThat(dataOf(memoryHit))
                .containsEntry("memoryLayer", "L3")
                .containsEntry("sourceType", "long_term_memory")
                .containsEntry("sourceId", "7")
                .containsEntry("contextOnly", true)
                .containsEntry("label", "\u957f\u671f\u8bb0\u5fc6\u53ec\u56de")
                .containsEntry("score", 0.66);

        WorkbenchEvent memoryCompleted = events.stream()
                .filter(event -> "memory.completed".equals(event.eventType().wireName()))
                .findFirst()
                .orElseThrow();
        assertThat(dataOf(memoryCompleted)).containsEntry("hitCount", 2);

        WorkbenchEvent gap = events.stream()
                .filter(event -> "evidence.gap.detected".equals(event.eventType().wireName()))
                .findFirst()
                .orElseThrow();
        assertThat(dataOf(gap))
                .containsKeys("claim", "reason", "severity")
                .containsEntry("severity", "medium");

        List<WorkbenchEvent> answerDeltas = events.stream()
                .filter(event -> "answer.delta".equals(event.eventType().wireName()))
                .toList();
        assertThat(answerDeltas).hasSize(2);
        assertThat(answerDeltas)
                .allSatisfy(event -> assertThat(event.payload()).containsKeys("delta", "text", "index"));
        assertThat(answerDeltas.get(0).payload())
                .containsEntry("delta", "First paragraph from the current evidence.\n\n")
                .containsEntry("text", "First paragraph from the current evidence.\n\n")
                .containsEntry("index", 0);
    }

    @Test
    void projectScopedSimpleMessageDoesNotPublishEvidenceGapWithoutResearchEvidencePath() throws Exception {
        ResearchSessionRecord session = createResearchSession();
        doReturn(new ProjectAgentRun(
                "你好，我在。",
                null,
                null,
                null,
                List.of()
        )).when(projectAgentToolLoop).run(any(ProjectAgentRequest.class));

        String runId = postProjectMessage(session).get("streamRunId").asText();

        assertThat(eventPublisher.readRunEventsAfter(runId, null))
                .extracting(event -> event.eventType().wireName())
                .doesNotContain("evidence.gap.detected");
    }

    @Test
    void projectSessionMessagesReturnPersistedConversationAndRejectCrossProjectReads() throws Exception {
        ResearchSessionRecord session = createResearchSession();
        ProjectRecord otherProject = projectRepository.createProject("Other project", "message boundary");

        postProjectMessage(session);

        mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/messages",
                        session.projectId(), session.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("What should we inspect next?"))
                .andExpect(jsonPath("$[0].sessionId").value(session.id()))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].content").isNotEmpty())
                .andExpect(jsonPath("$[1].sessionId").value(session.id()));

        mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/messages",
                        otherProject.id(), session.id()))
                .andExpect(status().isNotFound());
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

        result.getAsyncResult(5_000);
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

    @Test
    void planExecuteRequestPublishesSerialMultiAgentTraceAndSseReplay() throws Exception {
        ResearchSessionRecord session = createResearchSession();
        String question = "Compare these papers rigorously and write a Markdown report";
        MultiAgentWorkflowDecision decision = new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                "complex_research_request",
                true,
                true,
                true
        );
        ResearchPacket packet = researchPacket(question);
        AuditVerdict verdict = auditVerdict();
        DocumentDraft draft = documentDraft();
        doReturn(decision).when(multiAgentWorkflowDecider).decide(question, true);
        doReturn(packet).when(deepResearchAgent).research(
                anyLong(),
                eq(question),
                any(),
                eq(true)
        );
        doReturn(verdict).when(evidenceAuditAgent).audit(
                eq(question),
                eq("Supported claim"),
                any(ResearchPacket.class)
        );
        doReturn(draft).when(documentComposerAgent).compose(
                eq("markdown"),
                eq(question),
                any(ResearchPacket.class),
                eq(verdict)
        );

        JsonNode response = postProjectMessage(session, """
                {
                  "question": "Compare these papers rigorously and write a Markdown report",
                  "answerMode": "local_first",
                  "allowWebSupplement": true
                }
                """);
        String runId = response.get("streamRunId").asText();
        List<WorkbenchEvent> events = eventPublisher.readRunEventsAfter(runId, null);

        WorkbenchEvent modeSelected = firstTraceStep(events, "mode-selection", "completed");
        assertThat(dataOf(modeSelected))
                .containsEntry("mode", "PLAN_EXECUTE")
                .containsEntry("reason", "complex_research_request");

        WorkbenchEvent planCreated = events.stream()
                .filter(event -> "agent.plan.created".equals(event.eventType().wireName()))
                .filter(event -> event.payload().containsKey("data"))
                .findFirst()
                .orElseThrow();
        assertThat(dataOf(planCreated))
                .containsEntry("mode", "PLAN_EXECUTE")
                .containsEntry("summary", "Serial plan-execute workflow: complex_research_request")
                .containsEntry("execution", "serial");
        assertThat((List<?>) dataOf(planCreated).get("steps")).hasSize(3);

        WorkbenchEvent deepStarted = firstTraceStep(events, "deep-research", "running");
        WorkbenchEvent deepCompleted = firstTraceStep(events, "deep-research", "completed");
        WorkbenchEvent auditStarted = firstTraceStep(events, "evidence-audit", "running");
        WorkbenchEvent auditCompleted = firstTraceStep(events, "evidence-audit", "completed");
        WorkbenchEvent composerStarted = firstTraceStep(events, "document-composer", "running");
        WorkbenchEvent composerCompleted = firstTraceStep(events, "document-composer", "completed");

        assertThat(indexOf(events, modeSelected)).isLessThan(indexOf(events, planCreated));
        assertThat(indexOf(events, planCreated)).isLessThan(indexOf(events, deepStarted));
        assertThat(indexOf(events, deepStarted)).isLessThan(indexOf(events, deepCompleted));
        assertThat(indexOf(events, deepCompleted)).isLessThan(indexOf(events, auditStarted));
        assertThat(indexOf(events, auditStarted)).isLessThan(indexOf(events, auditCompleted));
        assertThat(indexOf(events, auditCompleted)).isLessThan(indexOf(events, composerStarted));
        assertThat(indexOf(events, composerStarted)).isLessThan(indexOf(events, composerCompleted));

        assertThat(deepStarted.actor()).isEqualTo("deep_research_agent");
        assertThat(dataOf(deepCompleted))
                .containsEntry("paperEvidenceCount", 1)
                .containsEntry("webEvidenceCount", 1)
                .containsEntry("memoryContextCount", 1);
        assertThat(auditCompleted.actor()).isEqualTo("evidence_audit_agent");
        assertThat(dataOf(auditCompleted))
                .containsEntry("verdict", "pass_with_cautions")
                .containsEntry("recommendedAnswerMode", "LOCAL_WEAK_EVIDENCE")
                .containsEntry("unsupportedClaimCount", 1)
                .containsEntry("sourcePolicyIssueCount", 1)
                .containsEntry("requiredRevisionCount", 1)
                .doesNotContainKey("unsupportedClaims");
        assertThat(composerCompleted.actor()).isEqualTo("document_composer_agent");
        assertThat(dataOf(composerCompleted))
                .containsEntry("format", "markdown")
                .containsEntry("title", "Plan Execute Report")
                .containsEntry("sectionCount", 2)
                .doesNotContainKey("body");

        assertThat(events)
                .extracting(event -> event.eventType().wireName())
                .containsSubsequence(
                        "agent.step.completed",
                        "agent.plan.created",
                        "agent.step.started",
                        "agent.step.completed",
                        "agent.step.started",
                        "agent.step.completed",
                        "agent.step.started",
                        "agent.step.completed",
                        "evidence.evaluated",
                        "answer.completed",
                        "run.completed"
                );

        MvcResult sseResult = mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events",
                        session.projectId(), session.id(), runId))
                .andExpect(request().asyncStarted())
                .andReturn();
        sseResult.getAsyncResult(5_000);
        String sseBody = mockMvc.perform(asyncDispatch(sseResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(sseBody)
                .contains("event:agent.plan.created")
                .contains("event:agent.step.started")
                .contains("event:agent.step.completed")
                .contains("\"mode\":\"PLAN_EXECUTE\"")
                .contains("\"reason\":\"complex_research_request\"")
                .contains("\"agentRole\":\"deep_research_agent\"")
                .contains("\"agentRole\":\"evidence_audit_agent\"")
                .contains("\"agentRole\":\"document_composer_agent\"")
                .contains("\"execution\":\"serial\"")
                .doesNotContain("\"execution\":\"parallel\"");
    }

    private JsonNode postProjectMessage(ResearchSessionRecord session) throws Exception {
        String requestBody = """
                {
                  "question": "What should we inspect next?",
                  "answerMode": "local_first"
                }
                """;

        return postProjectMessage(session, requestBody);
    }

    private JsonNode postProjectMessage(ResearchSessionRecord session, String requestBody) throws Exception {
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

    private ResearchPacket researchPacket(String question) {
        return new ResearchPacket(
                question,
                List.of("Supported claim"),
                List.of("paper: content=Supported claim appears in peer-reviewed evidence context."),
                List.of("web: title=Recent result url=https://example.test score=0.80 snippet=Supported web context"),
                List.of("memory: topic=Workflow summary score=0.70 summary=Prior context"),
                List.of(),
                List.of("Evidence is weak and should be qualified."),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
    }

    private AuditVerdict auditVerdict() {
        return new AuditVerdict(
                "pass_with_cautions",
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                List.of("Unsupported claim should only appear as a count in trace payload."),
                List.of("Use local evidence only."),
                List.of("Qualify weak evidence.")
        );
    }

    private DocumentDraft documentDraft() {
        return new DocumentDraft(
                "markdown",
                "Plan Execute Report",
                "# Plan Execute Report\n\nAudited document body.",
                List.of(
                        new DocumentDraft.Section("Summary", "Audited body."),
                        new DocumentDraft.Section("Evidence", "Bounded details.")
                )
        );
    }

    private ResearchSessionRecord createResearchSession() {
        ProjectRecord project = projectRepository.createProject("F006 test project", "event flow");
        return projectRepository.createSession(project.id(), "F006 session");
    }

    private void insertIndexedSource(String projectId, String sourceId, long indexedDocumentId) {
        jdbcTemplate.update("""
                insert into source_document(id, project_id, type, title, status, indexed_document_id)
                values (?, ?, 'paper', 'Trace Paper', 'indexed', ?)
                """, sourceId, projectId, indexedDocumentId);
    }

    private WorkbenchEvent firstEvent(List<WorkbenchEvent> events, String eventType, String sourceType) {
        return events.stream()
                .filter(event -> eventType.equals(event.eventType().wireName()))
                .filter(event -> sourceType == null || sourceType.equals(dataOf(event).get("sourceType")))
                .findFirst()
                .orElseThrow();
    }

    private WorkbenchEvent firstTraceStep(List<WorkbenchEvent> events, String stepId, String status) {
        return events.stream()
                .filter(event -> event.payload().containsKey("step"))
                .filter(event -> {
                    Object stepValue = event.payload().get("step");
                    if (!(stepValue instanceof Map<?, ?> step)) {
                        return false;
                    }
                    return stepId.equals(step.get("stepId")) && status.equals(step.get("status"));
                })
                .findFirst()
                .orElseThrow();
    }

    private int indexOf(List<WorkbenchEvent> events, WorkbenchEvent event) {
        return events.indexOf(event);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(WorkbenchEvent event) {
        assertThat(event.payload()).containsKey("data");
        return (Map<String, Object>) event.payload().get("data");
    }

    private MemoryEntry memoryEntry(String topic, String summary) {
        return new MemoryEntry(
                7L,
                42L,
                "COMPACTION",
                topic,
                summary,
                List.of("Trace detail comes from memory recall."),
                List.of(),
                List.of("trace"),
                1L,
                2L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
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
