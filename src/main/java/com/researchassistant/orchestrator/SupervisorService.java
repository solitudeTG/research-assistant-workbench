package com.researchassistant.orchestrator;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.chat.dto.CitationDto;
import com.researchassistant.chat.dto.ProjectMessageRequest;
import com.researchassistant.chat.dto.ProjectMessageResponse;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceAssessment;
import com.researchassistant.evidence.EvidenceBoundaryService;
import com.researchassistant.evidence.EvidenceLevel;
import com.researchassistant.evidence.EvidenceSourceRepository;
import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.evidence.ProjectEvidenceScopeRepository;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import com.researchassistant.ingest.model.ResearchDocument;
import com.researchassistant.memory.ExplicitMemoryService;
import com.researchassistant.memory.GlobalKnowledgeService;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.memory.WorkingMemoryService;
import com.researchassistant.orchestrator.support.DocumentMetadataService;
import com.researchassistant.project.AssistantAnswerRepository;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchPort;
import com.researchassistant.websearch.WebSearchResult;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SupervisorService {

    private static final int MAX_TRACE_HITS = 5;

    private final TaskRouter taskRouter;
    private final PaperRagService paperRagService;
    private final EvidenceBoundaryService evidenceBoundaryService;
    private final WorkingMemoryService workingMemoryService;
    private final DocumentMetadataService documentMetadataService;
    private final MemoryRecallPort memoryRecallPort;
    private final ExplicitMemoryService explicitMemoryService;
    private final GlobalKnowledgeService globalKnowledgeService;
    private final PlanExecuteFacade planExecuteFacade;
    private final ChatClient chatClient;
    private final WorkbenchEventPublisher eventPublisher;
    private final AssistantAnswerRepository assistantAnswerRepository;
    private final ProjectRepository projectRepository;
    private final EvidenceSourceRepository evidenceSourceRepository;
    private final ProjectEvidenceScopeRepository projectEvidenceScopeRepository;
    private final TransactionTemplate transactionTemplate;
    private final AgentIntentRouter agentIntentRouter;
    private final WebSearchPort webSearchPort;
    private final ProjectAgentToolLoop projectAgentToolLoop;

    public SupervisorService(
            TaskRouter taskRouter,
            PaperRagService paperRagService,
            EvidenceBoundaryService evidenceBoundaryService,
            WorkingMemoryService workingMemoryService,
            DocumentMetadataService documentMetadataService,
            MemoryRecallPort memoryRecallPort,
            ExplicitMemoryService explicitMemoryService,
            GlobalKnowledgeService globalKnowledgeService,
            PlanExecuteFacade planExecuteFacade,
            ChatClient chatClient,
            WorkbenchEventPublisher eventPublisher,
            AssistantAnswerRepository assistantAnswerRepository,
            ProjectRepository projectRepository,
            EvidenceSourceRepository evidenceSourceRepository,
            ProjectEvidenceScopeRepository projectEvidenceScopeRepository,
            TransactionTemplate transactionTemplate,
            AgentIntentRouter agentIntentRouter,
            WebSearchPort webSearchPort,
            ProjectAgentToolLoop projectAgentToolLoop) {
        this.taskRouter = taskRouter;
        this.paperRagService = paperRagService;
        this.evidenceBoundaryService = evidenceBoundaryService;
        this.workingMemoryService = workingMemoryService;
        this.documentMetadataService = documentMetadataService;
        this.memoryRecallPort = memoryRecallPort;
        this.explicitMemoryService = explicitMemoryService;
        this.globalKnowledgeService = globalKnowledgeService;
        this.planExecuteFacade = planExecuteFacade;
        this.chatClient = chatClient;
        this.eventPublisher = eventPublisher;
        this.assistantAnswerRepository = assistantAnswerRepository;
        this.projectRepository = projectRepository;
        this.evidenceSourceRepository = evidenceSourceRepository;
        this.projectEvidenceScopeRepository = projectEvidenceScopeRepository;
        this.transactionTemplate = transactionTemplate;
        this.agentIntentRouter = agentIntentRouter;
        this.webSearchPort = webSearchPort;
        this.projectAgentToolLoop = projectAgentToolLoop;
    }

    public ProjectMessageResponse answerProject(String projectId, String sessionId, ProjectMessageRequest request) {
        if (request.sourceFilters() != null && !request.sourceFilters().isEmpty()) {
            throw new IllegalArgumentException("sourceFilters require project-source-to-index mapping and are not supported yet");
        }
        String messageId = "msg_" + UUID.randomUUID();
        String answerId = "ans_" + UUID.randomUUID();
        String runId = "run_" + UUID.randomUUID();
        String answerMode = request.answerMode() == null || request.answerMode().isBlank()
                ? "local_first"
                : request.answerMode();
        ProjectEvidenceScope evidenceScope = projectEvidenceScopeRepository.load(projectId);
        List<Long> documentIds = evidenceScope.indexedDocumentIds();

        publishRunEvent(
                WorkbenchEventType.RUN_STARTED,
                projectId,
                sessionId,
                runId,
                "supervisor",
                null,
                payload(
                        "messageId", messageId,
                        "question", request.question(),
                        "answerMode", answerMode,
                        "allowWebSupplement", Boolean.TRUE.equals(request.allowWebSupplement()),
                        "extractKnowledgeCandidates", Boolean.TRUE.equals(request.extractKnowledgeCandidates())
                )
        );
        publishRunEvent(
                WorkbenchEventType.AGENT_PLAN_CREATED,
                projectId,
                sessionId,
                runId,
                "supervisor",
                null,
                payload(
                        "summary", "Route the project-scoped question through the existing supervisor answer path.",
                        "steps", List.of("route", "retrieve", "evaluate-evidence", "draft-answer")
                )
        );
        publishRunEvent(
                WorkbenchEventType.AGENT_STEP_STARTED,
                projectId,
                sessionId,
                runId,
                "supervisor",
                null,
                payload("step", "answer-project-message")
        );
        publishRunEvent(
                WorkbenchEventType.RETRIEVAL_STARTED,
                projectId,
                sessionId,
                runId,
                "retrieval-agent",
                null,
                payload(
                        "sourceFilterCount", request.sourceFilters() == null ? 0 : request.sourceFilters().size(),
                        "legacyDocumentIds", documentIds
                )
        );

        try {
            WorkingMemory memory = workingMemoryService.load(sessionId);
            boolean allowWebSupplement = Boolean.TRUE.equals(request.allowWebSupplement());
            ProjectAgentRun agentRun = projectAgentToolLoop.run(new ProjectAgentRequest(
                    projectId,
                    sessionId,
                    runId,
                    messageId,
                    answerId,
                    request.question(),
                    memory,
                    globalKnowledgeService.snapshot(),
                    evidenceScope,
                    allowWebSupplement
            ));
            RagResult ragResult = agentRun.ragResult() == null
                    ? new RagResult(request.question(), documentIds, List.of())
                    : scopedRagResult(agentRun.ragResult(), evidenceScope);
            WebSearchResult webSearchResult = agentRun.webSearchResult();
            MemoryRecallResult memoryRecallResult = agentRun.memoryRecallResult();
            EvidenceAssessment assessment = projectAgentEvidenceAssessment(
                    agentRun,
                    ragResult,
                    webSearchResult,
                    allowWebSupplement
            );
            RetrievalMode retrievalMode = projectAgentRetrievalMode(
                    agentRun,
                    evidenceScope,
                    memoryRecallResult,
                    assessment
            );
            String answer = agentRun.answer() == null ? "" : agentRun.answer();

            persistProjectAnswerAndEvidence(
                    answerId,
                    projectId,
                    sessionId,
                    request.question(),
                    answer,
                    assessment,
                    ragResult,
                    webSearchResult,
                    evidenceScope
            );

            publishMemoryTraceEvents(projectId, sessionId, runId, answerId, memory, memoryRecallResult, agentRun);
            publishRetrievalHitEvents(projectId, sessionId, runId, answerId, ragResult, webSearchResult, evidenceScope);
            publishRunEvent(
                    WorkbenchEventType.RETRIEVAL_COMPLETED,
                    projectId,
                    sessionId,
                    runId,
                    "retrieval-agent",
                    answerId,
                    payload(
                            "retrievalMode", retrievalMode.name(),
                            "sourceFilterCount", request.sourceFilters() == null ? 0 : request.sourceFilters().size(),
                            "paperEvidenceCount", chunkCount(ragResult),
                            "memoryRecallCount", memoryHitCount(memoryRecallResult),
                            "webSupplementAllowed", allowWebSupplement,
                            "intent", "MAIN_AGENT_TOOL_LOOP",
                            "toolsUsed", agentRun.toolsUsed() == null ? List.of() : agentRun.toolsUsed(),
                            "webEvidenceCount", webHitCount(webSearchResult),
                            "webSearchStatus", webSearchStatus(webSearchResult),
                            "topPaperScore", topPaperScore(ragResult),
                            "citationCount", assessment.citationCount(),
                            "summary", retrievalSummary(ragResult, memoryRecallResult)
                    )
            );
            publishRunEvent(
                    WorkbenchEventType.EVIDENCE_EVALUATED,
                    projectId,
                    sessionId,
                    runId,
                    "evidence-boundary",
                    answerId,
                    payload(
                            "evidenceState", assessment.evidenceLevel().name(),
                            "outputMode", assessment.answerMode().name(),
                            "citationCount", assessment.citationCount(),
                            "sourceTypes", sourceTypes(ragResult, webSearchResult)
                    )
            );
            publishEvidenceGapIfNeeded(projectId, sessionId, runId, answerId, assessment, agentRun, ragResult, webSearchResult);
            List<String> deltas = answerDeltas(answer);
            for (int index = 0; index < deltas.size(); index++) {
                String delta = deltas.get(index);
                publishRunEvent(
                        WorkbenchEventType.ANSWER_DELTA,
                        projectId,
                        sessionId,
                        runId,
                        "writing-agent",
                        answerId,
                        payload(
                                "delta", bounded(delta),
                                "text", bounded(delta),
                                "index", index
                        )
                );
            }
            publishRunEvent(
                    WorkbenchEventType.ANSWER_COMPLETED,
                    projectId,
                    sessionId,
                    runId,
                    "supervisor",
                    answerId,
                    payload(
                            "answerId", answerId,
                            "answerMode", assessment.answerMode().name(),
                            "evidenceState", assessment.evidenceLevel().name(),
                            "citationCount", assessment.citationCount()
                    )
            );
            publishRunEvent(
                    WorkbenchEventType.RUN_COMPLETED,
                    projectId,
                    sessionId,
                    runId,
                    "supervisor",
                    answerId,
                    payload("status", "completed")
            );

            return new ProjectMessageResponse(
                    messageId,
                    answerId,
                    runId,
                    "/api/projects/" + projectId + "/sessions/" + sessionId + "/runs/" + runId + "/events"
            );
        } catch (RuntimeException exception) {
            publishRunEvent(
                    WorkbenchEventType.RUN_FAILED,
                    projectId,
                    sessionId,
                    runId,
                    "supervisor",
                    null,
                    payload(
                            "message", exception.getMessage() == null
                                    ? exception.getClass().getSimpleName()
                                    : exception.getMessage()
                    )
            );
            throw exception;
        }
    }

    public ChatResponse answer(ChatRequest request) {
        WorkingMemory memory = workingMemoryService.load(request.sessionKey());
        ResearchDocument primaryDocument = documentMetadataService.findPrimaryDocument(request.documentIds());

        if (explicitMemoryService.isExplicitMemoryRequest(request.question())) {
            String answer = explicitMemoryService.store(memory, request.question());
            workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, AnswerMode.LOCAL_WEAK_EVIDENCE.name());
            return new ChatResponse(request.sessionKey(), AnswerMode.LOCAL_WEAK_EVIDENCE.name(), answer, List.of());
        }

        if (request.documentIds() != null && !request.documentIds().isEmpty() && primaryDocument == null) {
            String answer = "当前文档不存在、尚未完成索引，或在重启后已失效，请重新上传论文后再提问。";
            workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, AnswerMode.LOCAL_WEAK_EVIDENCE.name());
            return new ChatResponse(request.sessionKey(), AnswerMode.LOCAL_WEAK_EVIDENCE.name(), answer, List.of());
        }

        if (documentMetadataService.isTitleQuestion(request.question())) {
            String answer = documentMetadataService.answerTitleQuestion(primaryDocument);
            workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, AnswerMode.LOCAL_EVIDENCE.name());
            return new ChatResponse(request.sessionKey(), AnswerMode.LOCAL_EVIDENCE.name(), answer, List.of());
        }

        if (documentMetadataService.isOverviewQuestion(request.question()) && primaryDocument != null) {
            Optional<String> overviewAnswer = documentMetadataService.answerOverviewQuestion(primaryDocument);
            ChatResponse overviewResponse = respondToOverviewQuestion(request, memory, overviewAnswer);
            if (overviewResponse != null) {
                return overviewResponse;
            }
        }

        if (documentMetadataService.isMethodQuestion(request.question()) && primaryDocument != null) {
            Optional<String> methodAnswer = documentMetadataService.answerMethodQuestion(primaryDocument);
            if (methodAnswer.isPresent()) {
                workingMemoryService.appendExchange(
                        request.sessionKey(),
                        request.question(),
                        methodAnswer.get(),
                        AnswerMode.LOCAL_WEAK_EVIDENCE.name()
                );
                return new ChatResponse(
                        request.sessionKey(),
                        AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                        methodAnswer.get(),
                        List.of()
                );
            }
        }

        if (documentMetadataService.isContributionQuestion(request.question()) && primaryDocument != null) {
            Optional<String> contributionAnswer = documentMetadataService.answerContributionQuestion(primaryDocument);
            if (contributionAnswer.isPresent()) {
                workingMemoryService.appendExchange(
                        request.sessionKey(),
                        request.question(),
                        contributionAnswer.get(),
                        AnswerMode.LOCAL_WEAK_EVIDENCE.name()
                );
                return new ChatResponse(
                        request.sessionKey(),
                        AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                        contributionAnswer.get(),
                        List.of()
                );
            }
        }

        if (planExecuteFacade.shouldPlan(request.question())) {
            return respondWithPlanExecution(request, memory);
        }

        RetrievalMode retrievalMode = taskRouter.route(request.question(), request.documentIds());
        return switch (retrievalMode) {
            case NO_RETRIEVAL -> respondWithoutRetrieval(request);
            case MEMORY_RECALL_ONLY -> respondFromMemory(request, memory);
            case PAPER_RAG_ONLY -> respondFromPaper(request, memory, primaryDocument, null);
            case MEMORY_THEN_PAPER -> {
                MemoryRecallResult memoryRecallResult = memoryRecallPort.recall(memory.sessionId(), request.question(), 4);
                yield respondFromPaper(request, memory, primaryDocument, memoryRecallResult);
            }
            case WEB_SUPPLEMENT -> respondWithoutRetrieval(request);
        };
    }

    private String queryWithMemoryContext(String question, MemoryRecallResult memoryRecallResult) {
        if (memoryRecallResult == null || memoryRecallResult.isEmpty()) {
            return question;
        }
        return question + "\nHistorical context:\n" + memoryRecallResult.contextBlock();
    }

    private RagResult scopedRagResult(RagResult ragResult, ProjectEvidenceScope evidenceScope) {
        if (ragResult == null) {
            return new RagResult("", evidenceScope.indexedDocumentIds(), List.of());
        }
        List<RagChunk> scopedChunks = chunksOf(ragResult).stream()
                .filter(chunk -> evidenceScope.sourceIdByIndexedDocumentId().containsKey(chunk.documentId()))
                .toList();
        return new RagResult(ragResult.query(), evidenceScope.indexedDocumentIds(), scopedChunks);
    }

    private void persistProjectAnswerAndEvidence(String answerId,
                                                 String projectId,
                                                 String sessionId,
                                                 String question,
                                                 String answer,
                                                 EvidenceAssessment assessment,
                                                 RagResult ragResult,
                                                 WebSearchResult webSearchResult,
                                                 ProjectEvidenceScope evidenceScope) {
        transactionTemplate.executeWithoutResult(status -> {
            assistantAnswerRepository.insert(
                    answerId,
                    projectId,
                    sessionId,
                    question,
                    answer,
                    assessment.answerMode().name(),
                    assessment.evidenceLevel().name()
            );
            evidenceSourceRepository.insertPaperSources(
                    projectId,
                    answerId,
                    chunksOf(ragResult),
                    evidenceScope.sourceIdByIndexedDocumentId()
            );
            evidenceSourceRepository.insertWebSources(projectId, answerId, webSearchResult);
            workingMemoryService.appendExchange(sessionId, question, answer, assessment.answerMode().name());
            projectRepository.markSessionMessaged(projectId, sessionId);
        });
    }

    private RetrievalMode projectRetrievalMode(ProjectEvidenceScope evidenceScope,
                                               MemoryRecallResult memoryRecallResult,
                                               EvidenceAssessment assessment) {
        if (!evidenceScope.hasScopedPaperEvidence()) {
            return memoryRecallResult != null && !memoryRecallResult.isEmpty()
                    ? RetrievalMode.MEMORY_RECALL_ONLY
                    : RetrievalMode.NO_RETRIEVAL;
        }
        if (assessment.answerMode() == AnswerMode.WEB_SUPPLEMENT) {
            return RetrievalMode.WEB_SUPPLEMENT;
        }
        if (memoryRecallResult != null && !memoryRecallResult.isEmpty()) {
            return RetrievalMode.MEMORY_THEN_PAPER;
        }
        return RetrievalMode.PAPER_RAG_ONLY;
    }

    private RetrievalMode projectAgentRetrievalMode(ProjectAgentRun agentRun,
                                                    ProjectEvidenceScope evidenceScope,
                                                    MemoryRecallResult memoryRecallResult,
                                                    EvidenceAssessment assessment) {
        if (agentRun != null && agentRun.webSearchResult() != null) {
            return RetrievalMode.WEB_SUPPLEMENT;
        }
        if (assessment.answerMode() == AnswerMode.WEB_SUPPLEMENT) {
            return RetrievalMode.WEB_SUPPLEMENT;
        }
        if (agentRun != null && agentRun.toolsUsed() != null) {
            boolean memoryRecallCalled = agentRun.toolsUsed().contains("memory_recall");
            boolean paperRagCalled = agentRun.toolsUsed().contains("paper_rag");
            if (memoryRecallCalled && paperRagCalled) {
                return RetrievalMode.MEMORY_THEN_PAPER;
            }
            if (memoryRecallCalled) {
                return RetrievalMode.MEMORY_RECALL_ONLY;
            }
            if (paperRagCalled) {
                if (!evidenceScope.hasScopedPaperEvidence()) {
                    return RetrievalMode.NO_RETRIEVAL;
                }
                return RetrievalMode.PAPER_RAG_ONLY;
            }
            return RetrievalMode.NO_RETRIEVAL;
        }
        return projectRetrievalMode(evidenceScope, memoryRecallResult, assessment);
    }

    private RetrievalMode projectRetrievalMode(AgentRoutingDecision routingDecision,
                                               ProjectEvidenceScope evidenceScope,
                                               MemoryRecallResult memoryRecallResult,
                                               EvidenceAssessment assessment) {
        if (routingDecision.intent() == AgentIntent.SIMPLE_CHAT) {
            return RetrievalMode.NO_RETRIEVAL;
        }
        if (routingDecision.intent() == AgentIntent.MEMORY_RECALL) {
            return RetrievalMode.MEMORY_RECALL_ONLY;
        }
        if (routingDecision.needsWebSearch()) {
            return RetrievalMode.WEB_SUPPLEMENT;
        }
        return projectRetrievalMode(evidenceScope, memoryRecallResult, assessment);
    }

    private EvidenceAssessment projectEvidenceAssessment(AgentRoutingDecision routingDecision,
                                                         RagResult ragResult,
                                                         WebSearchResult webSearchResult,
                                                         boolean allowWebSupplement) {
        if (routingDecision.intent() == AgentIntent.SIMPLE_CHAT) {
            return new EvidenceAssessment(EvidenceLevel.NONE, AnswerMode.LOCAL_WEAK_EVIDENCE, 0);
        }
        if (routingDecision.intent() == AgentIntent.MEMORY_RECALL) {
            return new EvidenceAssessment(EvidenceLevel.WEAK, AnswerMode.LOCAL_WEAK_EVIDENCE, 0);
        }
        if (hasWebHits(webSearchResult)) {
            return new EvidenceAssessment(
                    EvidenceLevel.WEAK,
                    AnswerMode.WEB_SUPPLEMENT,
                    chunkCount(ragResult) + webHitCount(webSearchResult)
            );
        }
        if (routingDecision.intent() == AgentIntent.WEB_SEARCH && webSearchResult != null) {
            return new EvidenceAssessment(EvidenceLevel.WEAK, AnswerMode.WEB_SUPPLEMENT, 0);
        }
        return evidenceBoundaryService.assessProjectEvidence(ragResult, allowWebSupplement);
    }

    private EvidenceAssessment projectAgentEvidenceAssessment(ProjectAgentRun agentRun,
                                                              RagResult ragResult,
                                                              WebSearchResult webSearchResult,
                                                              boolean allowWebSupplement) {
        if (hasWebHits(webSearchResult)) {
            return new EvidenceAssessment(
                    EvidenceLevel.WEAK,
                    AnswerMode.WEB_SUPPLEMENT,
                    chunkCount(ragResult) + webHitCount(webSearchResult)
            );
        }
        if (webSearchResult != null) {
            return new EvidenceAssessment(EvidenceLevel.WEAK, AnswerMode.WEB_SUPPLEMENT, chunkCount(ragResult));
        }
        if (chunkCount(ragResult) > 0) {
            return evidenceBoundaryService.assessProjectEvidence(ragResult, allowWebSupplement);
        }
        if (agentRun != null && agentRun.toolsUsed() != null && agentRun.toolsUsed().contains("paper_rag")) {
            return evidenceBoundaryService.assessProjectEvidence(ragResult, allowWebSupplement);
        }
        if (agentRun != null && agentRun.memoryRecallResult() != null && !agentRun.memoryRecallResult().isEmpty()) {
            return new EvidenceAssessment(EvidenceLevel.WEAK, AnswerMode.LOCAL_WEAK_EVIDENCE, 0);
        }
        if (agentRun != null && agentRun.toolsUsed() != null && agentRun.toolsUsed().contains("memory_recall")) {
            return new EvidenceAssessment(EvidenceLevel.WEAK, AnswerMode.LOCAL_WEAK_EVIDENCE, 0);
        }
        return new EvidenceAssessment(EvidenceLevel.NONE, AnswerMode.LOCAL_WEAK_EVIDENCE, 0);
    }

    private EvidenceAssessment projectPlanningAssessment(RagResult ragResult) {
        if (chunkCount(ragResult) == 0) {
            return new EvidenceAssessment(EvidenceLevel.WEAK, AnswerMode.LOCAL_WEAK_EVIDENCE, 0);
        }
        return new EvidenceAssessment(EvidenceLevel.SUFFICIENT, AnswerMode.LOCAL_EVIDENCE, chunkCount(ragResult));
    }

    private String draftProjectAnswer(ProjectMessageRequest request,
                                      WorkingMemory memory,
                                      MemoryRecallResult memoryRecallResult,
                                      RagResult ragResult,
                                      WebSearchResult webSearchResult,
                                      AgentRoutingDecision routingDecision,
                                      EvidenceAssessment assessment) {
        if (routingDecision.intent() == AgentIntent.SIMPLE_CHAT) {
            String content = chatClient.prompt()
                    .system("Answer naturally as the main research assistant. Explain that you can help with project research and optional web search. Do not mention missing paper evidence for greetings or simple interaction.")
                    .user("User message: " + request.question()
                            + "\n\nWorking memory:\n" + safe(memory.rollingSummary())
                            + "\n\nGlobal knowledge:\n" + globalKnowledgeBlock())
                    .call()
                    .content();
            return content == null || content.isBlank()
                    ? "你好，我在。你可以问项目资料，也可以让我联网补充。"
                    : content;
        }
        if (routingDecision.intent() == AgentIntent.MEMORY_RECALL) {
            if (memoryRecallResult == null || memoryRecallResult.isEmpty()) {
                return "I do not have enough prior discussion in memory for this project yet.";
            }
            String content = chatClient.prompt()
                    .system("Answer from prior project memory. Be clear that this is historical conversation context, not paper evidence.")
                    .user("Question: " + request.question()
                            + "\n\nWorking memory:\n" + safe(memory.rollingSummary())
                            + "\n\nGlobal knowledge:\n" + globalKnowledgeBlock()
                            + "\n\nMemory context:\n" + memoryRecallResult.contextBlock())
                    .call()
                    .content();
            return content == null || content.isBlank()
                    ? memoryRecallResult.contextBlock()
                    : content;
        }
        if (routingDecision.intent() == AgentIntent.WEB_SEARCH && webSearchResult != null && !hasWebHits(webSearchResult)) {
            String status = webSearchResult.degraded() ? "degraded" : "unavailable";
            String message = safe(webSearchResult.message());
            return "The web search is currently " + status + ". " + message
                    + " I do not have web results to cite for this request.";
        }
        if (assessment.answerMode() == AnswerMode.REFUSAL) {
            return "当前项目资料中没有足够的论文证据支撑回答。请补充或重新索引相关资料后再提问。";
        }
        String evidenceContext = chunksOf(ragResult).stream()
                .map(chunk -> "[doc=" + chunk.documentId() + ",chunk=" + chunk.chunkIndex() + "] " + chunk.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String systemPrompt = assessment.answerMode() == AnswerMode.WEB_SUPPLEMENT
                ? "Answer conservatively. Separate local paper evidence from web supplement. Do not present web claims as local paper evidence."
                : "Answer conservatively. Memory context is background only. Paper context is the current evidence source.";
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user("Question: " + request.question()
                        + "\n\nWorking memory:\n" + safe(memory.rollingSummary())
                        + "\n\nGlobal knowledge:\n" + globalKnowledgeBlock()
                        + "\n\nMemory context:\n" + (memoryRecallResult == null ? "" : memoryRecallResult.contextBlock())
                        + "\n\nPaper evidence:\n" + evidenceContext
                        + "\n\nWeb evidence:\n" + webEvidenceBlock(webSearchResult))
                .call()
                .content();
        if (content == null || content.isBlank()) {
            return assessment.answerMode() == AnswerMode.WEB_SUPPLEMENT
                    ? "本地论文证据偏弱，需要联网补充后才能给出更稳健结论。"
                    : "已基于当前论文证据生成回答。";
        }
        return content;
    }

    private String webEvidenceBlock(WebSearchResult webSearchResult) {
        if (!hasWebHits(webSearchResult)) {
            return webSearchResult != null && webSearchResult.degraded()
                    ? "(web search degraded: " + safe(webSearchResult.message()) + ")"
                    : "(none)";
        }
        return webSearchResult.hits().stream()
                .map(hit -> "- " + hit.title() + " (" + hit.url() + "): " + hit.snippet())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(none)");
    }

    private boolean hasWebHits(WebSearchResult webSearchResult) {
        return webSearchResult != null
                && webSearchResult.hits() != null
                && !webSearchResult.hits().isEmpty();
    }

    private int webHitCount(WebSearchResult webSearchResult) {
        return webSearchResult == null || webSearchResult.hits() == null
                ? 0
                : webSearchResult.hits().size();
    }

    private String webSearchStatus(WebSearchResult webSearchResult) {
        if (webSearchResult == null) {
            return "not_requested";
        }
        return webSearchResult.degraded() ? "degraded" : "completed";
    }

    private void publishMemoryTraceEvents(String projectId,
                                          String sessionId,
                                          String runId,
                                          String answerId,
                                          WorkingMemory memory,
                                          MemoryRecallResult memoryRecallResult,
                                          ProjectAgentRun agentRun) {
        List<MemoryRecallHit> hits = memoryRecallResult == null || memoryRecallResult.hits() == null
                ? List.of()
                : memoryRecallResult.hits();
        int workingMemoryHitCount = 0;
        if (hasWorkingMemorySummary(memory)) {
            workingMemoryHitCount = 1;
            Map<String, Object> data = payload(
                    "memoryLayer", "L1",
                    "label", "\u5de5\u4f5c\u8bb0\u5fc6",
                    "snippet", bounded(workingMemorySnippet(memory)),
                    "score", 1.0
            );
            publishRunEvent(
                    WorkbenchEventType.MEMORY_HIT,
                    projectId,
                    sessionId,
                    runId,
                    "memory_worker",
                    answerId,
                    payload("data", data)
            );
        }
        for (MemoryRecallHit hit : hits) {
            Map<String, Object> data = payload(
                    "memoryLayer", "L3",
                    "label", "\u957f\u671f\u8bb0\u5fc6\u53ec\u56de",
                    "snippet", bounded(memorySnippet(hit)),
                    "score", hit.finalScore()
            );
            publishRunEvent(
                    WorkbenchEventType.MEMORY_HIT,
                    projectId,
                    sessionId,
                    runId,
                    "memory_worker",
                    answerId,
                    payload("data", data)
            );
        }
        if (workingMemoryHitCount > 0 || !hits.isEmpty() || toolWasUsed(agentRun, "memory_recall")) {
            publishRunEvent(
                    WorkbenchEventType.MEMORY_COMPLETED,
                    projectId,
                    sessionId,
                    runId,
                    "memory_worker",
                    answerId,
                    payload("data", payload(
                            "hitCount", workingMemoryHitCount + hits.size(),
                            "workingMemoryHitCount", workingMemoryHitCount,
                            "l3HitCount", hits.size()
                    ))
            );
        }
    }

    private boolean hasWorkingMemorySummary(WorkingMemory memory) {
        return memory != null
                && ((memory.rollingSummary() != null && !memory.rollingSummary().isBlank())
                || (memory.salientFacts() != null && !memory.salientFacts().isEmpty()));
    }

    private String workingMemorySnippet(WorkingMemory memory) {
        if (memory == null) {
            return "";
        }
        String summary = safe(memory.rollingSummary());
        if (memory.salientFacts() == null || memory.salientFacts().isEmpty()) {
            return summary;
        }
        return summary + "\n" + String.join("; ", memory.salientFacts());
    }

    private void publishRetrievalHitEvents(String projectId,
                                           String sessionId,
                                           String runId,
                                           String answerId,
                                           RagResult ragResult,
                                           WebSearchResult webSearchResult,
                                           ProjectEvidenceScope evidenceScope) {
        List<RagChunk> chunks = chunksOf(ragResult).stream().limit(MAX_TRACE_HITS).toList();
        for (int index = 0; index < chunks.size(); index++) {
            RagChunk chunk = chunks.get(index);
            String sourceId = evidenceScope.sourceIdByIndexedDocumentId().get(chunk.documentId());
            publishRunEvent(
                    WorkbenchEventType.RETRIEVAL_HIT,
                    projectId,
                    sessionId,
                    runId,
                    "retrieval-agent",
                    answerId,
                    payload("data", payload(
                            "sourceType", "paper",
                            "sourceId", sourceId == null ? String.valueOf(chunk.documentId()) : sourceId,
                            "title", "Paper chunk " + chunk.documentId() + "#" + chunk.chunkIndex(),
                            "snippet", bounded(chunk.content()),
                            "score", chunk.finalScore(),
                            "rank", index + 1,
                            "retrievalMode", "vector"
                    ))
            );
        }

        List<WebSearchHit> webHits = webSearchResult == null || webSearchResult.hits() == null
                ? List.of()
                : webSearchResult.hits().stream().limit(MAX_TRACE_HITS).toList();
        for (int index = 0; index < webHits.size(); index++) {
            WebSearchHit hit = webHits.get(index);
            publishRunEvent(
                    WorkbenchEventType.RETRIEVAL_HIT,
                    projectId,
                    sessionId,
                    runId,
                    "retrieval-agent",
                    answerId,
                    payload("data", payload(
                            "sourceType", "web",
                            "url", safe(hit.url()),
                            "title", safe(hit.title()),
                            "provider", safe(webSearchResult.provider()),
                            "snippet", bounded(hit.snippet()),
                            "score", hit.score(),
                            "rank", index + 1,
                            "retrievalMode", "web"
                    ))
            );
        }
    }

    private void publishEvidenceGapIfNeeded(String projectId,
                                            String sessionId,
                                            String runId,
                                            String answerId,
                                            EvidenceAssessment assessment,
                                            ProjectAgentRun agentRun,
                                            RagResult ragResult,
                                            WebSearchResult webSearchResult) {
        if (!hasResearchEvidencePath(agentRun, ragResult, webSearchResult)) {
            return;
        }
        if (assessment.evidenceLevel() == EvidenceLevel.SUFFICIENT && assessment.citationCount() > 0) {
            return;
        }
        publishRunEvent(
                WorkbenchEventType.EVIDENCE_GAP_DETECTED,
                projectId,
                sessionId,
                runId,
                "evidence-boundary",
                answerId,
                payload("data", payload(
                        "claim", "Current answer has an evidence boundary.",
                        "reason", "This run lacks sufficient local paper evidence or only has weak evidence.",
                        "severity", "medium"
                ))
        );
    }

    private List<String> answerDeltas(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of("");
        }
        List<String> deltas = Arrays.stream(answer.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
        return deltas.isEmpty() ? List.of("") : deltas;
    }

    private String memorySnippet(MemoryRecallHit hit) {
        if (hit == null || hit.entry() == null) {
            return "";
        }
        MemoryEntry entry = hit.entry();
        String keyFindings = entry.keyFindings() == null || entry.keyFindings().isEmpty()
                ? ""
                : "\nFindings: " + String.join("; ", entry.keyFindings());
        return safe(entry.topic()) + "\n" + safe(entry.summary()) + keyFindings;
    }

    private boolean toolWasUsed(ProjectAgentRun agentRun, String toolName) {
        return agentRun != null
                && agentRun.toolsUsed() != null
                && agentRun.toolsUsed().contains(toolName);
    }

    private boolean hasResearchEvidencePath(ProjectAgentRun agentRun, RagResult ragResult, WebSearchResult webSearchResult) {
        return chunkCount(ragResult) > 0
                || webSearchResult != null
                || toolWasUsed(agentRun, "paper_rag")
                || toolWasUsed(agentRun, "tavily_web_search");
    }

    private List<String> sourceTypes(RagResult ragResult, WebSearchResult webSearchResult) {
        List<String> sourceTypes = new java.util.ArrayList<>();
        if (chunkCount(ragResult) > 0) {
            sourceTypes.add("paper");
        }
        if (hasWebHits(webSearchResult)) {
            sourceTypes.add("web");
        }
        return sourceTypes;
    }

    private List<String> toolsUsed(boolean memoryRecallCalled, boolean paperRagCalled, boolean webSearchCalled) {
        List<String> tools = new java.util.ArrayList<>();
        if (memoryRecallCalled) {
            tools.add("memory_recall");
        }
        if (paperRagCalled) {
            tools.add("paper_rag");
        }
        if (webSearchCalled) {
            tools.add("tavily_web_search");
        }
        return tools;
    }

    private double topPaperScore(RagResult ragResult) {
        if (chunkCount(ragResult) == 0) {
            return 0.0;
        }
        return chunksOf(ragResult).get(0).finalScore();
    }

    private String retrievalSummary(RagResult ragResult, MemoryRecallResult memoryRecallResult) {
        int paperEvidenceCount = chunkCount(ragResult);
        int memoryRecallCount = memoryHitCount(memoryRecallResult);
        return "Retrieved " + paperEvidenceCount + " paper evidence item(s) with "
                + memoryRecallCount + " memory context item(s).";
    }

    private List<RagChunk> chunksOf(RagResult ragResult) {
        return ragResult == null || ragResult.chunks() == null ? List.of() : ragResult.chunks();
    }

    private int chunkCount(RagResult ragResult) {
        return chunksOf(ragResult).size();
    }

    private int memoryHitCount(MemoryRecallResult memoryRecallResult) {
        return memoryRecallResult == null || memoryRecallResult.hits() == null
                ? 0
                : memoryRecallResult.hits().size();
    }

    private ChatResponse respondWithoutRetrieval(ChatRequest request) {
        String answer = "我需要已索引的论文、历史研究记忆，或更明确的研究问题，才能给出可靠回答。";
        workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        return new ChatResponse(request.sessionKey(), AnswerMode.LOCAL_WEAK_EVIDENCE.name(), answer, List.of());
    }

    private ChatResponse respondWithPlanExecution(ChatRequest request, WorkingMemory memory) {
        MemoryRecallResult memoryRecallResult = memoryRecallPort.recall(memory.sessionId(), request.question(), 4);
        RagResult ragResult = request.documentIds() == null || request.documentIds().isEmpty()
                ? new RagResult(request.question(), List.of(), List.of())
                : paperRagService.retrieve(memory.sessionId(), request.question(), request.documentIds(), 5);
        PlanExecutionResult planExecutionResult = planExecuteFacade.execute(request.question(), memory, memoryRecallResult, ragResult);
        AnswerMode answerMode = ragResult.chunks().isEmpty() ? AnswerMode.LOCAL_WEAK_EVIDENCE : AnswerMode.LOCAL_EVIDENCE;
        workingMemoryService.appendExchange(request.sessionKey(), request.question(), planExecutionResult.answer(), answerMode.name());
        return new ChatResponse(request.sessionKey(), answerMode.name(), planExecutionResult.answer(), citationsFrom(ragResult));
    }

    private ChatResponse respondFromMemory(ChatRequest request, WorkingMemory memory) {
        MemoryRecallResult memoryRecallResult = memoryRecallPort.recall(memory.sessionId(), request.question(), 4);
        String answer;
        if (memoryRecallResult.isEmpty()) {
            answer = "我还没有检索到足够相关的历史研究记忆，可以换个说法，或先补充更多研究过程。";
        } else {
            answer = chatClient.prompt()
                    .system("You are answering from internal long-term research memory. Be explicit that this is historical context, not external paper evidence.")
                    .user("Question: " + request.question()
                            + "\n\nWorking memory:\n" + safe(memory.rollingSummary())
                            + "\n\nGlobal knowledge:\n" + globalKnowledgeBlock()
                            + "\n\nRetrieved memory context:\n" + memoryRecallResult.contextBlock())
                    .call()
                    .content();
        }
        workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        return new ChatResponse(request.sessionKey(), AnswerMode.LOCAL_WEAK_EVIDENCE.name(), answer, List.of());
    }

    private ChatResponse respondFromPaper(ChatRequest request,
                                          WorkingMemory memory,
                                          ResearchDocument primaryDocument,
                                          MemoryRecallResult memoryRecallResult) {
        String ragQuery = request.question();
        if (memoryRecallResult != null && !memoryRecallResult.isEmpty()) {
            ragQuery = request.question() + "\nHistorical context:\n" + memoryRecallResult.contextBlock();
        }

        RagResult ragResult = paperRagService.retrieve(memory.sessionId(), ragQuery, request.documentIds(), 5);
        AnswerMode answerMode = evidenceBoundaryService.toAnswerMode(evidenceBoundaryService.assess(ragResult));

        String answer;
        if (answerMode == AnswerMode.REFUSAL) {
            answer = primaryDocument != null
                    ? "当前本地论文证据还不足以给出稳健结论。你可以先查看文档分析面板，或换成更具体的问题。"
                    : "当前本地论文证据还不足以给出稳健结论。";
        } else {
            String context = ragResult.chunks().stream()
                    .map(chunk -> "[doc=" + chunk.documentId() + ",chunk=" + chunk.chunkIndex() + "] " + chunk.content())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            answer = chatClient.prompt()
                    .system("Answer conservatively. Memory context is background only. Paper context is the evidence source.")
                    .user("Question: " + request.question()
                            + "\n\nWorking memory:\n" + safe(memory.rollingSummary())
                            + "\n\nGlobal knowledge:\n" + globalKnowledgeBlock()
                            + "\n\nMemory context:\n" + (memoryRecallResult == null ? "" : memoryRecallResult.contextBlock())
                            + "\n\nPaper evidence:\n" + context)
                    .call()
                    .content();
        }

        workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, answerMode.name());
        return new ChatResponse(request.sessionKey(), answerMode.name(), answer, citationsFrom(ragResult));
    }

    private ChatResponse respondToOverviewQuestion(ChatRequest request,
                                                   WorkingMemory memory,
                                                   Optional<String> overviewDraft) {
        RagResult ragResult = paperRagService.retrieve(memory.sessionId(), request.question(), request.documentIds(), 5);
        AnswerMode answerMode = evidenceBoundaryService.toAnswerMode(evidenceBoundaryService.assess(ragResult));

        if (answerMode != AnswerMode.REFUSAL) {
            String evidenceContext = ragResult.chunks().stream()
                    .map(chunk -> "[doc=" + chunk.documentId() + ",chunk=" + chunk.chunkIndex() + "] " + chunk.content())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            String answer = chatClient.prompt()
                    .system("Answer the paper overview conservatively. If a structured overview draft is provided, use it only when the paper evidence supports it. Cite only grounded claims from the paper evidence.")
                    .user("Question: " + request.question()
                            + "\n\nWorking memory:\n" + safe(memory.rollingSummary())
                            + "\n\nGlobal knowledge:\n" + globalKnowledgeBlock()
                            + "\n\nStructured overview draft:\n" + overviewDraft.orElse("(empty)")
                            + "\n\nPaper evidence:\n" + evidenceContext)
                    .call()
                    .content();
            workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, answerMode.name());
            return new ChatResponse(request.sessionKey(), answerMode.name(), answer, citationsFrom(ragResult));
        }

        if (overviewDraft.isPresent()) {
            String answer = overviewDraft.get();
            workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, AnswerMode.LOCAL_WEAK_EVIDENCE.name());
            return new ChatResponse(request.sessionKey(), AnswerMode.LOCAL_WEAK_EVIDENCE.name(), answer, List.of());
        }

        return null;
    }

    private List<CitationDto> citationsFrom(RagResult ragResult) {
        return ragResult.chunks().stream()
                .map(chunk -> new CitationDto(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content().substring(0, Math.min(160, chunk.content().length()))
                ))
                .toList();
    }

    private String globalKnowledgeBlock() {
        var snapshot = globalKnowledgeService.snapshot();
        return "USER:\n" + safe(snapshot.user())
                + "\n\nSOUL:\n" + safe(snapshot.soul())
                + "\n\nResearch_state:\n" + safe(snapshot.researchState());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }

    private String evidenceStateFor(String answerMode) {
        if (AnswerMode.LOCAL_EVIDENCE.name().equals(answerMode)) {
            return "SUFFICIENT";
        }
        if (AnswerMode.REFUSAL.name().equals(answerMode)) {
            return "NONE";
        }
        return "WEAK";
    }

    private String bounded(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 1200 ? value : value.substring(0, 1200);
    }

    private WorkbenchEvent publishRunEvent(
            WorkbenchEventType eventType,
            String projectId,
            String sessionId,
            String runId,
            String actor,
            String answerId,
            Map<String, Object> payload) {
        return eventPublisher.publish(new WorkbenchEvent(
                null,
                eventType,
                projectId,
                sessionId,
                runId,
                actor,
                0,
                null,
                answerId,
                null,
                null,
                payload
        ));
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            payload.put((String) keyValues[index], keyValues[index + 1]);
        }
        return payload;
    }
}
