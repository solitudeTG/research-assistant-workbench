package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchPort;
import com.researchassistant.websearch.WebSearchResult;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DeepResearchAgent {

    private static final int PAPER_LIMIT = 5;
    private static final int WEB_LIMIT = 5;
    private static final int MEMORY_LIMIT = 4;

    private final PaperRagService paperRagService;
    private final WebSearchPort webSearchPort;
    private final MemoryRecallPort memoryRecallPort;

    public DeepResearchAgent(
            PaperRagService paperRagService,
            WebSearchPort webSearchPort,
            MemoryRecallPort memoryRecallPort
    ) {
        this.paperRagService = Objects.requireNonNull(paperRagService, "paperRagService");
        this.webSearchPort = Objects.requireNonNull(webSearchPort, "webSearchPort");
        this.memoryRecallPort = Objects.requireNonNull(memoryRecallPort, "memoryRecallPort");
    }

    public ResearchPacket research(
            long sessionId,
            String question,
            ProjectEvidenceScope evidenceScope,
            boolean allowWebSupplement
    ) {
        String normalizedQuestion = Objects.requireNonNull(question, "question").trim();
        List<String> paperEvidence = retrievePaperEvidence(sessionId, normalizedQuestion, evidenceScope);
        List<String> webEvidence = retrieveWebEvidence(normalizedQuestion, allowWebSupplement);
        List<String> memoryContext = recallMemory(sessionId, normalizedQuestion);
        List<String> evidenceGaps = paperEvidence.isEmpty() && webEvidence.isEmpty()
                ? List.of("No paper or web evidence was available for the question.")
                : List.of();

        return new ResearchPacket(
                normalizedQuestion,
                List.of(),
                paperEvidence,
                webEvidence,
                memoryContext,
                List.of(),
                evidenceGaps,
                recommendedAnswerMode(paperEvidence, webEvidence)
        );
    }

    private List<String> retrievePaperEvidence(long sessionId, String question, ProjectEvidenceScope evidenceScope) {
        if (evidenceScope == null || !evidenceScope.hasScopedPaperEvidence()) {
            return List.of();
        }
        RagResult result = paperRagService.retrieve(sessionId, question, evidenceScope.indexedDocumentIds(), PAPER_LIMIT);
        if (result == null || result.chunks() == null) {
            return List.of();
        }
        return result.chunks().stream()
                .map(this::formatPaperEvidence)
                .toList();
    }

    private List<String> retrieveWebEvidence(String question, boolean allowWebSupplement) {
        if (!allowWebSupplement) {
            return List.of();
        }
        WebSearchResult result = webSearchPort.search(question, WEB_LIMIT);
        if (result == null || result.hits() == null) {
            return List.of();
        }
        return result.hits().stream()
                .map(this::formatWebEvidence)
                .toList();
    }

    private List<String> recallMemory(long sessionId, String question) {
        MemoryRecallResult result = memoryRecallPort.recall(sessionId, question, MEMORY_LIMIT);
        if (result == null || result.hits() == null) {
            return List.of();
        }
        return result.hits().stream()
                .map(this::formatMemoryContext)
                .toList();
    }

    private String formatPaperEvidence(RagChunk chunk) {
        return "paper: documentId=%d chunkIndex=%d score=%s content=%s".formatted(
                chunk.documentId(),
                chunk.chunkIndex(),
                score(chunk.finalScore()),
                chunk.content()
        );
    }

    private String formatWebEvidence(WebSearchHit hit) {
        return "web: title=%s url=%s score=%s snippet=%s".formatted(
                hit.title(),
                hit.url(),
                score(hit.score()),
                hit.snippet()
        );
    }

    private String formatMemoryContext(MemoryRecallHit hit) {
        return "memory: topic=%s score=%s summary=%s".formatted(
                hit.entry().topic(),
                score(hit.finalScore()),
                hit.entry().summary()
        );
    }

    private String recommendedAnswerMode(List<String> paperEvidence, List<String> webEvidence) {
        if (!webEvidence.isEmpty()) {
            return AnswerMode.WEB_SUPPLEMENT.name();
        }
        if (!paperEvidence.isEmpty()) {
            return AnswerMode.LOCAL_EVIDENCE.name();
        }
        return AnswerMode.LOCAL_WEAK_EVIDENCE.name();
    }

    private String score(double score) {
        return String.format(Locale.ROOT, "%.2f", score);
    }
}
