package com.researchassistant.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.chat.dto.ProjectMessageRequest;
import com.researchassistant.chat.dto.ProjectMessageResponse;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.orchestrator.MemoryRecallPort;
import com.researchassistant.orchestrator.ProjectAgentRequest;
import com.researchassistant.orchestrator.ProjectAgentRun;
import com.researchassistant.orchestrator.ProjectAgentToolLoop;
import com.researchassistant.orchestrator.SupervisorService;
import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import com.researchassistant.support.PostgresIntegrationTest;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchPort;
import com.researchassistant.websearch.WebSearchResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProjectEvidenceBoundaryTest extends PostgresIntegrationTest {

    @Autowired
    private SupervisorService supervisorService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private WorkbenchEventPublisher eventPublisher;

    @SpyBean
    private EvidenceSourceRepository evidenceSourceRepository;

    @MockBean
    private PaperRagService paperRagService;

    @MockBean
    private MemoryRecallPort memoryRecallPort;

    @MockBean
    private WebSearchPort webSearchPort;

    @MockBean
    private ProjectAgentToolLoop projectAgentToolLoop;

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @BeforeEach
    void useDefaultProjectAgentToolLoopFixture() {
        when(projectAgentToolLoop.run(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> defaultAgentRun(invocation.getArgument(0)));
    }

    @Test
    void strongPaperEvidenceProducesSufficientLocalEvidenceAndPersistsPaperSources() throws Exception {
        ResearchSessionRecord session = createSession();
        String sourceId = insertIndexedProjectSource(session.projectId(), 11L);
        RagResult ragResult = new RagResult(
                "What does the paper prove?",
                List.of(11L),
                List.of(new RagChunk(101L, 11L, 3, "The paper proves bounded retrieval drift.", 0.91))
        );
        when(memoryRecallPort.recall(anyLong(), eq("What does the paper prove?"), anyInt()))
                .thenReturn(new MemoryRecallResult("What does the paper prove?", List.of()));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(11L)), eq(5))).thenReturn(ragResult);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("The paper proves bounded retrieval drift.");

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest("What does the paper prove?", List.of(), false, false, "local_first")
        );
        String answerId = response.answerId();

        Map<String, Object> answerRow = answerRow(answerId);
        assertThat(answerRow.get("evidence_state")).isEqualTo("SUFFICIENT");
        assertThat(answerRow.get("answer_mode")).isEqualTo("LOCAL_EVIDENCE");

        List<Map<String, Object>> evidenceRows = evidenceRows(answerId);
        assertThat(evidenceRows).hasSize(1);
        assertThat(evidenceRows.get(0))
                .containsEntry("source_type", "paper")
                .containsEntry("source_id", sourceId)
                .containsEntry("strength", "strong");
        assertThat(evidenceRows.get(0).get("snippet")).asString()
                .contains("bounded retrieval drift");
        JsonNode metadata = citationMeta(evidenceRows.get(0));
        assertThat(metadata.get("answerId").asText()).isEqualTo(answerId);
        assertThat(metadata.get("runId").asText()).isEqualTo(response.streamRunId());
        assertThat(metadata.get("origin").asText()).isEqualTo("paper_rag");
        assertThat(metadata.get("tool").asText()).isEqualTo("paper_rag_retrieve");

        assertEvidenceEvent(response.streamRunId(), answerId, "SUFFICIENT", "LOCAL_EVIDENCE", 1, List.of("paper"));
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "PAPER_RAG_ONLY", 1, 0, false);
    }

    @Test
    void weakLocalEvidenceWithWebAllowedProducesWeakWebSupplement() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 12L);
        String question = "What does the method imply?";
        RagResult ragResult = new RagResult(
                question,
                List.of(12L),
                List.of(new RagChunk(102L, 12L, 1, "The method may reduce manual evidence review.", 0.42))
        );
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(12L)), eq(5))).thenReturn(ragResult);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Local evidence is weak; web supplement is required.");

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );
        String answerId = response.answerId();

        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer_mode", "WEB_SUPPLEMENT");
        assertThat(evidenceRows(answerId)).hasSize(1);
        assertEvidenceEvent(response.streamRunId(), answerId, "WEAK", "WEB_SUPPLEMENT", 1, List.of("paper"));
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "WEB_SUPPLEMENT", 1, 0, true);
    }

    @Test
    void explicitWebSearchPersistsWebEvidenceMetadata() throws Exception {
        ResearchSessionRecord session = createSession();
        String question = "search current Tavily API evidence";
        when(webSearchPort.search(eq(question), eq(5)))
                .thenReturn(new WebSearchResult(
                        question,
                        List.of(new WebSearchHit(
                                "Tavily Search API",
                                "https://docs.tavily.com/docs/rest-api/api-reference",
                                "Tavily returns search results with titles, urls, and content snippets.",
                                0.86
                        )),
                        "tavily",
                        false,
                        ""
                ));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Tavily exposes a search API.");

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        List<Map<String, Object>> evidenceRows = evidenceRows(response.answerId());
        assertThat(evidenceRows).hasSize(1);
        assertThat(evidenceRows.get(0))
                .containsEntry("source_type", "web")
                .containsEntry("source_id", null)
                .containsEntry("snippet", "Tavily returns search results with titles, urls, and content snippets.")
                .containsEntry("strength", "strong");
        JsonNode metadata = citationMeta(evidenceRows.get(0));
        assertThat(metadata.get("title").asText()).isEqualTo("Tavily Search API");
        assertThat(metadata.get("url").asText()).isEqualTo("https://docs.tavily.com/docs/rest-api/api-reference");
        assertThat(metadata.get("provider").asText()).isEqualTo("tavily");
        assertThat(metadata.get("snippet").asText()).contains("content snippets");
        assertEvidenceEvent(response.streamRunId(), response.answerId(), "WEAK", "WEB_SUPPLEMENT", 1, List.of("web"));
    }

    @Test
    void mixedPaperAndWebAnswerPersistsSeparateEvidenceSourceTypes() {
        ResearchSessionRecord session = createSession();
        String sourceId = insertIndexedProjectSource(session.projectId(), 121L);
        String question = "paper latest comparison search";
        when(memoryRecallPort.recall(anyLong(), eq(question), anyInt()))
                .thenReturn(new MemoryRecallResult(question, List.of()));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(121L)), eq(5)))
                .thenReturn(new RagResult(
                        question,
                        List.of(121L),
                        List.of(new RagChunk(301L, 121L, 0, "Paper baseline evidence.", 0.9))
                ));
        when(webSearchPort.search(eq(question), eq(5)))
                .thenReturn(new WebSearchResult(
                        question,
                        List.of(new WebSearchHit(
                                "Recent benchmark",
                                "https://example.test/recent-benchmark",
                                "Recent web benchmark evidence.",
                                0.72
                        )),
                        "tavily",
                        false,
                        ""
                ));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Paper and web evidence are separated.");

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        List<Map<String, Object>> evidenceRows = evidenceRows(response.answerId());
        assertThat(evidenceRows).hasSize(2);
        assertThat(evidenceRows)
                .extracting(row -> row.get("source_type"))
                .containsExactlyInAnyOrder("paper", "web");
        Map<String, Object> paperRow = evidenceRows.stream()
                .filter(row -> "paper".equals(row.get("source_type")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> webRow = evidenceRows.stream()
                .filter(row -> "web".equals(row.get("source_type")))
                .findFirst()
                .orElseThrow();
        assertThat(paperRow)
                .containsEntry("source_id", sourceId)
                .containsEntry("snippet", "Paper baseline evidence.");
        assertThat(webRow)
                .containsEntry("source_id", null)
                .containsEntry("snippet", "Recent web benchmark evidence.");
        assertEvidenceEvent(response.streamRunId(), response.answerId(), "WEAK", "WEB_SUPPLEMENT", 2, List.of("paper", "web"));
    }

    @Test
    void degradedWebSearchDoesNotInsertFakeEvidenceRows() {
        ResearchSessionRecord session = createSession();
        String question = "search current unavailable web";
        when(webSearchPort.search(eq(question), eq(5)))
                .thenReturn(WebSearchResult.degraded(question, "tavily", "Tavily timeout"));

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), true, false, "local_first")
        );

        assertThat(answerRow(response.answerId()))
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer_mode", "WEB_SUPPLEMENT");
        assertThat(evidenceRows(response.answerId())).isEmpty();
    }

    @Test
    void noEvidenceWithWebDisabledProducesNoneRefusal() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 13L);
        String question = "What evidence supports the claim?";
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(13L)), eq(5)))
                .thenReturn(new RagResult(question, List.of(13L), List.of()));

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );
        String answerId = response.answerId();

        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "NONE")
                .containsEntry("answer_mode", "REFUSAL");
        assertThat(evidenceRows(answerId)).isEmpty();
        assertEvidenceEvent(response.streamRunId(), answerId, "NONE", "REFUSAL", 0, List.of());
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "PAPER_RAG_ONLY", 0, 0, false);
    }

    @Test
    void weakLocalEvidenceWithWebDisabledProducesWeakLocalWeakEvidence() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 14L);
        String question = "What does weak paper evidence support?";
        RagResult ragResult = new RagResult(
                question,
                List.of(14L),
                List.of(new RagChunk(104L, 14L, 0, "A weak but relevant paper chunk.", 0.48))
        );
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(14L)), eq(5))).thenReturn(ragResult);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Weak local evidence answer.");

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );
        String answerId = response.answerId();

        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE");
        assertThat(evidenceRows(answerId)).hasSize(1);
        assertEvidenceEvent(response.streamRunId(), answerId, "WEAK", "LOCAL_WEAK_EVIDENCE", 1, List.of("paper"));
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "PAPER_RAG_ONLY", 1, 0, false);
    }

    @Test
    void lowScoredScopedPaperEvidenceStillProducesWeakLocalEvidenceInsteadOfRefusal() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 141L);
        String question = "How does the paper distinguish colocated users?";
        RagResult ragResult = new RagResult(
                question,
                List.of(141L),
                List.of(new RagChunk(1411L, 141L, 13, "Doppler frequency diversity can separate colocated users.", 0.21))
        );
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(141L)), eq(5))).thenReturn(ragResult);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Doppler frequency diversity separates colocated users.");

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );
        String answerId = response.answerId();

        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE");
        assertThat(evidenceRows(answerId)).hasSize(1);
        assertEvidenceEvent(response.streamRunId(), answerId, "WEAK", "LOCAL_WEAK_EVIDENCE", 1, List.of("paper"));
    }

    @Test
    void projectAnswerWithoutScopedPaperMappingDoesNotSearchGlobalLegacyRag() {
        ResearchSessionRecord session = createSession();
        when(memoryRecallPort.recall(anyLong(), eq("Could an unrelated global paper answer this?"), anyInt()))
                .thenReturn(new MemoryRecallResult("Could an unrelated global paper answer this?", List.of()));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of()), eq(5)))
                .thenReturn(new RagResult(
                        "Could an unrelated global paper answer this?",
                        List.of(),
                        List.of(new RagChunk(999L, 999L, 0, "Unrelated global legacy chunk.", 0.99))
                ));

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest("Could an unrelated global paper answer this?", List.of(), false, false, "local_first")
        );
        String answerId = response.answerId();

        verify(paperRagService, never()).retrieve(anyLong(), anyString(), eq(List.of()), eq(5));
        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "NONE")
                .containsEntry("answer_mode", "REFUSAL");
        assertThat(evidenceRows(answerId)).isEmpty();
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "NO_RETRIEVAL", 0, 0, false);
    }

    @Test
    void memoryRecallWithoutScopedPaperMappingEmitsMemoryRecallOnlyButNoEvidence() {
        ResearchSessionRecord session = createSession();
        var memoryEntry = new com.researchassistant.memory.MemoryEntry(
                17L,
                88L,
                "COMPACTION",
                "Prior local context",
                "Prior project context exists but is not current paper evidence.",
                List.of("Prior context exists"),
                List.of(),
                List.of("prior", "context"),
                3L,
                4L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String question = "what did we discuss before without mapped papers";
        when(memoryRecallPort.recall(anyLong(), eq(question), anyInt()))
                .thenReturn(new MemoryRecallResult(
                        question,
                        List.of(new MemoryRecallHit(memoryEntry, 0.91))
                ));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of()), eq(5)))
                .thenReturn(new RagResult(
                        "Use prior context without mapped papers",
                        List.of(),
                        List.of(new RagChunk(1001L, 1001L, 0, "Global chunk must not be used.", 0.99))
                ));

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );
        String answerId = response.answerId();

        verify(paperRagService, never()).retrieve(anyLong(), anyString(), eq(List.of()), eq(5));
        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE");
        assertThat(evidenceRows(answerId)).isEmpty();
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "MEMORY_RECALL_ONLY", 0, 1, false);
    }

    @Test
    void l3MemoryRecallEnrichesRetrievalButDoesNotCountAsCurrentPaperEvidence() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 15L);
        var memoryEntry = new com.researchassistant.memory.MemoryEntry(
                7L,
                99L,
                "COMPACTION",
                "Prior discussion",
                "We discussed a promising hypothesis earlier.",
                List.of("Hypothesis was promising"),
                List.of(),
                List.of("hypothesis"),
                1L,
                2L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        MemoryRecallResult memory = new MemoryRecallResult(
                "paper latest comparison with prior context",
                List.of(new MemoryRecallHit(memoryEntry, 0.95))
        );
        when(memoryRecallPort.recall(anyLong(), eq("paper latest comparison with prior context"), anyInt()))
                .thenReturn(memory);
        when(webSearchPort.search(eq("paper latest comparison with prior context"), eq(5)))
                .thenReturn(WebSearchResult.degraded("paper latest comparison with prior context", "tavily", "Tavily timeout"));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(15L)), eq(5)))
                .thenReturn(new RagResult("paper latest comparison with prior context", List.of(15L), List.of()));

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(
                        "paper latest comparison with prior context",
                        List.of(),
                        true,
                        false,
                        "local_first")
        );
        String answerId = response.answerId();

        verify(paperRagService).retrieve(anyLong(), org.mockito.ArgumentMatchers.contains("Historical context"), eq(List.of(15L)), eq(5));
        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer_mode", "WEB_SUPPLEMENT");
        assertThat(evidenceRows(answerId)).isEmpty();
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "WEB_SUPPLEMENT", 0, 1, true);
    }

    @Test
    void answerEvidenceEndpointReturnsOnlyEvidenceForRequestedProject() throws Exception {
        ResearchSessionRecord session = createSession();
        String sourceId = insertIndexedProjectSource(session.projectId(), 16L);
        RagResult ragResult = new RagResult(
                "Which source is persisted?",
                List.of(16L),
                List.of(new RagChunk(106L, 16L, 2, "Persisted project evidence snippet.", 0.88))
        );
        when(memoryRecallPort.recall(anyLong(), eq("Which source is persisted?"), anyInt()))
                .thenReturn(new MemoryRecallResult("Which source is persisted?", List.of()));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(16L)), eq(5))).thenReturn(ragResult);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Evidence endpoint answer.");
        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest("Which source is persisted?", List.of(), false, false, "local_first")
        );
        ProjectRecord otherProject = projectRepository.createProject("Other F007 project", "isolation");

        mockMvc.perform(get("/api/projects/{projectId}/answers/{answerId}/evidence", session.projectId(), response.answerId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(session.projectId()))
                .andExpect(jsonPath("$[0].answerId").value(response.answerId()))
                .andExpect(jsonPath("$[0].sourceType").value("paper"))
                .andExpect(jsonPath("$[0].sourceId").value(sourceId))
                .andExpect(jsonPath("$[0].snippet").value("Persisted project evidence snippet."));

        mockMvc.perform(get("/api/projects/{projectId}/answers/{answerId}/evidence", otherProject.id(), response.answerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void mappedProjectFileSourceScopesPaperRagToOnlyProjectIndexedDocument() {
        ResearchSessionRecord session = createSession();
        long mappedDocumentId = insertResearchDocument("mapped-paper.txt");
        insertIndexedProjectSource(session.projectId(), mappedDocumentId);
        long globalDocumentId = insertResearchDocument("global-paper.txt");
        when(memoryRecallPort.recall(anyLong(), eq("Which mapped source should answer?"), anyInt()))
                .thenReturn(new MemoryRecallResult("Which mapped source should answer?", List.of()));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(mappedDocumentId)), eq(5)))
                .thenReturn(new RagResult(
                        "Which mapped source should answer?",
                        List.of(mappedDocumentId),
                        List.of(new RagChunk(200L, mappedDocumentId, 0, "Mapped project evidence.", 0.9))
                ));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Mapped answer.");

        supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest("Which mapped source should answer?", List.of(), false, false, "local_first")
        );

        verify(paperRagService).retrieve(anyLong(), anyString(), eq(List.of(mappedDocumentId)), eq(5));
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), eq(List.of(globalDocumentId)), eq(5));
        verify(paperRagService, never()).retrieve(anyLong(), anyString(), eq(List.of()), eq(5));
    }

    @Test
    void answerAndEvidencePersistenceRollsBackWhenEvidenceInsertFails() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 17L);
        when(memoryRecallPort.recall(anyLong(), eq("Persist atomically?"), anyInt()))
                .thenReturn(new MemoryRecallResult("Persist atomically?", List.of()));
        when(paperRagService.retrieve(anyLong(), anyString(), eq(List.of(17L)), eq(5)))
                .thenReturn(new RagResult(
                        "Persist atomically?",
                        List.of(17L),
                        List.of(new RagChunk(107L, 17L, 0, "Atomic evidence.", 0.9))
                ));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Atomic answer.");
        doThrow(new IllegalStateException("evidence insert failed"))
                .when(evidenceSourceRepository)
                .insertPaperSources(eq(session.projectId()), anyString(), anyList(), anyMap());
        clearInvocations(eventPublisher);

        assertThatThrownBy(() -> supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest("Persist atomically?", List.of(), false, false, "local_first")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evidence insert failed");

        assertThat(answerCount(session.projectId(), "Persist atomically?")).isZero();
        assertThat(evidenceRowsForQuestion(session.projectId(), "Persist atomically?")).isEmpty();
        assertThat(chatMessageCount(session.id())).isZero();
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(WorkbenchEvent.class);
        verify(eventPublisher, atLeast(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(event -> event.eventType().wireName())
                .doesNotContain("retrieval.completed", "evidence.evaluated", "answer.completed", "run.completed")
                .contains("run.failed");
    }

    @Test
    void memoryRecallRouteHandlesNullMemoryRecallAsWeakMemoryAnswer() {
        ResearchSessionRecord session = createSession();
        insertIndexedProjectSource(session.projectId(), 18L);
        String question = "what did we discuss before about null retrieval values";
        when(memoryRecallPort.recall(anyLong(), eq(question), anyInt()))
                .thenReturn(null);

        ProjectMessageResponse response = supervisorService.answerProject(
                session.projectId(),
                session.id(),
                new ProjectMessageRequest(question, List.of(), false, false, "local_first")
        );
        String answerId = response.answerId();

        assertThat(answerRow(answerId))
                .containsEntry("evidence_state", "WEAK")
                .containsEntry("answer_mode", "LOCAL_WEAK_EVIDENCE");
        assertThat(evidenceRows(answerId)).isEmpty();
        assertRetrievalCompletedEvent(response.streamRunId(), answerId, "MEMORY_RECALL_ONLY", 0, 0, false);
    }

    private ResearchSessionRecord createSession() {
        ProjectRecord project = projectRepository.createProject("F007 project", "evidence boundary");
        return projectRepository.createSession(project.id(), "F007 session");
    }

    private ProjectAgentRun defaultAgentRun(ProjectAgentRequest request) {
        String question = request.question();
        String normalized = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
        List<String> toolsUsed = new ArrayList<>();

        MemoryRecallResult memoryRecallResult = null;
        boolean memoryQuestion = normalized.contains("previous")
                || normalized.contains("history")
                || normalized.contains("prior context")
                || normalized.contains("before")
                || normalized.contains("discuss")
                || normalized.contains("之前")
                || normalized.contains("历史");
        if (memoryQuestion) {
            memoryRecallResult = memoryRecallPort.recall(request.memory().sessionId(), question, 4);
            toolsUsed.add("memory_recall");
        }

        RagResult ragResult = new RagResult(question, request.evidenceScope().indexedDocumentIds(), List.of());
        boolean paperQuestion = normalized.contains("paper")
                || normalized.contains("evidence")
                || normalized.contains("source")
                || normalized.contains("claim")
                || normalized.contains("论文")
                || normalized.contains("证据");
        if (request.evidenceScope().hasScopedPaperEvidence() && (!memoryQuestion || paperQuestion)) {
            String ragQuery = memoryRecallResult == null || memoryRecallResult.isEmpty()
                    ? question
                    : question + "\nHistorical context:\n" + memoryRecallResult.contextBlock();
            ragResult = paperRagService.retrieve(
                    request.memory().sessionId(),
                    ragQuery,
                    request.evidenceScope().indexedDocumentIds(),
                    5
            );
            toolsUsed.add("paper_rag");
        } else if (paperQuestion && !memoryQuestion) {
            toolsUsed.add("paper_rag");
        }

        WebSearchResult webSearchResult = null;
        boolean explicitWeb = normalized.contains("search")
                || normalized.contains("web")
                || normalized.contains("latest")
                || normalized.contains("current")
                || normalized.contains("联网")
                || normalized.contains("最新");
        if (explicitWeb && request.allowWebSupplement()) {
            webSearchResult = webSearchPort.search(question, 5);
            toolsUsed.add("tavily_web_search");
        }

        String answer = "Project agent answer.";
        if (webSearchResult != null && webSearchResult.degraded()) {
            answer = "The web search is currently degraded. " + webSearchResult.message();
        }
        return new ProjectAgentRun(answer, ragResult, webSearchResult, memoryRecallResult, toolsUsed);
    }

    private Map<String, Object> answerRow(String answerId) {
        return jdbcTemplate.queryForMap("""
                select evidence_state, answer_mode
                from assistant_answer
                where id = ?
                """, answerId);
    }

    private List<Map<String, Object>> evidenceRows(String answerId) {
        return jdbcTemplate.queryForList("""
                select source_type, source_id, snippet, strength, relevance_score, feedback_score, citation_meta_json
                from evidence_source
                where answer_id = ?
                order by created_at, id
                """, answerId);
    }

    private JsonNode citationMeta(Map<String, Object> evidenceRow) throws Exception {
        return objectMapper.readTree(String.valueOf(evidenceRow.get("citation_meta_json")));
    }

    private String insertIndexedProjectSource(String projectId, long indexedDocumentId) {
        String sourceId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into source_document(id, project_id, type, title, status, indexed_document_id)
                values (?, ?, 'pdf', 'Indexed project source', 'indexed', ?)
                """, sourceId, projectId, indexedDocumentId);
        return sourceId;
    }

    private long insertResearchDocument(String title) {
        Number id = jdbcTemplate.queryForObject("""
                insert into research_document(title, original_file_name, storage_path, status)
                values (?, ?, ?, 'INDEXED')
                returning id
                """, Number.class, title, title, "target/test-storage/" + title);
        return id.longValue();
    }

    private int answerCount(String projectId, String question) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from assistant_answer
                where project_id = ?
                  and question = ?
                """, Integer.class, projectId, question);
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> evidenceRowsForQuestion(String projectId, String question) {
        return jdbcTemplate.queryForList("""
                select e.*
                from evidence_source e
                join assistant_answer a
                  on a.project_id = e.project_id
                 and a.id = e.answer_id
                where a.project_id = ?
                  and a.question = ?
                """, projectId, question);
    }

    private int chatMessageCount(String sessionKey) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from chat_message m
                join chat_session s on s.id = m.session_id
                where s.session_key = ?
                """, Integer.class, sessionKey);
        return count == null ? 0 : count;
    }

    private void assertEvidenceEvent(
            String runId,
            String answerId,
            String evidenceState,
            String outputMode,
            int citationCount,
            List<String> sourceTypes) {
        WorkbenchEvent event = publishedEvent(runId, answerId, "evidence.evaluated");
        assertThat(event.payload())
                .containsEntry("evidenceState", evidenceState)
                .containsEntry("outputMode", outputMode)
                .containsEntry("citationCount", citationCount);
        JsonNode payload = objectMapper.valueToTree(event.payload());
        assertThat(payload.get("sourceTypes"))
                .extracting(JsonNode::asText)
                .containsExactlyElementsOf(sourceTypes);
    }

    private void assertRetrievalCompletedEvent(
            String runId,
            String answerId,
            String retrievalMode,
            int paperEvidenceCount,
            int memoryRecallCount,
            boolean webSupplementAllowed) {
        WorkbenchEvent event = publishedEvent(runId, answerId, "retrieval.completed");
        JsonNode payload = objectMapper.valueToTree(event.payload());
        assertThat(payload.get("retrievalMode").asText()).isEqualTo(retrievalMode);
        assertThat(payload.get("paperEvidenceCount").asInt()).isEqualTo(paperEvidenceCount);
        assertThat(payload.get("memoryRecallCount").asInt()).isEqualTo(memoryRecallCount);
        assertThat(payload.get("webSupplementAllowed").asBoolean()).isEqualTo(webSupplementAllowed);
    }

    private WorkbenchEvent publishedEvent(String runId, String answerId, String eventType) {
        return eventPublisher.readRunEventsAfter(runId, null).stream()
                .filter(event -> eventType.equals(event.eventType().wireName()))
                .filter(event -> answerId.equals(event.answerId()))
                .findFirst()
                .orElseThrow();
    }
}
