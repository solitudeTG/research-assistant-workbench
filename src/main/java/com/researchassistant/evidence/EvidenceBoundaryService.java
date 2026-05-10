package com.researchassistant.evidence;

import com.researchassistant.rag.RagResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvidenceBoundaryService {

    public EvidenceLevel assess(RagResult ragResult) {
        var chunks = chunksOf(ragResult);
        if (chunks.isEmpty()) {
            return EvidenceLevel.NONE;
        }

        double topScore = chunks.get(0).finalScore();
        if (topScore >= 0.75) {
            return EvidenceLevel.SUFFICIENT;
        }
        if (topScore >= 0.35) {
            return EvidenceLevel.WEAK;
        }
        return EvidenceLevel.NONE;
    }

    public AnswerMode toAnswerMode(EvidenceLevel evidenceLevel) {
        return toAnswerMode(evidenceLevel, false);
    }

    public EvidenceAssessment assessProjectEvidence(RagResult ragResult, boolean allowWebSupplement) {
        EvidenceLevel evidenceLevel = assess(ragResult);
        return new EvidenceAssessment(
                evidenceLevel,
                toAnswerMode(evidenceLevel, allowWebSupplement),
                chunksOf(ragResult).size()
        );
    }

    public AnswerMode toAnswerMode(EvidenceLevel evidenceLevel, boolean allowWebSupplement) {
        return switch (evidenceLevel) {
            case SUFFICIENT -> AnswerMode.LOCAL_EVIDENCE;
            case WEAK -> allowWebSupplement ? AnswerMode.WEB_SUPPLEMENT : AnswerMode.LOCAL_WEAK_EVIDENCE;
            case NONE -> AnswerMode.REFUSAL;
        };
    }

    private List<com.researchassistant.rag.RagChunk> chunksOf(RagResult ragResult) {
        return ragResult == null || ragResult.chunks() == null ? List.of() : ragResult.chunks();
    }
}
