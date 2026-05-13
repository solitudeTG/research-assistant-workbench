package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.chat.dto.ProjectMessageRequest;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagResult;
import com.researchassistant.support.PostgresIntegrationTest;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchPort;
import com.researchassistant.websearch.WebSearchResult;
import com.researchassistant.rag.RagChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectAgentRoutingTest extends PostgresIntegrationTest {

    @Autowired
    private SupervisorService supervisorService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkbenchEventPublisher eventPublisher;

    @MockBean
    private PaperRagService paperRagService;

    @MockBean
    private MemoryRecallPort memoryRecallPort;

    @MockBean
    private WebSearchPort webSearchPort;

    @MockBean
    private ProjectAgentToolLoop projectAgentToolLoop;

    @MockBean
    private MultiAgentPlanExecuteLoop multiAgentPlanExecuteLoop;

    @SpyBean
    private MultiAgentWorkflowDecider multiAgentWorkflowDecider;

    @MockBean
    private PlanExecuteFacade planExecuteFacade;

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Test
    void simpleGreetingSkipsPaperRagAndWebSearch() {
        ResearchSessionRecord session = createSession();
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "你好，我在。你可以问项目资料，也可以让我联网补充。",
                        new RagResult("你好", List.of(), List.of()),
                        null,
                        null,
                        List.of()
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest("你好", List.of(), true, false, "local_first")
        );

        verify(memoryRecallPort, never()).recall(anyLong(), anyString(), anyInt());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        verify(webSearchPort, never()).search(anyString(), anyInt());
        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(multiAgentWorkflowDecider).decide("你好", true);
        verify(multiAgentPlanExecuteLoop, never()).run(
                anyLong(),
                anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE")
                .containsEntry("evidence_state", "NONE")
                .containsEntry("answer", "你好，我在。你可以问项目资料，也可以让我联网补充。");
    }

    @Test
    void complexResearchRequestUsesPlanExecuteAndPersistsPlanAnswer() {
        ResearchSessionRecord session = createSession();
        String question = "Compare these papers rigorously and audit the evidence";
        MultiAgentWorkflowDecision decision = planDecision(false);
        doReturn(decision).when(multiAgentWorkflowDecider).decide(question, true);
        when(multiAgentPlanExecuteLoop.run(
                anyLong(),
                eq(question),
                org.mockito.ArgumentMatchers.any(),
                eq(true),
                eq(decision)
        )).thenReturn(planResult(
                null,
                AnswerMode.LOCAL_EVIDENCE,
                "Audited synthesis from plan-execute."
        ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop, never()).run(org.mockito.ArgumentMatchers.any());
        verify(multiAgentPlanExecuteLoop).run(
                anyLong(),
                eq(question),
                org.mockito.ArgumentMatchers.any(),
                eq(true),
                eq(decision)
        );
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "LOCAL_EVIDENCE")
                .containsEntry("evidence_state", "SUFFICIENT")
                .containsEntry("answer", "Audited synthesis from plan-execute.");
    }

    @Test
    void documentFormatRequestUsesComposerOutputAsPersistedAnswer() {
        ResearchSessionRecord session = createSession();
        String question = "Write a Markdown report from the project evidence";
        MultiAgentWorkflowDecision decision = planDecision(true);
        doReturn(decision).when(multiAgentWorkflowDecider).decide(question, false);
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "Project report",
                "# Project report\n\nAudited document body.",
                List.of(new DocumentDraft.Section("Summary", "Audited document body."))
        );
        when(multiAgentPlanExecuteLoop.run(
                anyLong(),
                eq(question),
                org.mockito.ArgumentMatchers.any(),
                eq(false),
                eq(decision)
        )).thenReturn(planResult(
                draft,
                AnswerMode.LOCAL_EVIDENCE,
                "Fallback synthesis should not be persisted when a draft exists."
        ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );

        verify(projectAgentToolLoop, never()).run(org.mockito.ArgumentMatchers.any());
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "LOCAL_EVIDENCE")
                .containsEntry("evidence_state", "SUFFICIENT")
                .containsEntry("answer", "# Project report\n\nAudited document body.");
    }

    @Test
    void auditDowngradeControlsPersistedAnswerModeAndEvidenceState() {
        ResearchSessionRecord session = createSession();
        String question = "Compare these papers and reject unsupported claims";
        MultiAgentWorkflowDecision decision = planDecision(false);
        doReturn(decision).when(multiAgentWorkflowDecider).decide(question, true);
        when(multiAgentPlanExecuteLoop.run(
                anyLong(),
                eq(question),
                org.mockito.ArgumentMatchers.any(),
                eq(true),
                eq(decision)
        )).thenReturn(planResult(
                null,
                AnswerMode.REFUSAL,
                "Unsupported claims must be refused."
        ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop, never()).run(org.mockito.ArgumentMatchers.any());
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "REFUSAL")
                .containsEntry("evidence_state", "NONE")
                .containsEntry("answer", "Unsupported claims must be refused.");
    }

    @Test
    void explicitWebQuestionCallsTavilyWithoutWaitingForPaperRagFailure() {
        ResearchSessionRecord session = createSession();
        String question = "联网查一下 Tavily API 怎么接入";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Tavily 可以通过 Search API 接入。",
                        new RagResult(question, List.of(), List.of()),
                        new WebSearchResult(
                                question,
                                List.of(new WebSearchHit(
                                        "Tavily API",
                                        "https://docs.tavily.com",
                                        "Tavily search API documentation.",
                                        0.91
                                )),
                                "tavily",
                                false,
                                ""
                        ),
                        null,
                        List.of("tavily_web_search")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(webSearchPort, never()).search(anyString(), anyInt());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "WEB_SUPPLEMENT")
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer", "Tavily 可以通过 Search API 接入。");
    }

    @Test
    void weatherQuestionUsesMainAgentToolLoopAndPersistsWebTelemetry() {
        ResearchSessionRecord session = createSession();
        String question = "你能帮我去查询一下深圳今天的天气吗";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProjectAgentRun(
                        "深圳今天多云，气温约 26 摄氏度。",
                        new RagResult(question, List.of(), List.of()),
                        new WebSearchResult(
                                question,
                                List.of(new WebSearchHit("深圳天气", "https://weather.example", "深圳多云，26 摄氏度。", 0.9)),
                                "tavily",
                                false,
                                ""
                        ),
                        null,
                        List.of("tavily_web_search")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.argThat(request ->
                question.equals(request.question())
                        && request.allowWebSupplement()
                        && session.id().equals(request.memory().sessionKey())
        ));
        verify(webSearchPort, never()).search(anyString(), anyInt());
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "WEB_SUPPLEMENT")
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer", "深圳今天多云，气温约 26 摄氏度。");
        assertRetrievalTools(response.streamRunId(), response.answerId(), "tavily_web_search");
    }

    @Test
    void explicitWebQuestionWithDegradedSearchPersistsRecoveryAnswer() {
        ResearchSessionRecord session = createSession();
        String question = "search Tavily API docs";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "The web search is currently degraded. Tavily timeout I do not have web results to cite.",
                        new RagResult(question, List.of(), List.of()),
                        new WebSearchResult(question, List.of(), "tavily", true, "Tavily timeout"),
                        null,
                        List.of("tavily_web_search")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(webSearchPort, never()).search(anyString(), anyInt());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        Map<String, Object> answerRow = answerRow(response.answerId());
        assertThat(answerRow)
                .containsEntry("answer_mode", "WEB_SUPPLEMENT")
                .containsEntry("evidence_state", "WEAK");
        assertThat((String) answerRow.get("answer"))
                .contains("web search")
                .contains("Tavily timeout")
                .doesNotContain("paper evidence");
    }

    @Test
    void localQuestionCallsPaperRagOnly() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 22L);
        String question = "这篇论文的核心方法是什么？";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "论文方法来自本地证据。",
                        new RagResult(
                                question,
                                List.of(22L),
                                List.of(new RagChunk(100L, 22L, 0, "Local method evidence.", 0.92))
                        ),
                        null,
                        null,
                        List.of("paper_rag")
                ));

        supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        verify(memoryRecallPort, never()).recall(anyLong(), anyString(), anyInt());
        verify(webSearchPort, never()).search(anyString(), anyInt());
    }

    @Test
    void localLatestQuestionDoesNotCallWebSearchWhenWebSupplementDisabled() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 24L);
        String question = "paper latest comparison";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Only local paper evidence was used.",
                        new RagResult(question, List.of(24L), List.of(new RagChunk(101L, 24L, 0, "Paper comparison.", 0.89))),
                        null,
                        null,
                        List.of("paper_rag")
                ));

        supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        verify(webSearchPort, never()).search(anyString(), anyInt());
    }

    @Test
    void historyQuestionCallsMemoryRecall() {
        ResearchSessionRecord session = createSession();
        String question = "\u6211\u4eec\u4e4b\u524d\u8ba8\u8bba\u8fc7\u4ec0\u4e48?";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "我们之前讨论过主 Agent 工具选择。",
                        new RagResult(question, List.of(), List.of()),
                        null,
                        new MemoryRecallResult(question, List.of()),
                        List.of("memory_recall")
                ));

        supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(memoryRecallPort, never()).recall(anyLong(), anyString(), anyInt());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        verify(webSearchPort, never()).search(anyString(), anyInt());
    }

    @Test
    void planningQuestionUsesMainAgentToolLoop() {
        ResearchSessionRecord session = createSession();
        String question = "please make a research plan";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Here is the project research plan.",
                        new RagResult(question, List.of(), List.of()),
                        null,
                        null,
                        List.of()
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(planExecuteFacade, never()).execute(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(webSearchPort, never()).search(anyString(), anyInt());
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE")
                .containsEntry("evidence_state", "NONE")
                .containsEntry("answer", "Here is the project research plan.");
    }

    @Test
    void mixedLocalAndLatestQuestionCallsPaperRagAndTavily() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 23L);
        String question = "这篇论文的方法和最新工作相比有什么不足？";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "本地论文证据和联网结果共同提供了最新对比。",
                        new RagResult(question, List.of(23L), List.of(new RagChunk(102L, 23L, 0, "Local method evidence.", 0.91))),
                        new WebSearchResult(
                                question,
                                List.of(new WebSearchHit("Recent work", "https://example.test", "Recent comparison.", 0.8)),
                                "tavily",
                                false,
                                ""
                        ),
                        null,
                        List.of("paper_rag", "tavily_web_search")
                ));

        supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        verify(webSearchPort, never()).search(anyString(), anyInt());
    }

    @Test
    void naturalLatestResearchQuestionCallsPaperRagAndTavilyWhenWebAllowed() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 25L);
        String question = "What are the latest findings?";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Latest findings require local and web evidence.",
                        new RagResult(question, List.of(25L), List.of(new RagChunk(103L, 25L, 0, "Local finding.", 0.9))),
                        new WebSearchResult(
                                question,
                                List.of(new WebSearchHit("Latest findings", "https://example.test/latest", "Fresh result.", 0.8)),
                                "tavily",
                                false,
                                ""
                        ),
                        new MemoryRecallResult(question, List.of()),
                        List.of("memory_recall", "paper_rag", "tavily_web_search")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        assertRetrievalTools(response.streamRunId(), response.answerId(), "memory_recall", "paper_rag", "tavily_web_search");
    }

    @Test
    void workingMemorySummaryPublishesMemoryHitEvenWhenL3RecallIsEmpty() {
        ResearchSessionRecord session = createSession();
        jdbcTemplate.update("""
                insert into chat_session(session_key, rolling_summary, salient_facts_json, compressed_rounds_json)
                values (?, ?, ?::jsonb, '[]'::jsonb)
                """,
                session.id(),
                "User previously compared two satellite communication papers.",
                "[\"Discussed two IEEE papers\"]"
        );
        String question = "What did we discuss before?";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "You previously compared two satellite communication papers.",
                        new RagResult(question, List.of(), List.of()),
                        null,
                        new MemoryRecallResult(question, List.of()),
                        List.of("memory_recall")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        WorkbenchEvent memoryHit = eventPublisher.readRunEventsAfter(response.streamRunId(), null).stream()
                .filter(candidate -> "memory.hit".equals(candidate.eventType().wireName()))
                .filter(candidate -> response.answerId().equals(candidate.answerId()))
                .findFirst()
                .orElseThrow();
        JsonNode data = objectMapper.valueToTree(memoryHit.payload()).get("data");
        assertThat(data.get("memoryLayer").asText()).isEqualTo("L1");
        assertThat(data.get("label").asText()).isEqualTo("工作记忆");
        assertThat(data.get("snippet").asText()).contains("satellite communication papers");
    }

    @Test
    void toolsUsedOmitsPaperRagWhenNoScopedPaperEvidenceExists() {
        ResearchSessionRecord session = createSession();
        String question = "What are the latest findings?";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Latest findings require web evidence.",
                        new RagResult(question, List.of(), List.of()),
                        new WebSearchResult(
                                question,
                                List.of(new WebSearchHit("Latest findings", "https://example.test/latest", "Fresh result.", 0.8)),
                                "tavily",
                                false,
                                ""
                        ),
                        new MemoryRecallResult(question, List.of()),
                        List.of("memory_recall", "tavily_web_search")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        verify(projectAgentToolLoop).run(org.mockito.ArgumentMatchers.any());
        verify(memoryRecallPort, never()).recall(anyLong(), anyString(), anyInt());
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList(), anyInt());
        verify(webSearchPort, never()).search(anyString(), anyInt());
        assertRetrievalTools(response.streamRunId(), response.answerId(), "memory_recall", "tavily_web_search");
    }

    private ProjectAgentRun agentRun(String answer,
                                     RagResult ragResult,
                                     WebSearchResult webSearchResult,
                                     MemoryRecallResult memoryRecallResult,
                                     List<String> toolsUsed) {
        return new ProjectAgentRun(answer, ragResult, webSearchResult, memoryRecallResult, toolsUsed);
    }

    private MultiAgentWorkflowDecision planDecision(boolean documentRequest) {
        return new MultiAgentWorkflowDecision(
                MultiAgentExecutionMode.PLAN_EXECUTE,
                documentRequest ? "document_composition_request" : "complex_research_request",
                true,
                true,
                documentRequest
        );
    }

    private MultiAgentPlanExecuteResult planResult(
            DocumentDraft documentDraft,
            AnswerMode recommendedAnswerMode,
            String finalSynthesisContext
    ) {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("Supported claim"),
                List.of("paper: content=Supported claim"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
        AuditVerdict verdict = new AuditVerdict(
                recommendedAnswerMode == AnswerMode.LOCAL_EVIDENCE ? "pass" : "requires_revision",
                recommendedAnswerMode.name(),
                recommendedAnswerMode == AnswerMode.LOCAL_EVIDENCE ? List.of() : List.of("Unsupported claim"),
                List.of(),
                List.of()
        );
        return new MultiAgentPlanExecuteResult(
                new MultiAgentPlan(MultiAgentExecutionMode.PLAN_EXECUTE, "test plan", List.of()),
                packet,
                verdict,
                documentDraft,
                finalSynthesisContext
        );
    }

    private ResearchSessionRecord createSession() {
        ProjectRecord project = projectRepository.createProject("F012 project", "agentic routing");
        return projectRepository.createSession(project.id(), "F012 session");
    }

    private Map<String, Object> answerRow(String answerId) {
        return jdbcTemplate.queryForMap("""
                select answer, answer_mode, evidence_state
                from assistant_answer
                where id = ?
                """, answerId);
    }

    private void insertIndexedProjectSource(String projectId, long indexedDocumentId) {
        jdbcTemplate.update("""
                insert into source_document(id, project_id, type, title, status, indexed_document_id)
                values (?, ?, 'pdf', 'Indexed F012 source', 'indexed', ?)
                """, java.util.UUID.randomUUID().toString(), projectId, indexedDocumentId);
    }

    private void assertRetrievalTools(String runId, String answerId, String... expectedTools) {
        WorkbenchEvent event = eventPublisher.readRunEventsAfter(runId, null).stream()
                .filter(candidate -> "retrieval.completed".equals(candidate.eventType().wireName()))
                .filter(candidate -> answerId.equals(candidate.answerId()))
                .findFirst()
                .orElseThrow();
        JsonNode payload = objectMapper.valueToTree(event.payload());
        assertThat(payload.get("toolsUsed"))
                .extracting(JsonNode::asText)
                .containsExactly(expectedTools);
    }
}
