package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.events.WorkbenchEventType;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import com.researchassistant.rag.RetrievalObservation;
import com.researchassistant.rag.RetrievalTraceContext;
import com.researchassistant.rag.ZeroHitReason;
import com.researchassistant.websearch.WebSearchPort;
import com.researchassistant.websearch.WebSearchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class ProjectAgentTools {

    private static final Logger log = LoggerFactory.getLogger(ProjectAgentTools.class);
    private static final String PAPER_RAG_DISPLAY_NAME = "\u8d44\u6599\u68c0\u7d22\u6b65\u9aa4";
    private static final String WEB_SEARCH_DISPLAY_NAME = "\u8054\u7f51\u68c0\u7d22\u6b65\u9aa4";
    private static final String MEMORY_RECALL_DISPLAY_NAME = "\u8bb0\u5fc6\u53ec\u56de\u6b65\u9aa4";
    private static final int PAPER_RAG_CALL_BUDGET = 3;

    private final long sessionId;
    private final ProjectEvidenceScope evidenceScope;
    private final PaperRagService paperRagService;
    private final MemoryRecallPort memoryRecallPort;
    private final WebSearchPort webSearchPort;
    private final ObjectMapper objectMapper;
    private final AgentTracePublisher tracePublisher;
    private final AgentTraceContext traceContext;
    private final String answerQuestion;
    private final List<String> toolsUsed = new ArrayList<>();
    private final Map<String, String> paperRagPayloadByNormalizedQuery = new LinkedHashMap<>();
    private int paperRagBackendCalls;
    private RagResult ragResult;
    private WebSearchResult webSearchResult;
    private MemoryRecallResult memoryRecallResult;

    public ProjectAgentTools(long sessionId,
                             ProjectEvidenceScope evidenceScope,
                             PaperRagService paperRagService,
                             MemoryRecallPort memoryRecallPort,
                             WebSearchPort webSearchPort,
                             ObjectMapper objectMapper,
                             AgentTracePublisher tracePublisher,
                             AgentTraceContext traceContext) {
        this(sessionId, evidenceScope, paperRagService, memoryRecallPort, webSearchPort,
                objectMapper, tracePublisher, traceContext, "");
    }

    public ProjectAgentTools(long sessionId,
                             ProjectEvidenceScope evidenceScope,
                             PaperRagService paperRagService,
                             MemoryRecallPort memoryRecallPort,
                             WebSearchPort webSearchPort,
                             ObjectMapper objectMapper,
                             AgentTracePublisher tracePublisher,
                             AgentTraceContext traceContext,
                             String answerQuestion) {
        this.sessionId = sessionId;
        this.evidenceScope = evidenceScope;
        this.paperRagService = paperRagService;
        this.memoryRecallPort = memoryRecallPort;
        this.webSearchPort = webSearchPort;
        this.objectMapper = objectMapper;
        this.tracePublisher = tracePublisher;
        this.traceContext = traceContext;
        this.answerQuestion = answerQuestion;
    }

    public long sessionId() {
        return sessionId;
    }

    public ProjectEvidenceScope evidenceScope() {
        return evidenceScope;
    }

    public PaperRagService paperRagService() {
        return paperRagService;
    }

    public MemoryRecallPort memoryRecallPort() {
        return memoryRecallPort;
    }

    public WebSearchPort webSearchPort() {
        return webSearchPort;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    public RagResult ragResult() {
        return ragResult;
    }

    public WebSearchResult webSearchResult() {
        return webSearchResult;
    }

    public MemoryRecallResult memoryRecallResult() {
        return memoryRecallResult;
    }

    @Tool(name = "web_search", description = "Search the web for external, fresh, real-time, or non-project facts.")
    public String webSearch(
            @ToolParam(description = "The web search query.") String query,
            @ToolParam(description = "Maximum number of search results to return.") int maxResults) {
        int boundedMaxResults = boundedMaxResults(maxResults, 5);
        publishCalled("retrieval_worker", WEB_SEARCH_DISPLAY_NAME, "step_web_search", "web_search", query, boundedMaxResults);
        try {
            this.webSearchResult = webSearchPort.search(query, boundedMaxResults);
            addToolUsed("tavily_web_search");
            String payload = toJson(Map.of(
                    "query", safe(query),
                    "provider", safe(webSearchResult.provider()),
                    "degraded", webSearchResult.degraded(),
                    "message", safe(webSearchResult.message()),
                    "hits", webSearchResult.hits() == null ? List.of() : webSearchResult.hits()
            ));
            publishCompleted(
                    "retrieval_worker",
                    WEB_SEARCH_DISPLAY_NAME,
                    "step_web_search",
                    "web_search",
                    "web_search returned " + (webSearchResult.hits() == null ? 0 : webSearchResult.hits().size()) + " hit(s)"
            );
            return payload;
        } catch (RuntimeException exception) {
            publishFailed("retrieval_worker", WEB_SEARCH_DISPLAY_NAME, "step_web_search", "web_search", exception);
            throw exception;
        }
    }

    @Tool(name = "paper_rag", description = "Retrieve current project-local paper or source evidence.")
    public String paperRag(
            @ToolParam(description = "The project evidence retrieval query.") String query,
            @ToolParam(description = "Maximum number of evidence chunks to return.") int maxResults) {
        int boundedMaxResults = boundedMaxResults(maxResults, 5);
        publishCalled("retrieval_worker", PAPER_RAG_DISPLAY_NAME, "step_retrieval", "paper_rag", query, boundedMaxResults);
        try {
            String normalizedQuery = normalizeQuery(query);
            if (paperRagPayloadByNormalizedQuery.containsKey(normalizedQuery)) {
                String payload = withToolControlFlag(
                        paperRagPayloadByNormalizedQuery.get(normalizedQuery),
                        "deduplicated",
                        true
                );
                publishCompleted(
                        "retrieval_worker",
                        PAPER_RAG_DISPLAY_NAME,
                        "step_retrieval",
                        "paper_rag",
                        "paper_rag reused an earlier identical query"
                );
                return payload;
            }
            if (paperRagBackendCalls >= PAPER_RAG_CALL_BUDGET) {
                String payload = toJson(Map.of(
                        "query", safe(query),
                        "skipped", true,
                        "reason", "paper_rag_budget_exhausted",
                        "message", "paper_rag call budget for this answer has already been used.",
                        "chunks", List.of()
                ));
                publishCompleted(
                        "retrieval_worker",
                        PAPER_RAG_DISPLAY_NAME,
                        "step_retrieval",
                        "paper_rag",
                        "paper_rag skipped because the per-answer query budget was exhausted"
                );
                return payload;
            }
            if (evidenceScope == null || !evidenceScope.hasScopedPaperEvidence()) {
                RetrievalObservation observation = RetrievalObservation.builder(query, List.of(), boundedMaxResults)
                        .returnedScopedChunkCount(0)
                        .zeroHitReason(ZeroHitReason.NO_SCOPED_EVIDENCE)
                        .build();
                this.ragResult = new RagResult(query, List.of(), List.of(), observation);
                addToolUsed("paper_rag");
                String payload = toJson(Map.of(
                        "query", safe(query),
                        "message", "No scoped indexed project paper evidence is available.",
                        "chunks", List.of()
                ));
                publishCompleted(
                        "retrieval_worker",
                        PAPER_RAG_DISPLAY_NAME,
                        "step_retrieval",
                        "paper_rag",
                        "paper_rag completed with no scoped paper evidence",
                        observation.summary()
                );
                paperRagPayloadByNormalizedQuery.put(normalizedQuery, payload);
                return payload;
            }
            paperRagBackendCalls++;
            RagResult retrieved = paperRagService.retrieve(
                    sessionId,
                    query,
                    evidenceScope.indexedDocumentIds(),
                    boundedMaxResults,
                    retrievalTraceContext(paperRagBackendCalls)
            );
            List<RagChunk> scopedChunks = retrieved == null || retrieved.chunks() == null
                    ? List.of()
                    : retrieved.chunks().stream()
                            .filter(chunk -> evidenceScope.sourceIdByIndexedDocumentId().containsKey(chunk.documentId()))
                            .toList();
            this.ragResult = new RagResult(
                    retrieved == null ? query : retrieved.query(),
                    evidenceScope.indexedDocumentIds(),
                    scopedChunks,
                    retrieved == null ? null : retrieved.observation()
            );
            addToolUsed("paper_rag");
            String payload = toJson(Map.of(
                    "query", safe(query),
                    "chunks", scopedChunks.stream().map(this::chunkPayload).toList()
            ));
            publishRetrievalObservation(this.ragResult.observation(), scopedChunks.size());
            publishCompleted(
                    "retrieval_worker",
                    PAPER_RAG_DISPLAY_NAME,
                    "step_retrieval",
                    "paper_rag",
                    "paper_rag returned " + scopedChunks.size() + " scoped chunk(s)",
                    retrievalObservationSummary(this.ragResult.observation(), scopedChunks.size())
            );
            paperRagPayloadByNormalizedQuery.put(normalizedQuery, payload);
            return payload;
        } catch (RuntimeException exception) {
            publishFailed("retrieval_worker", PAPER_RAG_DISPLAY_NAME, "step_retrieval", "paper_rag", exception);
            throw exception;
        }
    }

    @Tool(name = "memory_recall", description = "Recall historical project discussion context.")
    public String memoryRecall(
            @ToolParam(description = "The memory recall query.") String query,
            @ToolParam(description = "Maximum number of memory hits to return.") int maxResults) {
        int boundedMaxResults = boundedMaxResults(maxResults, 4);
        publishCalled("memory_worker", MEMORY_RECALL_DISPLAY_NAME, "step_memory", "memory_recall", query, boundedMaxResults);
        try {
            this.memoryRecallResult = memoryRecallPort.recall(sessionId, query, boundedMaxResults);
            addToolUsed("memory_recall");
            List<Map<String, Object>> hits = memoryRecallResult == null || memoryRecallResult.hits() == null
                    ? List.of()
                    : memoryRecallResult.hits().stream().map(this::memoryHitPayload).toList();
            String payload = toJson(Map.of(
                    "query", safe(query),
                    "hits", hits
            ));
            publishCompleted(
                    "memory_worker",
                    MEMORY_RECALL_DISPLAY_NAME,
                    "step_memory",
                    "memory_recall",
                    "memory_recall returned " + hits.size() + " hit(s)"
            );
            return payload;
        } catch (RuntimeException exception) {
            publishFailed("memory_worker", MEMORY_RECALL_DISPLAY_NAME, "step_memory", "memory_recall", exception);
            throw exception;
        }
    }

    private void publishCalled(String agentRole,
                               String displayName,
                               String stepId,
                               String toolName,
                               String query,
                               int maxResults) {
        publishTrace(
                WorkbenchEventType.TOOL_CALLED,
                agentRole,
                displayName,
                stepId,
                "running",
                data("toolName", toolName, "query", safe(query), "maxResults", maxResults)
        );
    }

    private void publishCompleted(String agentRole,
                                  String displayName,
                                  String stepId,
                                  String toolName,
                                  String resultSummary) {
        publishCompleted(agentRole, displayName, stepId, toolName, resultSummary, Map.of());
    }

    private void publishCompleted(String agentRole,
                                  String displayName,
                                  String stepId,
                                  String toolName,
                                  String resultSummary,
                                  Map<String, Object> retrievalObservationSummary) {
        Map<String, Object> payload = data("toolName", toolName, "resultSummary", resultSummary);
        if (retrievalObservationSummary != null && !retrievalObservationSummary.isEmpty()) {
            payload.put("retrievalObservationSummary", retrievalObservationSummary);
        }
        publishTrace(
                WorkbenchEventType.TOOL_COMPLETED,
                agentRole,
                displayName,
                stepId,
                "completed",
                payload
        );
    }

    private void publishFailed(String agentRole,
                               String displayName,
                               String stepId,
                               String toolName,
                               RuntimeException exception) {
        publishTrace(
                WorkbenchEventType.TOOL_FAILED,
                agentRole,
                displayName,
                stepId,
                "failed",
                data(
                        "toolName", toolName,
                        "errorType", exception.getClass().getSimpleName(),
                        "message", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                        "recoverable", false
                )
        );
    }

    private void publishTrace(WorkbenchEventType eventType,
                              String agentRole,
                              String displayName,
                              String stepId,
                              String status,
                              Map<String, Object> data) {
        try {
            tracePublisher.publish(traceContext, eventType, agentRole, displayName, stepId, "step_plan", status, data);
        } catch (RuntimeException exception) {
            log.warn("Failed to publish project agent trace event {} for step {}", eventType.wireName(), stepId, exception);
        }
    }

    private void publishRetrievalObservation(RetrievalObservation observation, int scopedChunkCount) {
        if (observation == null) {
            return;
        }
        publishTrace(
                WorkbenchEventType.RETRIEVAL_QUERY_REWRITTEN,
                "retrieval_worker",
                PAPER_RAG_DISPLAY_NAME,
                "step_retrieval",
                "completed",
                data(
                        "toolName", "paper_rag",
                        "originalQuery", safe(observation.originalQuery()),
                        "strategy", observation.rewriteStrategy(),
                        "retrievalQueries", observation.retrievalQueries(),
                        "keywords", observation.keywords()
                )
        );
        Map<String, Object> summary = new LinkedHashMap<>(observation.summary());
        summary.put("toolName", "paper_rag");
        summary.put("retrievalMode", "HYBRID_RAG");
        summary.put("returnedScopedChunkCount", scopedChunkCount);
        publishTrace(
                WorkbenchEventType.RETRIEVAL_COMPLETED,
                "retrieval_worker",
                PAPER_RAG_DISPLAY_NAME,
                "step_retrieval",
                "completed",
                summary
        );
    }

    private Map<String, Object> data(Object... keyValues) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            data.put((String) keyValues[index], keyValues[index + 1]);
        }
        return data;
    }

    private Map<String, Object> retrievalObservationSummary(RetrievalObservation observation, int scopedChunkCount) {
        if (observation == null) {
            return Map.of("returnedScopedChunkCount", scopedChunkCount);
        }
        return observation.summary();
    }

    private RetrievalTraceContext retrievalTraceContext(int toolCallIndex) {
        if (traceContext == null) {
            return RetrievalTraceContext.empty();
        }
        return new RetrievalTraceContext(
                traceContext.projectId(),
                traceContext.sessionId(),
                traceContext.runId(),
                traceContext.messageId(),
                traceContext.answerId(),
                answerQuestion,
                toolCallIndex
        );
    }

    private Map<String, Object> memoryHitPayload(MemoryRecallHit hit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memoryLayer", "L3");
        payload.put("sourceType", "long_term_memory");
        payload.put("contextOnly", true);
        payload.put("score", hit.finalScore());
        if (hit.entry() != null) {
            payload.put("sourceId", String.valueOf(hit.entry().id()));
            payload.put("topic", safe(hit.entry().topic()));
            payload.put("summary", safe(hit.entry().summary()));
            payload.put("keyFindings", hit.entry().keyFindings() == null ? List.of() : hit.entry().keyFindings());
            payload.put("openQuestions", hit.entry().openQuestions() == null ? List.of() : hit.entry().openQuestions());
            payload.put("keywords", hit.entry().keywords() == null ? List.of() : hit.entry().keywords());
        }
        return payload;
    }

    private Map<String, Object> chunkPayload(RagChunk chunk) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", chunk.chunkId());
        payload.put("documentId", chunk.documentId());
        payload.put("chunkIndex", chunk.chunkIndex());
        payload.put("content", chunk.content());
        payload.put("score", chunk.finalScore());
        return payload;
    }

    private void addToolUsed(String toolName) {
        if (!toolsUsed.contains(toolName)) {
            toolsUsed.add(toolName);
        }
    }

    private int boundedMaxResults(int requested, int defaultValue) {
        int normalized = requested <= 0 ? defaultValue : requested;
        return Math.min(normalized, 10);
    }

    private String normalizeQuery(String query) {
        return safe(query)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String withToolControlFlag(String payload, String key, boolean value) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
            data.put(key, value);
            return toJson(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize project agent tool result", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize project agent tool result", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
