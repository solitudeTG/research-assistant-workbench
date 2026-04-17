package com.researchassistant.memory;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingMemoryServiceLogicTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private WorkingMemoryService workingMemoryService;

    @Test
    void appendExchangeBuildsSummaryAndUpdatesMessageCount() {
        WorkingMemory memory = new WorkingMemory(7L, "session-1", null, null, 0);
        when(chatSessionRepository.findOrCreate("session-1")).thenReturn(memory);
        when(chatMessageRepository.latestContents(7L, 4)).thenReturn(List.of(
                "It splits projections across heads.",
                "What is multi-head attention?",
                "Attention uses weighted context.",
                "Summarize attention"
        ));
        when(chatMessageRepository.count(7L)).thenReturn(4);

        WorkingMemory updated = workingMemoryService.appendExchange(
                "session-1",
                "What is multi-head attention?",
                "It splits projections across heads.",
                "LOCAL_EVIDENCE"
        );

        assertThat(updated.currentTask()).isEqualTo("What is multi-head attention?");
        assertThat(updated.rollingSummary()).isEqualTo(
                "Summarize attention | Attention uses weighted context. | What is multi-head attention? | It splits projections across heads."
        );
        assertThat(updated.messageCount()).isEqualTo(4);

        verify(chatMessageRepository).append(7L, "USER", "What is multi-head attention?", null);
        verify(chatMessageRepository).append(7L, "ASSISTANT", "It splits projections across heads.", "LOCAL_EVIDENCE");
        verify(chatSessionRepository).updateSummary(
                7L,
                "What is multi-head attention?",
                "Summarize attention | Attention uses weighted context. | What is multi-head attention? | It splits projections across heads."
        );
    }
}
