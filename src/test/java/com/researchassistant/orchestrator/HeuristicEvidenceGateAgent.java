package com.researchassistant.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class HeuristicEvidenceGateAgent implements EvidenceGateAgent {

    private static final String SEMANTIC_OFF_TOPIC = "SEMANTIC_OFF_TOPIC";

    @Override
    public CuratedEvidenceSet gate(String question, CuratedEvidenceSet hygienicCandidates) {
        String normalizedQuestion = normalize(question);
        List<CuratedEvidenceItem> gated = new ArrayList<>();
        for (CuratedEvidenceItem item : hygienicCandidates.items()) {
            if (!item.accepted()) {
                gated.add(item);
                continue;
            }
            if (isRelevant(normalizedQuestion, normalize(item.text()))) {
                gated.add(item);
            } else {
                gated.add(new CuratedEvidenceItem(
                        item.sourceType(),
                        item.text(),
                        false,
                        SEMANTIC_OFF_TOPIC,
                        List.of()
                ));
            }
        }
        return new CuratedEvidenceSet(gated);
    }

    private boolean isRelevant(String question, String evidence) {
        Set<String> requiredDomain = satelliteDomainTerms(question);
        if (!requiredDomain.isEmpty()) {
            return requiredDomain.stream().anyMatch(evidence::contains);
        }
        return true;
    }

    private Set<String> satelliteDomainTerms(String question) {
        if (containsAny(
                question,
                "satellite",
                "leo",
                "interference",
                "beamforming",
                "communication",
                "navigation",
                "\u536b\u661f",
                "\u8fd1\u90bb\u661f",
                "\u5e72\u6d89",
                "\u6ce2\u675f",
                "\u901a\u4fe1",
                "\u5bfc\u822a"
        )) {
            return Set.of(
                    "satellite",
                    "leo",
                    "interference",
                    "beamforming",
                    "communication",
                    "navigation",
                    "constellation",
                    "wireless",
                    "\u536b\u661f",
                    "\u8fd1\u90bb\u661f",
                    "\u5e72\u6d89",
                    "\u6ce2\u675f",
                    "\u901a\u4fe1",
                    "\u5bfc\u822a"
            );
        }
        return Set.of();
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
