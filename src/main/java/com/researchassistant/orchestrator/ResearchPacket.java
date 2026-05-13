package com.researchassistant.orchestrator;

import java.util.ArrayList;
import java.util.List;

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
        claims = List.copyOf(claims);
        paperEvidence = List.copyOf(paperEvidence);
        webEvidence = List.copyOf(webEvidence);
        memoryContext = List.copyOf(memoryContext);
        conflicts = List.copyOf(conflicts);
        evidenceGaps = List.copyOf(evidenceGaps);
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
                "grounded"
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
}
