package com.researchassistant.orchestrator;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceBoundaryService;
import com.researchassistant.evidence.EvidenceLevel;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.ResearchDocument;
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

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @InjectMocks
    private SupervisorService supervisorService;

    @Test
    void answerReturnsCitationsForPaperRagRoute() {
        ChatRequest request = new ChatRequest("session-42", "What does attention do?", List.of(1L));
        WorkingMemory memory = new WorkingMemory(5L, "session-42", null, null, 0);
        ResearchDocument document = new ResearchDocument(
                1L,
                "attention-paper.pdf",
                "attention-paper.pdf",
                "storage/attention-paper.pdf",
                DocumentStatus.INDEXED,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        RagResult ragResult = new RagResult(
                request.question(),
                List.of(1L),
                List.of(new RagChunk(11L, 1L, 0, "Attention computes weighted token interactions.", 0.91))
        );

        when(workingMemoryService.load("session-42")).thenReturn(memory);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(document);
        when(documentMetadataService.isTitleQuestion(request.question())).thenReturn(false);
        when(documentMetadataService.isOverviewQuestion(request.question())).thenReturn(false);
        when(taskRouter.route(request.question(), request.documentIds())).thenReturn(RetrievalMode.PAPER_RAG_ONLY);
        when(paperRagService.retrieve(5L, request.question(), request.documentIds(), 5)).thenReturn(ragResult);
        when(evidenceBoundaryService.assess(ragResult)).thenReturn(EvidenceLevel.SUFFICIENT);
        when(evidenceBoundaryService.toAnswerMode(EvidenceLevel.SUFFICIENT)).thenReturn(AnswerMode.LOCAL_EVIDENCE);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Attention computes weighted token interactions.");

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_EVIDENCE.name());
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentId()).isEqualTo(1L);
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
        WorkingMemory memory = new WorkingMemory(7L, "session-7", null, null, 0);
        ResearchDocument document = new ResearchDocument(
                2L,
                "Joint Beamforming Design and Satellite Selection for Integrated Communication and Navigation in LEO Satellite Networks.pdf",
                "Joint Beamforming Design and Satellite Selection for Integrated Communication and Navigation in LEO Satellite Networks.pdf",
                "storage/paper.pdf",
                DocumentStatus.INDEXED,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(workingMemoryService.load("session-7")).thenReturn(memory);
        when(documentMetadataService.isTitleQuestion(request.question())).thenReturn(true);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(document);
        when(documentMetadataService.answerTitleQuestion(document))
                .thenReturn("The paper title is: " + document.title());

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_EVIDENCE.name());
        assertThat(response.answer()).contains("Joint Beamforming Design and Satellite Selection");
        assertThat(response.citations()).isEmpty();
        verify(workingMemoryService).appendExchange(
                "session-7",
                request.question(),
                response.answer(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
    }
    @Test
    void answerFallsBackToTitleBasedOverviewWhenRetrievalEvidenceIsMissing() {
        ChatRequest request = new ChatRequest("session-8", "这篇论文研究了什么？", List.of(3L));
        WorkingMemory memory = new WorkingMemory(8L, "session-8", null, null, 0);
        ResearchDocument document = new ResearchDocument(
                3L,
                "卫星选星-深度学习.pdf",
                "卫星选星-深度学习.pdf",
                "storage/paper.pdf",
                DocumentStatus.INDEXED,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(workingMemoryService.load("session-8")).thenReturn(memory);
        when(documentMetadataService.isTitleQuestion(request.question())).thenReturn(false);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(document);
        when(documentMetadataService.isOverviewQuestion(request.question())).thenReturn(true);
        when(documentMetadataService.answerOverviewQuestion(document))
                .thenReturn("从论文标题看，这篇论文主要研究：卫星选星-深度学习。");

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        assertThat(response.answer()).contains("卫星选星-深度学习");
        assertThat(response.citations()).isEmpty();
        verify(workingMemoryService).appendExchange(
                "session-8",
                request.question(),
                response.answer(),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
        verifyNoInteractions(taskRouter, paperRagService, evidenceBoundaryService, chatClient);
    }
    @Test
    void answerExplainsWhenDocumentIdIsMissingAfterRestart() {
        ChatRequest request = new ChatRequest("session-9", "\u6458\u8981\u7684\u5185\u5bb9\u662f\u4ec0\u4e48", List.of(2L));
        WorkingMemory memory = new WorkingMemory(9L, "session-9", null, null, 0);

        when(workingMemoryService.load("session-9")).thenReturn(memory);
        when(documentMetadataService.findPrimaryDocument(request.documentIds())).thenReturn(null);

        ChatResponse response = supervisorService.answer(request);

        assertThat(response.answerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        assertThat(response.answer()).contains("\u91cd\u65b0\u4e0a\u4f20");
        assertThat(response.citations()).isEmpty();
        verify(workingMemoryService).appendExchange(
                "session-9",
                request.question(),
                response.answer(),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
        verifyNoInteractions(taskRouter, paperRagService, evidenceBoundaryService, chatClient);
    }
}
