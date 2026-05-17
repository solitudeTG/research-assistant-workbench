package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ResearchPacket(
        String question,
        List<String> claims,
        List<String> paperEvidence,
        List<String> webEvidence,
        List<String> memoryContext,
        List<String> conflicts,
        List<String> evidenceGaps,
        String recommendedAnswerMode
) {

    public ResearchPacket {
        question = Objects.requireNonNull(question, "question");
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        paperEvidence = List.copyOf(Objects.requireNonNull(paperEvidence, "paperEvidence"));
        webEvidence = List.copyOf(Objects.requireNonNull(webEvidence, "webEvidence"));
        memoryContext = List.copyOf(Objects.requireNonNull(memoryContext, "memoryContext"));
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
        evidenceGaps = List.copyOf(Objects.requireNonNull(evidenceGaps, "evidenceGaps"));
        recommendedAnswerMode = Objects.requireNonNull(recommendedAnswerMode, "recommendedAnswerMode");
    }

    public static ResearchPacket empty(String question) {
        return new ResearchPacket(
                question,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
    }

    public ResearchPacket withEvidenceGap(String gap) {
        List<String> updatedGaps = new ArrayList<>(evidenceGaps);
        updatedGaps.add(gap);
        return new ResearchPacket(
                question,
                claims,
                paperEvidence,
                webEvidence,
                memoryContext,
                conflicts,
                updatedGaps,
                recommendedAnswerMode
        );
    }

    public ResearchPacket withEvidence(
            List<String> updatedPaperEvidence,
            List<String> updatedWebEvidence,
            List<String> updatedEvidenceGaps
    ) {
        return new ResearchPacket(
                question,
                claims,
                updatedPaperEvidence,
                updatedWebEvidence,
                memoryContext,
                conflicts,
                updatedEvidenceGaps,
                recommendedAnswerMode
        );
    }
}
