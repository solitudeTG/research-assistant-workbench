package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceCitationSource;
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
        List<EvidenceCitationSource> paperSources = retrievePaperSources(sessionId, normalizedQuestion, evidenceScope);
        List<EvidenceCitationSource> webSources = retrieveWebSources(normalizedQuestion, allowWebSupplement);
        List<String> paperEvidence = paperSources.stream().map(this::formatEvidenceSource).toList();
        List<String> webEvidence = webSources.stream().map(this::formatEvidenceSource).toList();
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
                recommendedAnswerMode(paperEvidence, webEvidence),
                citationSources(paperSources, webSources)
        );
    }

    private List<EvidenceCitationSource> retrievePaperSources(
            long sessionId,
            String question,
            ProjectEvidenceScope evidenceScope
    ) {
        if (evidenceScope == null || !evidenceScope.hasScopedPaperEvidence()) {
            return List.of();
        }
        RagResult result = paperRagService.retrieve(sessionId, question, evidenceScope.indexedDocumentIds(), PAPER_LIMIT);
        if (result == null || result.chunks() == null) {
            return List.of();
        }
        return result.chunks().stream()
                .map(this::paperCitationSource)
                .toList();
    }

    private List<EvidenceCitationSource> retrieveWebSources(String question, boolean allowWebSupplement) {
        if (!allowWebSupplement) {
            return List.of();
        }
        WebSearchResult result = webSearchPort.search(question, WEB_LIMIT);
        if (result == null || result.hits() == null) {
            return List.of();
        }
        List<EvidenceCitationSource> sources = new java.util.ArrayList<>();
        for (int index = 0; index < result.hits().size(); index++) {
            WebSearchHit hit = result.hits().get(index);
            sources.add(webCitationSource(result, hit, index + 1));
        }
        return List.copyOf(sources);
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

    private EvidenceCitationSource paperCitationSource(RagChunk chunk) {
        return EvidenceCitationSource.paper(
                chunk.content(),
                chunk.documentId(),
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.finalScore()
        );
    }

    private EvidenceCitationSource webCitationSource(WebSearchResult result, WebSearchHit hit, int rank) {
        return EvidenceCitationSource.web(
                hit.snippet(),
                hit.title(),
                hit.url(),
                result.provider(),
                rank,
                hit.score()
        );
    }

    private List<EvidenceCitationSource> citationSources(
            List<EvidenceCitationSource> paperSources,
            List<EvidenceCitationSource> webSources
    ) {
        List<EvidenceCitationSource> sources = new java.util.ArrayList<>();
        sources.addAll(paperSources);
        sources.addAll(webSources);
        return List.copyOf(sources);
    }

    private String formatEvidenceSource(EvidenceCitationSource source) {
        if ("paper".equals(source.sourceType())) {
            return formatPaperEvidence(source);
        }
        if ("web".equals(source.sourceType())) {
            return formatWebEvidence(source);
        }
        return source.text();
    }

    private String formatPaperEvidence(EvidenceCitationSource source) {
        return "paper: documentId=%d chunkIndex=%d score=%s content=%s".formatted(
                source.documentId(),
                source.chunkIndex(),
                score(source.score()),
                source.text()
        );
    }

    private String formatWebEvidence(EvidenceCitationSource source) {
        return "web: title=%s url=%s score=%s snippet=%s".formatted(
                source.title(),
                source.url(),
                score(source.score()),
                source.text()
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
