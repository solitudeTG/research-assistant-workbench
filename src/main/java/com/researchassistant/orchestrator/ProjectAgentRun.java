package com.researchassistant.orchestrator;

import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.rag.RagResult;
import com.researchassistant.websearch.WebSearchResult;
import java.util.List;

public record ProjectAgentRun(
        String answer,
        RagResult ragResult,
        WebSearchResult webSearchResult,
        MemoryRecallResult memoryRecallResult,
        List<String> toolsUsed
) {
}
