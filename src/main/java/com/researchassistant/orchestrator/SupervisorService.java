package com.researchassistant.orchestrator;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.chat.dto.CitationDto;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceBoundaryService;
import com.researchassistant.ingest.model.ResearchDocument;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.memory.WorkingMemoryService;
import com.researchassistant.orchestrator.support.DocumentMetadataService;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagResult;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SupervisorService {

    private final TaskRouter taskRouter;
    private final PaperRagService paperRagService;
    private final EvidenceBoundaryService evidenceBoundaryService;
    private final WorkingMemoryService workingMemoryService;
    private final DocumentMetadataService documentMetadataService;
    private final ChatClient chatClient;

    public SupervisorService(
            TaskRouter taskRouter,
            PaperRagService paperRagService,
            EvidenceBoundaryService evidenceBoundaryService,
            WorkingMemoryService workingMemoryService,
            DocumentMetadataService documentMetadataService,
            ChatClient chatClient) {
        this.taskRouter = taskRouter;
        this.paperRagService = paperRagService;
        this.evidenceBoundaryService = evidenceBoundaryService;
        this.workingMemoryService = workingMemoryService;
        this.documentMetadataService = documentMetadataService;
        this.chatClient = chatClient;
    }

    public ChatResponse answer(ChatRequest request) {
        WorkingMemory memory = workingMemoryService.load(request.sessionKey());
        ResearchDocument primaryDocument = documentMetadataService.findPrimaryDocument(request.documentIds());

        if (request.documentIds() != null && !request.documentIds().isEmpty() && primaryDocument == null) {
            String answer = "当前文档不存在、尚未完成索引，或在重启后已失效，请重新上传论文后再提问。";
            workingMemoryService.appendExchange(
                    request.sessionKey(),
                    request.question(),
                    answer,
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name()
            );
            return new ChatResponse(
                    request.sessionKey(),
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                    answer,
                    List.of()
            );
        }

        if (documentMetadataService.isTitleQuestion(request.question())) {
            String answer = documentMetadataService.answerTitleQuestion(primaryDocument);
            workingMemoryService.appendExchange(
                    request.sessionKey(),
                    request.question(),
                    answer,
                    AnswerMode.LOCAL_EVIDENCE.name()
            );
            return new ChatResponse(
                    request.sessionKey(),
                    AnswerMode.LOCAL_EVIDENCE.name(),
                    answer,
                    List.of()
            );
        }

        if (documentMetadataService.isOverviewQuestion(request.question()) && primaryDocument != null) {
            String answer = documentMetadataService.answerOverviewQuestion(primaryDocument);
            workingMemoryService.appendExchange(
                    request.sessionKey(),
                    request.question(),
                    answer,
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name()
            );
            return new ChatResponse(
                    request.sessionKey(),
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                    answer,
                    List.of()
            );
        }

        RetrievalMode retrievalMode = taskRouter.route(request.question(), request.documentIds());

        if (retrievalMode == RetrievalMode.NO_RETRIEVAL) {
            String answer = "I need indexed local papers or a paper-grounded question to answer reliably.";
            workingMemoryService.appendExchange(
                    request.sessionKey(),
                    request.question(),
                    answer,
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name()
            );
            return new ChatResponse(
                    request.sessionKey(),
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                    answer,
                    List.of()
            );
        }

        RagResult ragResult = paperRagService.retrieve(memory.sessionId(), request.question(), request.documentIds(), 5);
        AnswerMode answerMode = evidenceBoundaryService.toAnswerMode(evidenceBoundaryService.assess(ragResult));

        String answer;
        if (answerMode == AnswerMode.REFUSAL) {
            answer = "Local indexed evidence is not strong enough for a grounded answer yet.";
        } else {
            String context = ragResult.chunks().stream()
                    .map(chunk -> "[doc=" + chunk.documentId() + ",chunk=" + chunk.chunkIndex() + "] " + chunk.content())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            answer = chatClient.prompt()
                    .system("Answer only from the provided context. If the context is weak, stay conservative.")
                    .user("Question: " + request.question() + "\n\nContext:\n" + context)
                    .call()
                    .content();
        }

        workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, answerMode.name());

        List<CitationDto> citations = ragResult.chunks().stream()
                .map(chunk -> new CitationDto(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content().substring(0, Math.min(160, chunk.content().length()))
                ))
                .toList();

        return new ChatResponse(request.sessionKey(), answerMode.name(), answer, citations);
    }
}
