package com.researchassistant.orchestrator;

import com.researchassistant.evidence.EvidenceCitationSource;

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

    public List<EvidenceCitationSource> acceptedCitationSources() {
        return acceptedItems().stream()
                .map(CuratedEvidenceItem::citationSource)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> acceptedEvidenceBySource(String sourceType) {
        return acceptedItems().stream()
                .filter(item -> sourceType.equals(item.sourceType()))
                .map(CuratedEvidenceItem::text)
                .toList();
    }
}
