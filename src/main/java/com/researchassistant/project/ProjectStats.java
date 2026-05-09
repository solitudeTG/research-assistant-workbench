package com.researchassistant.project;

public record ProjectStats(
        int sourceCount,
        int sessionCount,
        int knowledgeEntryCount,
        int candidateCount
) {
}
