package com.researchassistant.knowledge;

public record ProjectKnowledgeRecallHit(
        KnowledgeEntryRecord entry,
        double finalScore,
        double semanticScore,
        double evidenceScore,
        double recencyScore,
        String reason
) {
}
