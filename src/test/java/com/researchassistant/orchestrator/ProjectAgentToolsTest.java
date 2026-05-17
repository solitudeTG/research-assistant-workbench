package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.events.InMemoryWorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import com.researchassistant.rag.RetrievalObservation;
import com.researchassistant.rag.RetrievalTraceContext;
import com.researchassistant.rag.ZeroHitReason;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchPort;
import com.researchassistant.websearch.WebSearchResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectAgentToolsTest {

    private final PaperRagService paperRagService = mock(PaperRagService.class);
    private final MemoryRecallPort memoryRecallPort = mock(MemoryRecallPort.class);
    private final WebSearchPort webSearchPort = mock(WebSearchPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void paperRagToolPublishesCalledAndCompletedTraceEvents() {
        InMemoryWorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "src-10"));
        when(paperRagService.retrieve(eq(42L), eq("multi agent"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class)))
                .thenReturn(ragResult(
                        "multi agent",
                        List.of(10L),
                        List.of(new RagChunk(1L, 10L, 0, "Scoped project evidence.", 0.91))
                ));
        ProjectAgentTools tools = tools(scope, publisher);

        tools.paperRag("multi agent", 5);

        assertThat(publisher.readRunEventsAfter("run-trace", null))
                .extracting(event -> event.eventType().wireName())
                .contains("tool.called", "retrieval.query.rewritten", "retrieval.completed", "tool.completed");
        WorkbenchEvent completed = publisher.readRunEventsAfter("run-trace", null).stream()
                .filter(event -> event.eventType().wireName().equals("tool.completed"))
                .findFirst()
                .orElseThrow();
        Map<?, ?> data = (Map<?, ?>) completed.payload().get("data");
        assertThat(data.containsKey("retrievalObservationSummary")).isTrue();
    }

    @Test
    void paperRagToolStillReturnsWhenTracePublisherFails() throws Exception {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "src-10"));
        when(paperRagService.retrieve(eq(42L), eq("multi agent"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class)))
                .thenReturn(new RagResult(
                        "multi agent",
                        List.of(10L),
                        List.of(new RagChunk(1L, 10L, 0, "Scoped project evidence.", 0.91))
                ));
        ProjectAgentTools tools = tools(scope, new ThrowingWorkbenchEventPublisher());

        String payload = tools.paperRag("multi agent", 5);

        verify(paperRagService).retrieve(eq(42L), eq("multi agent"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class));
        assertThat(tools.toolsUsed()).containsExactly("paper_rag");
        assertThat(objectMapper.readTree(payload).get("chunks")).hasSize(1);
    }

    @Test
    void webSearchToolCallsWebSearchPortAndRecordsToolUse() throws Exception {
        when(webSearchPort.search("深圳今天的天气", 5))
                .thenReturn(new WebSearchResult(
                        "深圳今天的天气",
                        List.of(new WebSearchHit("深圳天气", "https://weather.example", "深圳多云，26 摄氏度。", 0.9)),
                        "tavily",
                        false,
                        ""
                ));
        ProjectAgentTools tools = tools(new ProjectEvidenceScope(List.of(), Map.of()));

        String payload = tools.webSearch("深圳今天的天气", 5);

        verify(webSearchPort).search("深圳今天的天气", 5);
        assertThat(tools.toolsUsed()).containsExactly("tavily_web_search");
        assertThat(tools.webSearchResult()).isNotNull();
        JsonNode json = objectMapper.readTree(payload);
        assertThat(json.get("provider").asText()).isEqualTo("tavily");
        assertThat(json.get("hits")).hasSize(1);
    }

    @Test
    void paperRagToolCallsPaperRagServiceAndFiltersProjectScope() throws Exception {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "src-10"));
        when(paperRagService.retrieve(eq(42L), eq("核心方法"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class)))
                .thenReturn(new RagResult(
                        "核心方法",
                        List.of(10L),
                        List.of(
                                new RagChunk(1L, 10L, 0, "Scoped project evidence.", 0.91),
                                new RagChunk(2L, 99L, 0, "Cross-project evidence.", 0.95)
                        )
                ));
        ProjectAgentTools tools = tools(scope);

        String payload = tools.paperRag("核心方法", 5);

        verify(paperRagService).retrieve(eq(42L), eq("核心方法"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class));
        assertThat(tools.toolsUsed()).containsExactly("paper_rag");
        assertThat(tools.ragResult().chunks()).extracting(RagChunk::documentId).containsExactly(10L);
        JsonNode json = objectMapper.readTree(payload);
        assertThat(json.get("chunks")).hasSize(1);
        assertThat(json.get("chunks").get(0).get("documentId").asLong()).isEqualTo(10L);
    }

    @Test
    void paperRagToolReusesDuplicateQueryWithinOneAgentRun() throws Exception {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "src-10"));
        when(paperRagService.retrieve(eq(42L), eq("same query"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class)))
                .thenReturn(ragResult(
                        "same query",
                        List.of(10L),
                        List.of(new RagChunk(1L, 10L, 0, "Scoped project evidence.", 0.91))
                ));
        ProjectAgentTools tools = tools(scope);

        String firstPayload = tools.paperRag("same query", 5);
        String secondPayload = tools.paperRag("  same   query  ", 5);

        verify(paperRagService, times(1)).retrieve(eq(42L), eq("same query"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class));
        assertThat(objectMapper.readTree(secondPayload).get("deduplicated").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(secondPayload).get("chunks")).hasSize(1);
        assertThat(objectMapper.readTree(firstPayload).get("chunks")).hasSize(1);
    }

    @Test
    void paperRagToolStopsCallingBackendAfterRunBudgetIsExhausted() throws Exception {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "src-10"));
        when(paperRagService.retrieve(eq(42L), anyString(), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class)))
                .thenAnswer(invocation -> ragResult(
                        invocation.getArgument(1),
                        List.of(10L),
                        List.of(new RagChunk(1L, 10L, 0, "Scoped project evidence.", 0.91))
                ));
        ProjectAgentTools tools = tools(scope);

        tools.paperRag("query one", 5);
        tools.paperRag("query two", 5);
        tools.paperRag("query three", 5);
        String fourthPayload = tools.paperRag("query four", 5);

        verify(paperRagService, times(3)).retrieve(eq(42L), anyString(), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class));
        JsonNode json = objectMapper.readTree(fourthPayload);
        assertThat(json.get("skipped").asBoolean()).isTrue();
        assertThat(json.get("reason").asText()).isEqualTo("paper_rag_budget_exhausted");
    }

    @Test
    void paperRagToolPassesAnswerRunContextToRetrievalTrace() {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "src-10"));
        when(paperRagService.retrieve(eq(42L), eq("multi agent"), eq(List.of(10L)), eq(5), any(RetrievalTraceContext.class)))
                .thenReturn(ragResult(
                        "multi agent",
                        List.of(10L),
                        List.of(new RagChunk(1L, 10L, 0, "Scoped project evidence.", 0.91))
                ));
        ProjectAgentTools tools = tools(scope);

        tools.paperRag("multi agent", 5);

        verify(paperRagService).retrieve(
                eq(42L),
                eq("multi agent"),
                eq(List.of(10L)),
                eq(5),
                argThat(context ->
                        "project-trace".equals(context.projectId())
                                && "session-trace".equals(context.sessionId())
                                && "run-trace".equals(context.runId())
                                && "msg-trace".equals(context.messageId())
                                && "ans-trace".equals(context.answerId())
                                && "What does the project evidence support?".equals(context.answerQuestion())
                                && context.toolCallIndex() == 1
                ));
    }

    @Test
    void memoryRecallToolCallsMemoryRecallPortAndDoesNotCreateEvidence() throws Exception {
        MemoryRecallResult recallResult = new MemoryRecallResult(
                "之前讨论",
                List.of(new MemoryRecallHit(memoryEntry(), 0.88))
        );
        when(memoryRecallPort.recall(42L, "之前讨论", 4)).thenReturn(recallResult);
        ProjectAgentTools tools = tools(new ProjectEvidenceScope(List.of(), Map.of()));

        String payload = tools.memoryRecall("之前讨论", 4);

        verify(memoryRecallPort).recall(42L, "之前讨论", 4);
        assertThat(tools.toolsUsed()).containsExactly("memory_recall");
        assertThat(tools.memoryRecallResult()).isSameAs(recallResult);
        assertThat(tools.ragResult()).isNull();
        assertThat(tools.webSearchResult()).isNull();
        JsonNode json = objectMapper.readTree(payload);
        assertThat(json.get("hits")).hasSize(1);
        assertThat(json.get("hits").get(0).has("memoryLayer")).isTrue();
        assertThat(json.get("hits").get(0).has("sourceType")).isTrue();
        assertThat(json.get("hits").get(0).has("contextOnly")).isTrue();
        assertThat(json.get("hits").get(0).get("memoryLayer").asText()).isEqualTo("L3");
        assertThat(json.get("hits").get(0).get("sourceType").asText()).isEqualTo("long_term_memory");
        assertThat(json.get("hits").get(0).get("contextOnly").asBoolean()).isTrue();
        assertThat(json.get("hits").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(json.get("hits").get(0).get("injectionMode").asText()).isEqualTo("tool_recall");
        assertThat(json.get("hits").get(0).get("reason").asText()).isEqualTo("memory_recall_result");
        assertThat(json.get("hits").get(0).get("title").asText()).isEqualTo(json.get("hits").get(0).get("topic").asText());
    }

    private ProjectAgentTools tools(ProjectEvidenceScope scope) {
        return tools(scope, new InMemoryWorkbenchEventPublisher());
    }

    private ProjectAgentTools tools(ProjectEvidenceScope scope, InMemoryWorkbenchEventPublisher publisher) {
        return tools(scope, (WorkbenchEventPublisher) publisher);
    }

    private ProjectAgentTools tools(ProjectEvidenceScope scope, WorkbenchEventPublisher publisher) {
        return new ProjectAgentTools(
                42L,
                scope,
                paperRagService,
                memoryRecallPort,
                webSearchPort,
                objectMapper,
                new AgentTracePublisher(publisher),
                new AgentTraceContext("project-trace", "session-trace", "run-trace", "msg-trace", "ans-trace"),
                "What does the project evidence support?"
        );
    }

    private RagResult ragResult(String query, List<Long> allowedDocumentIds, List<RagChunk> chunks) {
        return new RagResult(
                query,
                allowedDocumentIds,
                chunks,
                RetrievalObservation.builder(query, allowedDocumentIds, 5)
                        .returnedScopedChunkCount(chunks.size())
                        .zeroHitReason(chunks.isEmpty() ? ZeroHitReason.NO_BACKEND_HITS : null)
                        .build()
        );
    }

    private static class ThrowingWorkbenchEventPublisher implements WorkbenchEventPublisher {

        @Override
        public WorkbenchEvent publish(WorkbenchEvent event) {
            throw new IllegalStateException("trace backend unavailable");
        }

        @Override
        public List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId) {
            return List.of();
        }
    }

    private MemoryEntry memoryEntry() {
        return new MemoryEntry(
                7L,
                42L,
                "COMPACTION",
                "天气查询",
                "用户想确认主 Agent 应自行判断是否联网。",
                List.of("主 Agent should decide tools"),
                List.of(),
                List.of("agent", "tool"),
                1L,
                2L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
