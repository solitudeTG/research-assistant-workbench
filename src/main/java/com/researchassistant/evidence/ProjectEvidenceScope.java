package com.researchassistant.evidence;

import java.util.List;
import java.util.Map;

public record ProjectEvidenceScope(
        List<Long> indexedDocumentIds,
        Map<Long, String> sourceIdByIndexedDocumentId
) {

    public boolean hasScopedPaperEvidence() {
        return !indexedDocumentIds.isEmpty();
    }
}
