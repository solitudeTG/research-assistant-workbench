package com.researchassistant.evidence;

import com.researchassistant.rag.RagResult;
import org.springframework.stereotype.Service;

@Service
public class EvidenceBoundaryService {

    public EvidenceLevel assess(RagResult ragResult) {
        if (ragResult.chunks().isEmpty()) {
            return EvidenceLevel.NONE;
        }

        double topScore = ragResult.chunks().get(0).finalScore();
        if (topScore >= 0.75) {
            return EvidenceLevel.SUFFICIENT;
        }
        if (topScore >= 0.35) {
            return EvidenceLevel.WEAK;
        }
        return EvidenceLevel.NONE;
    }

    public AnswerMode toAnswerMode(EvidenceLevel evidenceLevel) {
        return switch (evidenceLevel) {
            case SUFFICIENT -> AnswerMode.LOCAL_EVIDENCE;
            case WEAK -> AnswerMode.LOCAL_WEAK_EVIDENCE;
            case NONE -> AnswerMode.REFUSAL;
        };
    }
}
