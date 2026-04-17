package com.researchassistant.orchestrator;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceBoundaryService;
import com.researchassistant.evidence.EvidenceLevel;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.memory.WorkingMemoryService;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
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

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @InjectMocks
    private SupervisorService supervisorService;

    @Test
    void answerReturnsCitationsForPaperRagRoute() {
        ChatRequest request = new ChatRequest("session-42", "What does attention do?", List.of(1L));
        WorkingMemory memory = new WorkingMemory(5L, "session-42", null, null, 0);
        RagResult ragResult = new RagResult(
                request.question(),
                List.of(1L),
                List.of(new RagChunk(11L, 1L, 0, "Attention computes weighted token interactions.", 0.91))
        );

        when(workingMemoryService.load("session-42")).thenReturn(memory);
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
}
