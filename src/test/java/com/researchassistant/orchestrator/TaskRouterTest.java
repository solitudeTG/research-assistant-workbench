package com.researchassistant.orchestrator;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRouterTest {

    private final TaskRouter taskRouter = new TaskRouter();

    @Test
    void routeUsesMemoryThenPaperForHistoryQuestionWithDocumentScope() {
        assertThat(taskRouter.route("继续我们上次关于这篇论文的讨论", List.of(1L)))
                .isEqualTo(RetrievalMode.MEMORY_THEN_PAPER);
    }

    @Test
    void routeUsesMemoryOnlyForHistoryQuestionWithoutDocumentScope() {
        assertThat(taskRouter.route("我们之前讨论过什么？", List.of()))
                .isEqualTo(RetrievalMode.MEMORY_RECALL_ONLY);
    }
}
