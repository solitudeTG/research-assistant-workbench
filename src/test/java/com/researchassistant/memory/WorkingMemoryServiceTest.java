package com.researchassistant.memory;

import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingMemoryServiceTest extends PostgresIntegrationTest {

    @Autowired
    private WorkingMemoryService workingMemoryService;

    @Test
    void updateMemoryTracksCurrentTaskAndSummary() {
        workingMemoryService.appendExchange(
                "session-1",
                "Summarize attention",
                "Attention uses weighted context.",
                "LOCAL_EVIDENCE"
        );
        WorkingMemory second = workingMemoryService.appendExchange(
                "session-1",
                "What is multi-head attention?",
                "It splits projections across heads.",
                "LOCAL_EVIDENCE"
        );

        assertThat(second.currentTask()).isEqualTo("What is multi-head attention?");
        assertThat(second.rollingSummary()).contains("multi-head attention");
        assertThat(second.messageCount()).isEqualTo(4);
    }
}
