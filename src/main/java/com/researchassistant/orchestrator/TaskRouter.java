package com.researchassistant.orchestrator;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TaskRouter {

    public RetrievalMode route(String question, List<Long> documentIds) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean referencesHistory = normalized.contains("之前")
                || normalized.contains("上次")
                || normalized.contains("继续")
                || normalized.contains("延续")
                || normalized.contains("最近")
                || normalized.contains("昨天")
                || normalized.contains("discussed before")
                || normalized.contains("previously")
                || normalized.contains("last time");

        if (referencesHistory && documentIds != null && !documentIds.isEmpty()) {
            return RetrievalMode.MEMORY_THEN_PAPER;
        }

        if (referencesHistory) {
            return RetrievalMode.MEMORY_RECALL_ONLY;
        }

        if (documentIds != null && !documentIds.isEmpty()) {
            return RetrievalMode.PAPER_RAG_ONLY;
        }

        if (normalized.contains("paper") || normalized.contains("attention") || normalized.contains("method")) {
            return RetrievalMode.PAPER_RAG_ONLY;
        }
        return RetrievalMode.NO_RETRIEVAL;
    }
}
