package com.researchassistant.orchestrator;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.chat.dto.CitationDto;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceBoundaryService;
import com.researchassistant.ingest.model.ResearchDocument;
import com.researchassistant.memory.ExplicitMemoryService;
import com.researchassistant.memory.GlobalKnowledgeService;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.memory.WorkingMemoryService;
import com.researchassistant.orchestrator.support.DocumentMetadataService;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagResult;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SupervisorService {

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
            ChatClient chatClient) {
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
            if (overviewAnswer.isPresent()) {
                workingMemoryService.appendExchange(
                        request.sessionKey(),
                        request.question(),
                        overviewAnswer.get(),
                        AnswerMode.LOCAL_WEAK_EVIDENCE.name()
                );
                return new ChatResponse(
                        request.sessionKey(),
                        AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                        overviewAnswer.get(),
                        List.of()
                );
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
        };
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
}
