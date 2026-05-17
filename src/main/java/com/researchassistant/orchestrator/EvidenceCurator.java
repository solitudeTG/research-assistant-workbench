package com.researchassistant.orchestrator;

import com.researchassistant.evidence.EvidenceCitationSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class EvidenceCurator {

    private static final String EMPTY_OR_NAVIGATION_PAGE = "EMPTY_OR_NAVIGATION_PAGE";
    private static final String ADMINISTRATIVE_METADATA = "ADMINISTRATIVE_METADATA";
    private static final String REFERENCE_FRAGMENT = "REFERENCE_FRAGMENT";
    private static final String CODE_OR_MARKUP = "CODE_OR_MARKUP";
    private static final String TOO_FRAGMENTED = "TOO_FRAGMENTED";

    public CuratedEvidenceSet curate(String question, ResearchPacket packet) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(packet, "packet");
        List<CuratedEvidenceItem> items = new ArrayList<>();
        addEvidence(items, "paper", packet.paperEvidence(), citationSources(packet, "paper"));
        addEvidence(items, "web", packet.webEvidence(), citationSources(packet, "web"));
        return new CuratedEvidenceSet(items);
    }

    public ResearchPacket curatedPacket(String question, ResearchPacket packet) {
        CuratedEvidenceSet set = curate(question, packet);
        List<String> gaps = new ArrayList<>(packet.evidenceGaps());
        if (set.rejectedCount() > 0) {
            gaps.add("Excluded " + set.rejectedCount() + " raw evidence candidate during hygiene filtering.");
        }
        if (set.acceptedCount() == 0 && (!packet.paperEvidence().isEmpty() || !packet.webEvidence().isEmpty())) {
            gaps.add("No hygienic paper or web evidence survived curation.");
        }
        return packet.withEvidence(set.acceptedPaperEvidence(), set.acceptedWebEvidence(), gaps);
    }

    private void addEvidence(
            List<CuratedEvidenceItem> items,
            String sourceType,
            List<String> evidence,
            List<EvidenceCitationSource> citationSources
    ) {
        for (int index = 0; index < evidence.size(); index++) {
            EvidenceCitationSource citationSource = index < citationSources.size() ? citationSources.get(index) : null;
            items.add(curateOne(sourceType, evidence.get(index), citationSource));
        }
    }

    private List<EvidenceCitationSource> citationSources(ResearchPacket packet, String sourceType) {
        return packet.citationSources().stream()
                .filter(source -> sourceType.equals(source.sourceType()))
                .toList();
    }

    private CuratedEvidenceItem curateOne(
            String sourceType,
            String evidence,
            EvidenceCitationSource citationSource
    ) {
        String text = evidence == null ? "" : evidence.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        String rejectReason = rejectReason(text, normalized);
        return new CuratedEvidenceItem(
                sourceType,
                text,
                rejectReason.isBlank(),
                rejectReason,
                List.of(),
                citationSource
        );
    }

    private String rejectReason(String text, String normalized) {
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

}
