package com.researchassistant.orchestrator;

import com.researchassistant.evidence.EvidenceCitationSource;

import java.util.List;
import java.util.Objects;

public record CuratedEvidenceItem(
        String sourceType,
        String text,
        boolean accepted,
        String rejectReason,
        List<String> matchedTerms,
        EvidenceCitationSource citationSource
) {

    public CuratedEvidenceItem {
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        text = Objects.requireNonNull(text, "text");
        rejectReason = rejectReason == null ? "" : rejectReason;
        matchedTerms = List.copyOf(Objects.requireNonNull(matchedTerms, "matchedTerms"));
    }

    public CuratedEvidenceItem(
            String sourceType,
            String text,
            boolean accepted,
            String rejectReason,
            List<String> matchedTerms
    ) {
        this(sourceType, text, accepted, rejectReason, matchedTerms, null);
    }
}
