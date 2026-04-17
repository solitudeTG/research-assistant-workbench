package com.researchassistant.orchestrator;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TaskRouter {

    public RetrievalMode route(String question, List<Long> documentIds) {
        if (documentIds != null && !documentIds.isEmpty()) {
            return RetrievalMode.PAPER_RAG_ONLY;
        }

        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (normalized.contains("paper") || normalized.contains("attention") || normalized.contains("method")) {
            return RetrievalMode.PAPER_RAG_ONLY;
        }
        return RetrievalMode.NO_RETRIEVAL;
    }
}
