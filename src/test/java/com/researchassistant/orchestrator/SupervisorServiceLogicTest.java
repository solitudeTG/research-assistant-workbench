package com.researchassistant.orchestrator;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceBoundaryService;
import com.researchassistant.evidence.EvidenceLevel;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.ResearchDocument;
import com.researchassistant.memory.ExplicitMemoryService;
import com.researchassistant.memory.GlobalKnowledgeService;
import com.researchassistant.memory.GlobalKnowledgeSnapshot;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.memory.WorkingMemoryService;
import com.researchassistant.orchestrator.support.DocumentMetadataService;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisorServiceLogicTest {

    @Mock
    private TaskRouter taskRouter;

    @Mock
    private PaperRagService paperRagService;

    @Mock
    private EvidenceBoundaryService evidenceBoundaryService;

    @Mock
    private WorkingMemoryService workingMemoryService;

    @Mock
    private DocumentMetadataService documentMetadataService;

    @Mock
    private MemoryRecallPort memoryRecallPort;

    @Mock
    private ExplicitMemoryService explicitMemoryService;

    @Mock
    private GlobalKnowledgeService globalKnowledgeService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @InjectMocks
    private SupervisorService supervisorService;

    @Test
    void answerReturnsCitationsForPaperRagRoute() {
        ChatRequest request = new ChatRequest("session-42", "What does attention do?", List.of(1L));
        WorkingMemory memory = memory(5L, "session-42");
        ResearchDocument document = new ResearchDocument(
                1L,
                "attention-paper.pdf",
                "attention-paper.pdf",
                "storage/attention-paper.pdf",
                DocumentStatus.INDEXED,
                null,
                null,
                4,
                320,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        RagResult ragResult = new RagResult(
                request.question(),
                List.of(1L),
                List.of(new RagChunk(11L, 1L, 0, "Attention computes weighted token interactions.", 0.91))
        );

        when(workingMemoryService.load("session-42")).thenReturn(memory);
        when(explicitMemoryService.isExplicitMemoryRequest(request.question())).thenReturn(false);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(document);
        when(documentMetadataService.isTitleQuestion(request.question())).thenReturn(false);
        when(documentMetadataService.isOverviewQuestion(request.question())).thenReturn(false);
        when(taskRouter.route(request.question(), request.documentIds())).thenReturn(RetrievalMode.PAPER_RAG_ONLY);
        when(paperRagService.retrieve(5L, request.question(), request.documentIds(), 5)).thenReturn(ragResult);
        when(evidenceBoundaryService.assess(ragResult)).thenReturn(EvidenceLevel.SUFFICIENT);
        when(evidenceBoundaryService.toAnswerMode(EvidenceLevel.SUFFICIENT)).thenReturn(AnswerMode.LOCAL_EVIDENCE);
        when(globalKnowledgeService.snapshot()).thenReturn(new GlobalKnowledgeSnapshot("", "", ""));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Attention computes weighted token interactions.");

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_EVIDENCE.name());
        assertThat(response.citations()).hasSize(1);
        verify(workingMemoryService).appendExchange(
                "session-42",
                request.question(),
                "Attention computes weighted token interactions.",
                AnswerMode.LOCAL_EVIDENCE.name()
        );
    }

    @Test
    void answerReturnsDocumentTitleForTitleQuestion() {
        ChatRequest request = new ChatRequest("session-7", "论文题目是什么？", List.of(2L));
        WorkingMemory memory = memory(7L, "session-7");
        ResearchDocument document = new ResearchDocument(
                2L,
                "Joint Beamforming Design and Satellite Selection for Integrated Communication and Navigation in LEO Satellite Networks.pdf",
                "Joint Beamforming Design and Satellite Selection for Integrated Communication and Navigation in LEO Satellite Networks.pdf",
                "storage/paper.pdf",
                DocumentStatus.INDEXED,
                null,
                null,
                10,
                1500,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(workingMemoryService.load("session-7")).thenReturn(memory);
        when(explicitMemoryService.isExplicitMemoryRequest(request.question())).thenReturn(false);
        when(documentMetadataService.isTitleQuestion(request.question())).thenReturn(true);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(document);
        when(documentMetadataService.answerTitleQuestion(document))
                .thenReturn("The paper title is: " + document.title());

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_EVIDENCE.name());
        assertThat(response.answer()).contains("Joint Beamforming Design and Satellite Selection");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void answerUsesMemoryRecallOnlyRouteForHistoryQuestion() {
        ChatRequest request = new ChatRequest("session-10", "我们之前讨论过什么？", List.of());
        WorkingMemory memory = memory(10L, "session-10");
        MemoryEntry entry = new MemoryEntry(
                1L,
                10L,
                "COMPACTION",
                "Satellite selection",
                "We compared satellite selection strategies.",
                List.of("Compared two strategies"),
                List.of("How to validate online?"),
                List.of("satellite", "selection"),
                1L,
                4L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(workingMemoryService.load("session-10")).thenReturn(memory);
        when(explicitMemoryService.isExplicitMemoryRequest(request.question())).thenReturn(false);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(null);
        when(documentMetadataService.isTitleQuestion(request.question())).thenReturn(false);
        when(taskRouter.route(request.question(), request.documentIds())).thenReturn(RetrievalMode.MEMORY_RECALL_ONLY);
        when(memoryRecallPort.recall(10L, request.question(), 4))
                .thenReturn(new MemoryRecallResult(request.question(), List.of(new MemoryRecallHit(entry, 0.9))));
        when(globalKnowledgeService.snapshot()).thenReturn(new GlobalKnowledgeSnapshot("", "", ""));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("我们之前重点讨论了卫星选择策略和验证问题。");

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        assertThat(response.answer()).contains("卫星选择");
        verify(workingMemoryService).appendExchange(
                "session-10",
                request.question(),
                response.answer(),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
    }

    @Test
    void answerExplainsWhenDocumentIdIsMissingAfterRestart() {
        ChatRequest request = new ChatRequest("session-9", "摘要的内容是什么", List.of(2L));
        WorkingMemory memory = memory(9L, "session-9");

        when(workingMemoryService.load("session-9")).thenReturn(memory);
        when(explicitMemoryService.isExplicitMemoryRequest(request.question())).thenReturn(false);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(null);

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        assertThat(response.answer()).contains("重新上传");
        verifyNoInteractions(taskRouter, paperRagService, evidenceBoundaryService, chatClient, memoryRecallPort);
    }

    private WorkingMemory memory(long sessionId, String sessionKey) {
        return new WorkingMemory(sessionId, sessionKey, null, null, List.of(), List.of(), 0L, null, 0);
    }
}
