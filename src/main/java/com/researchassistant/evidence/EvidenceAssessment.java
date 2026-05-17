package com.researchassistant.evidence;

public record EvidenceAssessment(
        EvidenceLevel evidenceLevel,
        AnswerMode answerMode,
        int citationCount
) {
}
