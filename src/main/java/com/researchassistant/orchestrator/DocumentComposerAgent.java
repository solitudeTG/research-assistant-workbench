package com.researchassistant.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DocumentComposerAgent {

    public DocumentDraft compose(
            String requestedFormat,
            String title,
            ResearchPacket packet,
            AuditVerdict verdict
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(verdict, "verdict");
        String format = requestedFormat == null || requestedFormat.isBlank() ? "markdown" : requestedFormat.trim();
        String normalizedTitle = title == null || title.isBlank() ? packet.question() : title.trim();

        List<DocumentDraft.Section> sections = verdict.isPassing()
                ? passingSections(packet, verdict)
                : revisionSections(verdict);
        String body = renderBody(normalizedTitle, sections);
        return new DocumentDraft(format, normalizedTitle, body, sections);
    }

    private List<DocumentDraft.Section> passingSections(ResearchPacket packet, AuditVerdict verdict) {
        List<DocumentDraft.Section> sections = new ArrayList<>();
        sections.add(new DocumentDraft.Section(
                "Answer Mode",
                "Recommended answer mode: " + verdict.recommendedAnswerMode()
        ));
        addListSection(sections, "Paper Evidence", packet.paperEvidence());
        addListSection(sections, "Web Evidence", packet.webEvidence());
        addListSection(sections, "Cautions", verdict.requiredRevisions());
        return sections;
    }

    private List<DocumentDraft.Section> revisionSections(AuditVerdict verdict) {
        List<DocumentDraft.Section> sections = new ArrayList<>();
        sections.add(new DocumentDraft.Section(
                "Audit Status",
                "Verdict: " + verdict.verdict() + "\nRecommended answer mode: " + verdict.recommendedAnswerMode()
        ));
        sections.add(new DocumentDraft.Section(
                "Required Revisions",
                "Required revisions:\n" + renderList(verdict.requiredRevisions())
        ));
        return sections;
    }

    private void addListSection(List<DocumentDraft.Section> sections, String heading, List<String> items) {
        if (!items.isEmpty()) {
            sections.add(new DocumentDraft.Section(heading, renderList(items)));
        }
    }

    private String renderBody(String title, List<DocumentDraft.Section> sections) {
        StringBuilder body = new StringBuilder("# ").append(title);
        for (DocumentDraft.Section section : sections) {
            body.append("\n\n## ")
                    .append(section.heading())
                    .append("\n")
                    .append(section.body());
        }
        return body.toString();
    }

    private String renderList(List<String> items) {
        if (items.isEmpty()) {
            return "- None";
        }
        return items.stream()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- None");
    }
}
