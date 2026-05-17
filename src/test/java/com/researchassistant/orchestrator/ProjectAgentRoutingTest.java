package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.chat.dto.ProjectMessageRequest;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceCitationSource;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryEntryDraft;
import com.researchassistant.memory.MemoryEntryRepository;
import com.researchassistant.memory.MemoryRecallHit;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
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

    @Autowired
    private MemoryEntryRepository memoryEntryRepository;

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
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE")
                .containsEntry("evidence_state", "NONE")
                .containsEntry("answer", "你好，我在。你可以问项目资料，也可以让我联网补充。");
        assertModeSelection(response.streamRunId(), response.answerId(), "REACT", "fallback");
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
                eq(decision),
                org.mockito.ArgumentMatchers.any()
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
                eq(decision),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE")
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer", "Audited synthesis from plan-execute.");
        assertThat(evidenceSourceCount(response.answerId())).isZero();
        assertRetrievalCitationTelemetry(response.streamRunId(), response.answerId(), 0);
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
                eq(decision),
                org.mockito.ArgumentMatchers.any()
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
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE")
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer", "# Project report\n\nAudited document body.");
        assertThat(evidenceSourceCount(response.answerId())).isZero();
    }

    @Test
    void planExecutePersistsAcceptedCitationSources() {
        ResearchSessionRecord session = createSession();
        String sourceId = insertIndexedProjectSource(session.projectId(), 10L);
        String question = "Write a citable Markdown report from the project evidence and web context";
        MultiAgentWorkflowDecision decision = planDecision(true);
        doReturn(decision).when(multiAgentWorkflowDecider).decide(question, true);
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "Citable report",
                "# Citable report\n\nAdaptive beamforming reduces interference.",
                List.of()
        );
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("Adaptive beamforming reduces interference."),
                List.of("paper: documentId=10 chunkIndex=2 score=0.91 content=Adaptive beamforming reduces interference."),
                List.of("web: title=Fresh context url=https://example.test score=0.72 snippet=Recent context confirms the research direction."),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name(),
                List.of(
                        EvidenceCitationSource.paper(
                                "Adaptive beamforming reduces interference.",
                                10L,
                                1002L,
                                2,
                                0.91
                        ),
                        EvidenceCitationSource.web(
                                "Recent context confirms the research direction.",
                                "Fresh context",
                                "https://example.test",
                                "tavily",
                                1,
                                0.72
                        )
                )
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass",
                AnswerMode.LOCAL_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        );
        when(multiAgentPlanExecuteLoop.run(
                anyLong(),
                eq(question),
                org.mockito.ArgumentMatchers.any(),
                eq(true),
                eq(decision),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new MultiAgentPlanExecuteResult(
                new MultiAgentPlan(MultiAgentExecutionMode.PLAN_EXECUTE, "test plan", List.of()),
                packet,
                verdict,
                draft,
                "Fallback synthesis should not be persisted."
        ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "LOCAL_EVIDENCE")
                .containsEntry("evidence_state", "SUFFICIENT")
                .containsEntry("answer", "# Citable report\n\nAdaptive beamforming reduces interference.");
        assertThat(evidenceRows(response.answerId()))
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("source_type", "paper")
                        .containsEntry("source_id", sourceId)
                        .containsEntry("snippet", "Adaptive beamforming reduces interference."))
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("source_type", "web")
                        .containsEntry("snippet", "Recent context confirms the research direction."));
        assertRetrievalCitationTelemetry(response.streamRunId(), response.answerId(), 2);
        assertEvidenceEvaluatedTelemetry(response.streamRunId(), response.answerId(), 2, "paper", "web");
    }

    @Test
    void planExecuteWebOnlyCitationsStayWeakAndDoNotGenerateCandidates() {
        ResearchSessionRecord session = createSession();
        String question = "Write a citable Markdown report from web context only";
        MultiAgentWorkflowDecision decision = planDecision(true);
        doReturn(decision).when(multiAgentWorkflowDecider).decide(question, true);
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "Web-only report",
                "# Web-only report\n\nA web result suggests the field is moving quickly.",
                List.of()
        );
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("A web result suggests the field is moving quickly."),
                List.of(),
                List.of("web: title=Fresh context url=https://example.test score=0.72 snippet=The field is moving quickly."),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name(),
                List.of(EvidenceCitationSource.web(
                        "The field is moving quickly.",
                        "Fresh context",
                        "https://example.test",
                        "tavily",
                        1,
                        0.72
                ))
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass",
                AnswerMode.LOCAL_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        );
        when(multiAgentPlanExecuteLoop.run(
                anyLong(),
                eq(question),
                org.mockito.ArgumentMatchers.any(),
                eq(true),
                eq(decision),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new MultiAgentPlanExecuteResult(
                new MultiAgentPlan(MultiAgentExecutionMode.PLAN_EXECUTE, "test plan", List.of()),
                packet,
                verdict,
                draft,
                "Fallback synthesis should not be persisted."
        ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, true, "allow_web")
        );

        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "WEB_SUPPLEMENT")
                .containsEntry("evidence_state", "WEAK");
        assertThat(evidenceRows(response.answerId()))
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("source_type", "web"));
        assertThat(candidateRows(response.answerId())).isEmpty();
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
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
                eq(decision),
                org.mockito.ArgumentMatchers.any()
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
    void l3MemoryRecallPublishesRankedToolRecallContextOnlyTrace() {
        ResearchSessionRecord session = createSession();
        String question = "What prior memory should guide this answer?";
        MemoryRecallResult recallResult = new MemoryRecallResult(question, List.of(
                new MemoryRecallHit(memoryEntry(71L, "Prior scope", "Use local evidence first."), 0.82),
                new MemoryRecallHit(memoryEntry(72L, "Prior constraint", "Do not treat memory as citation evidence."), 0.67)
        ));
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Prior memory is context only.",
                        new RagResult(question, List.of(), List.of()),
                        null,
                        recallResult,
                        List.of("memory_recall")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        List<JsonNode> memoryHits = eventPublisher.readRunEventsAfter(response.streamRunId(), null).stream()
                .filter(candidate -> "memory.hit".equals(candidate.eventType().wireName()))
                .filter(candidate -> response.answerId().equals(candidate.answerId()))
                .map(candidate -> objectMapper.valueToTree(candidate.payload()).get("data"))
                .filter(data -> data != null && "long_term_memory".equals(data.get("sourceType").asText()))
                .toList();
        assertThat(memoryHits).hasSize(2);
        assertThat(memoryHits.get(0).get("memoryLayer").asText()).isEqualTo("L3");
        assertThat(memoryHits.get(0).get("contextOnly").asBoolean()).isTrue();
        assertThat(memoryHits.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(memoryHits.get(0).get("injectionMode").asText()).isEqualTo("tool_recall");
        assertThat(memoryHits.get(0).get("reason").asText()).isEqualTo("memory_recall_result");
        assertThat(memoryHits.get(1).get("rank").asInt()).isEqualTo(2);
        JsonNode memoryCompleted = memoryCompletedData(response.streamRunId(), response.answerId());
        assertThat(memoryCompleted.get("longTermMemoryHitCount").asInt()).isEqualTo(2);
        assertThat(memoryCompleted.get("l3HitCount").asInt()).isEqualTo(2);
        assertThat(memoryCompleted.get("toolCalled").asBoolean()).isTrue();
        assertThat(memoryCompleted.get("contextOnly").asBoolean()).isTrue();
        assertThat(evidenceSourceCount(response.answerId())).isZero();
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
    void extractKnowledgeCandidatesCreatesPendingCandidateFromGroundedAnswer() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 26L);
        String question = "What reusable conclusion should we keep?";
        String answer = "Adaptive beamforming reduces interference in dense satellite links.\n\n"
                + "This conclusion is supported by the scoped local paper evidence.";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        answer,
                        new RagResult(
                                question,
                                List.of(26L),
                                List.of(new RagChunk(104L, 26L, 0, "Adaptive beamforming reduces interference.", 0.93))
                        ),
                        null,
                        null,
                        List.of("paper_rag")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, true, "local_first")
        );

        List<Map<String, Object>> candidates = candidateRows(response.answerId());
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0))
                .containsEntry("project_id", session.projectId())
                .containsEntry("session_id", session.id())
                .containsEntry("answer_id", response.answerId())
                .containsEntry("title", "Adaptive beamforming reduces interference in dense satellite links.")
                .containsEntry("statement", answer)
                .containsEntry("suggested_section", "confirmed_finding")
                .containsEntry("status", "pending");
        assertThat((Integer) candidates.get(0).get("evidence_count")).isEqualTo(1);
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
        assertThat(eventPublisher.readRunEventsAfter(response.streamRunId(), null))
                .filteredOn(event -> "candidate.created".equals(event.eventType().wireName()))
                .filteredOn(event -> response.answerId().equals(event.answerId()))
                .hasSize(1);
        WorkbenchEvent created = eventPublisher.readRunEventsAfter(response.streamRunId(), null).stream()
                .filter(event -> "candidate.created".equals(event.eventType().wireName()))
                .filter(event -> response.answerId().equals(event.answerId()))
                .findFirst()
                .orElseThrow();
        JsonNode payload = objectMapper.valueToTree(created.payload());
        assertThat(payload.get("statement").asText()).isEqualTo(answer);
        assertThat(payload.get("sourceTypes"))
                .extracting(JsonNode::asText)
                .containsExactly("paper");
        assertThat(payload.get("evidenceSourceIds")).hasSize(1);
    }

    @Test
    void extractKnowledgeCandidatesFlagDisabledDoesNotCreateCandidate() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 27L);
        String question = "What conclusion should stay only in the answer?";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "This answer has local evidence but candidate extraction is disabled.",
                        new RagResult(
                                question,
                                List.of(27L),
                                List.of(new RagChunk(105L, 27L, 0, "Local evidence exists.", 0.9))
                        ),
                        null,
                        null,
                        List.of("paper_rag")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        assertThat(candidateRows(response.answerId())).isEmpty();
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
    }

    @Test
    void extractKnowledgeCandidatesSkipsWeakWebSupplementAnswer() {
        ResearchSessionRecord session = createSession();
        String question = "What changed in the broader field?";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "A recent web result suggests the field is moving quickly.",
                        new RagResult(question, List.of(), List.of()),
                        new WebSearchResult(
                                question,
                                List.of(new WebSearchHit(
                                        "Recent field update",
                                        "https://example.test/field-update",
                                        "The field is moving quickly.",
                                        0.72
                                )),
                                "tavily",
                                false,
                                "ok"
                        ),
                        null,
                        List.of("tavily_web_search")
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, true, "allow_web")
        );

        assertThat(answerRow(response.answerId()))
                .containsEntry("answer_mode", "WEB_SUPPLEMENT")
                .containsEntry("evidence_state", "WEAK");
        assertThat(evidenceSourceCount(response.answerId())).isEqualTo(1);
        assertThat(candidateRows(response.answerId())).isEmpty();
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
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
        assertThat(data.get("sourceType").asText()).isEqualTo("working_memory");
        assertThat(data.get("contextOnly").asBoolean()).isTrue();
        assertThat(data.get("title").asText()).isEqualTo("Working memory");
        assertThat(data.get("rank").asInt()).isEqualTo(1);
        assertThat(data.get("injectionMode").asText()).isEqualTo("preloaded_prompt");
        assertThat(data.get("reason").asText()).isEqualTo("working_memory_window");
        assertThat(data.get("snippet").asText()).contains("satellite communication papers");
    }

    @Test
    void globalKnowledgeSnapshotPublishesL2ContextOnlyMemoryTrace() {
        ResearchSessionRecord session = createSession();
        jdbcTemplate.update("""
                insert into global_knowledge_note(note_type, content)
                values ('RESEARCH_STATE', 'Current research state prefers local-first evidence.')
                on conflict (note_type) do update set content = excluded.content
                """);
        String question = "Use the current research state.";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Use local-first evidence.",
                        new RagResult(question, List.of(), List.of()),
                        null,
                        new MemoryRecallResult(question, List.of()),
                        List.of()
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        JsonNode data = eventPublisher.readRunEventsAfter(response.streamRunId(), null).stream()
                .filter(candidate -> "memory.hit".equals(candidate.eventType().wireName()))
                .filter(candidate -> response.answerId().equals(candidate.answerId()))
                .map(candidate -> objectMapper.valueToTree(candidate.payload()).get("data"))
                .filter(candidate -> candidate != null && "global_knowledge".equals(candidate.get("sourceType").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(data.get("memoryLayer").asText()).isEqualTo("L2");
        assertThat(data.get("sourceId").asText()).isEqualTo("global_knowledge_snapshot");
        assertThat(data.get("contextOnly").asBoolean()).isTrue();
        assertThat(data.get("injectionMode").asText()).isEqualTo("preloaded_prompt");
        assertThat(data.get("reason").asText()).isEqualTo("global_cognition_snapshot");
        assertThat(data.get("snippet").asText()).contains("Research_state.md");
        JsonNode memoryCompleted = memoryCompletedData(response.streamRunId(), response.answerId());
        assertThat(memoryCompleted.get("globalKnowledgeHitCount").asInt()).isEqualTo(1);
        assertThat(memoryCompleted.get("l2HitCount").asInt()).isEqualTo(1);
        assertThat(evidenceSourceCount(response.answerId())).isZero();
    }

    @Test
    void repeatedProjectL3RecallCreatesPendingCandidateWithoutEvidenceOrConfirmedKnowledge() {
        ResearchSessionRecord session = createSession();
        String question = "Which prior project memory should become a candidate?";
        MemoryEntry memory = persistedMemoryEntry(
                "Candidate promotion memory",
                "Adaptive beamforming repeatedly appears as a stable project direction."
        );
        MemoryRecallResult recallResult = new MemoryRecallResult(question, List.of(new MemoryRecallHit(memory, 0.76)));
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        agentRun(
                                "Use the repeated L3 memory as context only.",
                                new RagResult(question, List.of(), List.of()),
                                null,
                                recallResult,
                                List.of("memory_recall")
                        ),
                        agentRun(
                                "The repeated L3 memory is still context only.",
                                new RagResult(question, List.of(), List.of()),
                                null,
                                recallResult,
                                List.of("memory_recall")
                        )
                );

        var first = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );
        assertThat(l3CandidateRows(session.projectId())).isEmpty();

        var second = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        List<Map<String, Object>> candidates = l3CandidateRows(session.projectId());
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0))
                .containsEntry("status", "pending")
                .containsEntry("source_kind", "l3_memory")
                .containsEntry("source_memory_entry_id", memory.id())
                .containsEntry("promotion_hit_count", 2)
                .containsEntry("evidence_count", 0);
        assertThat(evidenceSourceCount(first.answerId())).isZero();
        assertThat(evidenceSourceCount(second.answerId())).isZero();
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
        assertRetrievalCitationTelemetry(second.streamRunId(), second.answerId(), 0);
        assertEvidenceEvaluatedTelemetry(second.streamRunId(), second.answerId(), 0);
        assertThat(eventPublisher.readRunEventsAfter(second.streamRunId(), null).stream()
                .filter(event -> second.answerId().equals(event.answerId()))
                .anyMatch(event -> "candidate.created".equals(event.eventType().wireName()))).isTrue();
    }

    @Test
    void confirmedKnowledgeEntryPublishesL2ProjectKnowledgeMemoryTraceWithoutCitationEvidence() {
        ResearchSessionRecord session = createSession();
        String entryId = insertConfirmedKnowledgeEntry(
                session.projectId(),
                "Confirmed beamforming finding",
                "Adaptive beamforming should be treated as the stable project direction."
        );
        String question = "Use the confirmed project direction in the next answer.";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "The stable direction is adaptive beamforming.",
                        new RagResult(question, List.of(), List.of()),
                        null,
                        new MemoryRecallResult(question, List.of()),
                        List.of()
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        WorkbenchEvent memoryHit = eventPublisher.readRunEventsAfter(response.streamRunId(), null).stream()
                .filter(candidate -> "memory.hit".equals(candidate.eventType().wireName()))
                .filter(candidate -> response.answerId().equals(candidate.answerId()))
                .filter(candidate -> {
                    JsonNode data = objectMapper.valueToTree(candidate.payload()).get("data");
                    return data != null && "project_knowledge".equals(data.get("sourceType").asText());
                })
                .findFirst()
                .orElseThrow();
        JsonNode data = objectMapper.valueToTree(memoryHit.payload()).get("data");
        assertThat(data.get("memoryLayer").asText()).isEqualTo("L2");
        assertThat(data.get("sourceType").asText()).isEqualTo("project_knowledge");
        assertThat(data.get("sourceId").asText()).isEqualTo(entryId);
        assertThat(data.get("contextOnly").asBoolean()).isTrue();
        assertThat(data.get("title").asText()).isEqualTo("Confirmed beamforming finding");
        assertThat(data.get("summary").asText()).contains("Adaptive beamforming");
        assertThat(data.get("rank").asInt()).isEqualTo(1);
        assertThat(data.get("injectionMode").asText()).isEqualTo("preloaded_prompt");
        assertThat(data.get("score").asDouble()).isGreaterThan(0.0);
        assertThat(data.get("semanticScore").asDouble()).isGreaterThan(0.0);
        assertThat(data.get("evidenceScore").asDouble()).isEqualTo(1.0);
        assertThat(data.get("recencyScore").asDouble()).isGreaterThanOrEqualTo(0.0);
        assertThat(data.get("reason").asText()).isEqualTo("semantic_confirmed_project_knowledge");
        assertThat(data.get("snippet").asText()).contains("Adaptive beamforming");
        JsonNode memoryCompleted = memoryCompletedData(response.streamRunId(), response.answerId());
        assertThat(memoryCompleted.get("projectKnowledgeHitCount").asInt()).isEqualTo(1);
        assertThat(memoryCompleted.get("l2HitCount").asInt()).isEqualTo(1);
        assertThat(memoryCompleted.get("contextOnly").asBoolean()).isTrue();
        assertThat(evidenceSourceCount(response.answerId())).isZero();
        assertRetrievalCitationTelemetry(response.streamRunId(), response.answerId(), 0);
        assertEvidenceEvaluatedTelemetry(response.streamRunId(), response.answerId(), 0);
    }

    @Test
    void semanticProjectKnowledgeRecallSelectsOlderRelevantEntryForAgentPrompt() {
        ResearchSessionRecord session = createSession();
        OffsetDateTime now = OffsetDateTime.now();
        for (int index = 1; index <= 5; index++) {
            insertConfirmedKnowledgeEntryAt(
                    session.projectId(),
                    "Recent unrelated knowledge " + index,
                    "This note is about rebuild scripts, calendars, formatting, and UI maintenance.",
                    now.minusHours(index)
            );
        }
        String relevantEntryId = insertConfirmedKnowledgeEntryAt(
                session.projectId(),
                "Adaptive beamforming strategy",
                "Adaptive beamforming is the stable project direction for antenna scheduling.",
                now.minusDays(40)
        );
        String question = "How should adaptive beamforming guide the project direction?";
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(agentRun(
                        "Use adaptive beamforming as the stable direction.",
                        new RagResult(question, List.of(), List.of()),
                        null,
                        new MemoryRecallResult(question, List.of()),
                        List.of()
                ));

        var response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        var requestCaptor = forClass(ProjectAgentRequest.class);
        verify(projectAgentToolLoop).run(requestCaptor.capture());
        ProjectAgentRequest request = requestCaptor.getValue();
        assertThat(request.projectKnowledge()).hasSize(5);
        assertThat(request.projectKnowledge().get(0).id()).isEqualTo(relevantEntryId);
        assertThat(request.projectKnowledge())
                .extracting("id")
                .contains(relevantEntryId);

        JsonNode firstProjectKnowledgeHit = eventPublisher.readRunEventsAfter(response.streamRunId(), null).stream()
                .filter(candidate -> "memory.hit".equals(candidate.eventType().wireName()))
                .filter(candidate -> response.answerId().equals(candidate.answerId()))
                .map(candidate -> objectMapper.valueToTree(candidate.payload()).get("data"))
                .filter(candidate -> candidate != null && "project_knowledge".equals(candidate.get("sourceType").asText()))
                .filter(candidate -> candidate.get("rank").asInt() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(firstProjectKnowledgeHit.get("sourceId").asText()).isEqualTo(relevantEntryId);
        assertThat(firstProjectKnowledgeHit.get("reason").asText()).isEqualTo("semantic_confirmed_project_knowledge");
        assertThat(firstProjectKnowledgeHit.get("contextOnly").asBoolean()).isTrue();
        assertThat(firstProjectKnowledgeHit.get("semanticScore").asDouble()).isGreaterThan(0.0);
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

    private MemoryEntry memoryEntry(long id, String topic, String summary) {
        OffsetDateTime now = OffsetDateTime.now();
        return new MemoryEntry(
                id,
                42L,
                "COMPACTION",
                topic,
                summary,
                List.of(summary),
                List.of(),
                List.of(topic.toLowerCase()),
                1L,
                2L,
                now,
                now
        );
    }

    private MemoryEntry persistedMemoryEntry(String topic, String summary) {
        return memoryEntryRepository.append(new MemoryEntryDraft(
                null,
                "COMPACTION",
                topic,
                summary,
                List.of(summary),
                List.of(),
                List.of("adaptive", "beamforming", "candidate"),
                1L,
                2L
        ));
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
        resetGlobalKnowledgeNotes();
        ProjectRecord project = projectRepository.createProject("F012 project", "agentic routing");
        return projectRepository.createSession(project.id(), "F012 session");
    }

    private void resetGlobalKnowledgeNotes() {
        for (String noteType : List.of("USER", "SOUL", "RESEARCH_STATE")) {
            jdbcTemplate.update("""
                    insert into global_knowledge_note(note_type, content)
                    values (?, '')
                    on conflict (note_type) do update set content = ''
                    """, noteType);
        }
    }

    private Map<String, Object> answerRow(String answerId) {
        return jdbcTemplate.queryForMap("""
                select answer, answer_mode, evidence_state
                from assistant_answer
                where id = ?
                """, answerId);
    }

    private int evidenceSourceCount(String answerId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from evidence_source
                where answer_id = ?
                """, Integer.class, answerId);
        return count == null ? 0 : count;
    }

    private String insertIndexedProjectSource(String projectId, long indexedDocumentId) {
        String sourceId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into source_document(id, project_id, type, title, status, indexed_document_id)
                values (?, ?, 'pdf', 'Indexed F012 source', 'indexed', ?)
                """, sourceId, projectId, indexedDocumentId);
        return sourceId;
    }

    private List<Map<String, Object>> evidenceRows(String answerId) {
        return jdbcTemplate.queryForList("""
                select source_type, source_id, snippet
                from evidence_source
                where answer_id = ?
                order by source_type, id
                """, answerId);
    }

    private List<Map<String, Object>> candidateRows(String answerId) {
        return jdbcTemplate.queryForList("""
                select project_id, session_id, answer_id, title, statement, suggested_section, status,
                       jsonb_array_length(evidence_source_ids_json) as evidence_count
                from knowledge_candidate
                where answer_id = ?
                order by created_at, id
                """, answerId);
    }

    private List<Map<String, Object>> l3CandidateRows(String projectId) {
        return jdbcTemplate.queryForList("""
                select source_kind, source_memory_entry_id, status, promotion_hit_count,
                       jsonb_array_length(evidence_source_ids_json) as evidence_count
                from knowledge_candidate
                where project_id = ?
                  and source_kind = 'l3_memory'
                order by created_at, id
                """, projectId);
    }

    private int knowledgeEntryCount(String projectId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from knowledge_entry
                where project_id = ?
                """, Integer.class, projectId);
        return count == null ? 0 : count;
    }

    private String insertConfirmedKnowledgeEntry(String projectId, String title, String content) {
        String entryId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into knowledge_entry(id, project_id, section, title, content, evidence_status, evidence_source_ids_json)
                values (?, ?, 'confirmed_finding', ?, ?, 'confirmed', '[]'::jsonb)
                """, entryId, projectId, title, content);
        return entryId;
    }

    private String insertConfirmedKnowledgeEntryAt(String projectId, String title, String content, OffsetDateTime updatedAt) {
        String entryId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into knowledge_entry(
                    id, project_id, section, title, content, evidence_status, evidence_source_ids_json, created_at, updated_at
                )
                values (?, ?, 'confirmed_finding', ?, ?, 'confirmed', '[]'::jsonb, ?, ?)
                """, entryId, projectId, title, content, updatedAt.minusDays(1), updatedAt);
        return entryId;
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

    private void assertRetrievalCitationTelemetry(String runId, String answerId, int expectedCitationCount) {
        WorkbenchEvent event = eventPublisher.readRunEventsAfter(runId, null).stream()
                .filter(candidate -> "retrieval.completed".equals(candidate.eventType().wireName()))
                .filter(candidate -> answerId.equals(candidate.answerId()))
                .findFirst()
                .orElseThrow();
        JsonNode payload = objectMapper.valueToTree(event.payload());
        assertThat(payload.get("citationCount").asInt()).isEqualTo(expectedCitationCount);
    }

    private JsonNode memoryCompletedData(String runId, String answerId) {
        WorkbenchEvent event = eventPublisher.readRunEventsAfter(runId, null).stream()
                .filter(candidate -> "memory.completed".equals(candidate.eventType().wireName()))
                .filter(candidate -> answerId.equals(candidate.answerId()))
                .findFirst()
                .orElseThrow();
        return objectMapper.valueToTree(event.payload()).get("data");
    }

    private void assertEvidenceEvaluatedTelemetry(
            String runId,
            String answerId,
            int expectedCitationCount,
            String... expectedSourceTypes
    ) {
        WorkbenchEvent event = eventPublisher.readRunEventsAfter(runId, null).stream()
                .filter(candidate -> "evidence.evaluated".equals(candidate.eventType().wireName()))
                .filter(candidate -> answerId.equals(candidate.answerId()))
                .findFirst()
                .orElseThrow();
        JsonNode payload = objectMapper.valueToTree(event.payload());
        assertThat(payload.get("citationCount").asInt()).isEqualTo(expectedCitationCount);
        assertThat(payload.get("sourceTypes"))
                .extracting(JsonNode::asText)
                .containsExactly(expectedSourceTypes);
    }

    private void assertModeSelection(String runId, String answerId, String expectedMode, String expectedDecisionSource) {
        WorkbenchEvent event = eventPublisher.readRunEventsAfter(runId, null).stream()
                .filter(candidate -> "agent.step.completed".equals(candidate.eventType().wireName()))
                .filter(candidate -> answerId.equals(candidate.answerId()))
                .filter(candidate -> {
                    JsonNode payload = objectMapper.valueToTree(candidate.payload());
                    return "mode-selection".equals(payload.get("step").get("stepId").asText());
                })
                .findFirst()
                .orElseThrow();
        JsonNode data = objectMapper.valueToTree(event.payload()).get("data");
        assertThat(data.get("mode").asText()).isEqualTo(expectedMode);
        assertThat(data.get("decisionSource").asText()).isEqualTo(expectedDecisionSource);
        assertThat(data.has("fallbackReason")).isTrue();
        assertThat(data.has("semanticConfidence")).isTrue();
    }
}
