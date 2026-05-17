package com.researchassistant.orchestrator;

import java.util.List;
import java.util.Objects;

public record CuratedEvidenceSet(List<CuratedEvidenceItem> items) {

    public CuratedEvidenceSet {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public List<CuratedEvidenceItem> acceptedItems() {
        return items.stream()
                .filter(CuratedEvidenceItem::accepted)
                .toList();
    }

    public List<CuratedEvidenceItem> rejectedItems() {
        return items.stream()
                .filter(item -> !item.accepted())
                .toList();
    }

    public int acceptedCount() {
        return acceptedItems().size();
    }

    public int rejectedCount() {
        return rejectedItems().size();
    }

    public List<String> acceptedPaperEvidence() {
        return acceptedEvidenceBySource("paper");
    }

    public List<String> acceptedWebEvidence() {
        return acceptedEvidenceBySource("web");
    }

    private List<String> acceptedEvidenceBySource(String sourceType) {
        return acceptedItems().stream()
                .filter(item -> sourceType.equals(item.sourceType()))
                .map(CuratedEvidenceItem::text)
                .toList();
    }
}
