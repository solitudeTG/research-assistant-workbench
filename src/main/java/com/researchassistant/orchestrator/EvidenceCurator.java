package com.researchassistant.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EvidenceCurator {

    private static final String OFF_TOPIC = "OFF_TOPIC";
    private static final String EMPTY_OR_NAVIGATION_PAGE = "EMPTY_OR_NAVIGATION_PAGE";
    private static final String ADMINISTRATIVE_METADATA = "ADMINISTRATIVE_METADATA";
    private static final String REFERENCE_FRAGMENT = "REFERENCE_FRAGMENT";
    private static final String CODE_OR_MARKUP = "CODE_OR_MARKUP";
    private static final String TOO_FRAGMENTED = "TOO_FRAGMENTED";

    public CuratedEvidenceSet curate(String question, ResearchPacket packet) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(packet, "packet");
        List<String> topicTerms = topicTerms(question);
        boolean requireTopicMatch = hasDomainTopicSignal(question);
        List<CuratedEvidenceItem> items = new ArrayList<>();
        packet.paperEvidence().forEach(evidence -> items.add(curateOne("paper", evidence, topicTerms, requireTopicMatch)));
        packet.webEvidence().forEach(evidence -> items.add(curateOne("web", evidence, topicTerms, requireTopicMatch)));
        return new CuratedEvidenceSet(items);
    }

    public ResearchPacket curatedPacket(String question, ResearchPacket packet) {
        CuratedEvidenceSet set = curate(question, packet);
        List<String> gaps = new ArrayList<>(packet.evidenceGaps());
        if (set.rejectedCount() > 0) {
            gaps.add("Rejected " + set.rejectedCount() + " raw evidence candidate during curation.");
        }
        if (set.acceptedCount() == 0 && (!packet.paperEvidence().isEmpty() || !packet.webEvidence().isEmpty())) {
            gaps.add("No topic-matched paper or web evidence survived curation.");
        }
        return packet.withEvidence(set.acceptedPaperEvidence(), set.acceptedWebEvidence(), gaps);
    }

    private CuratedEvidenceItem curateOne(
            String sourceType,
            String evidence,
            List<String> topicTerms,
            boolean requireTopicMatch
    ) {
        String text = evidence == null ? "" : evidence.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> matchedTerms = matchedTerms(normalized, topicTerms);
        String rejectReason = rejectReason(text, normalized, matchedTerms, requireTopicMatch);
        return new CuratedEvidenceItem(
                sourceType,
                text,
                rejectReason.isBlank(),
                rejectReason,
                matchedTerms
        );
    }

    private String rejectReason(
            String text,
            String normalized,
            List<String> matchedTerms,
            boolean requireTopicMatch
    ) {
        if (text.isBlank()
                || normalized.contains("no information is available for this page")
                || normalized.contains("learn why")) {
            return EMPTY_OR_NAVIGATION_PAGE;
        }
        if (looksLikeCodeOrMarkup(normalized)) {
            return CODE_OR_MARKUP;
        }
        if (looksAdministrative(normalized)) {
            return ADMINISTRATIVE_METADATA;
        }
        if (looksLikeReferenceFragment(normalized)) {
            return REFERENCE_FRAGMENT;
        }
        if (text.length() < 24 || normalized.split("\\s+").length < 4) {
            return TOO_FRAGMENTED;
        }
        if (requireTopicMatch && matchedTerms.isEmpty()) {
            return OFF_TOPIC;
        }
        return "";
    }

    private boolean looksAdministrative(String normalized) {
        return List.of(
                "grant",
                "corresponding author",
                "associate editor",
                "copyright",
                "presented in part",
                "e-mail",
                "email",
                "affiliation",
                "university",
                "funded by",
                "msit"
        ).stream().anyMatch(normalized::contains);
    }

    private boolean looksLikeReferenceFragment(String normalized) {
        return normalized.contains("references")
                || normalized.matches(".*\\[[0-9]+].*")
                || normalized.contains("ieee transactions");
    }

    private boolean looksLikeCodeOrMarkup(String normalized) {
        return normalized.contains("import dash")
                || normalized.contains("dbc.")
                || normalized.contains("dcc.")
                || normalized.contains("html.")
                || normalized.contains("<html")
                || normalized.contains("</div>")
                || normalized.contains("function(")
                || normalized.contains("@app.callback");
    }

    private List<String> topicTerms(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (token.length() >= 4) {
                terms.add(token);
            }
        }
        if (containsAny(normalized, "satellite", "leo", "\u536b\u661f", "\u8fd1\u90bb\u661f")) {
            terms.addAll(List.of("satellite", "leo", "constellation", "orbit", "wireless"));
        }
        if (containsAny(normalized, "interference", "\u5e72\u6d89")) {
            terms.addAll(List.of("interference", "mitigation", "coordination"));
        }
        if (containsAny(normalized, "beamforming", "\u6ce2\u675f")) {
            terms.addAll(List.of("beamforming", "beam", "gdop"));
        }
        if (containsAny(normalized, "communication", "\u901a\u4fe1")) {
            terms.addAll(List.of("communication", "wireless", "network"));
        }
        if (containsAny(normalized, "navigation", "\u5bfc\u822a")) {
            terms.addAll(List.of("navigation", "ican", "gdop"));
        }
        if (terms.isEmpty()) {
            terms.addAll(List.of("evidence", "research", "paper", "study", "result", "claim"));
        }
        return List.copyOf(terms);
    }

    private boolean hasDomainTopicSignal(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return containsAny(
                normalized,
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
        );
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<String> matchedTerms(String normalizedEvidence, List<String> topicTerms) {
        return topicTerms.stream()
                .filter(normalizedEvidence::contains)
                .distinct()
                .toList();
    }
}
