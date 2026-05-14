package com.researchassistant.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        String normalizedTitle = reportTitle(title, packet.question());

        List<DocumentDraft.Section> sections = verdict.isPassing()
                ? passingSections(packet, verdict)
                : revisionSections(verdict);
        String body = renderBody(normalizedTitle, sections);
        return new DocumentDraft(format, normalizedTitle, body, sections);
    }

    private List<DocumentDraft.Section> passingSections(ResearchPacket packet, AuditVerdict verdict) {
        List<DocumentDraft.Section> sections = new ArrayList<>();
        sections.add(new DocumentDraft.Section(
                "结论摘要",
                conclusionSummary(packet, verdict)
        ));
        sections.add(new DocumentDraft.Section(
                "主要证据",
                primaryEvidence(packet)
        ));
        sections.add(new DocumentDraft.Section(
                "证据不足",
                evidenceLimits(packet, verdict)
        ));
        sections.add(new DocumentDraft.Section(
                "当前判断",
                currentJudgment(verdict)
        ));
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

    private String conclusionSummary(ResearchPacket packet, AuditVerdict verdict) {
        if (!packet.claims().isEmpty()) {
            return renderList(packet.claims());
        }
        if (verdict.sourcePolicyIssues().isEmpty() && verdict.requiredRevisions().isEmpty()) {
            return "当前材料通过审计，但仍应按可用证据强度谨慎使用结论。";
        }
        return "当前材料只支持谨慎结论；报告保留证据边界和后续补强要求。";
    }

    private String primaryEvidence(ResearchPacket packet) {
        List<String> evidence = new ArrayList<>();
        packet.paperEvidence().stream()
                .map(this::userFacingEvidence)
                .filter(value -> !value.isBlank())
                .filter(this::isSubstantiveEvidence)
                .filter(this::hasReportEvidenceShape)
                .limit(5)
                .forEach(evidence::add);
        packet.webEvidence().stream()
                .map(this::userFacingEvidence)
                .filter(value -> !value.isBlank())
                .filter(this::isSubstantiveEvidence)
                .filter(this::hasReportEvidenceShape)
                .limit(3)
                .forEach(evidence::add);
        return evidence.isEmpty()
                ? "暂无可用于报告正文的论文或联网证据。"
                : renderList(evidence);
    }

    private String evidenceLimits(ResearchPacket packet, AuditVerdict verdict) {
        List<String> limits = new ArrayList<>();
        limits.addAll(packet.evidenceGaps());
        limits.addAll(verdict.sourcePolicyIssues());
        limits.addAll(verdict.requiredRevisions());
        return limits.isEmpty()
                ? "本轮审计未记录额外证据缺口。"
                : renderList(limits);
    }

    private String currentJudgment(AuditVerdict verdict) {
        if (verdict.requiredRevisions().isEmpty() && verdict.sourcePolicyIssues().isEmpty()) {
            return "可以作为当前研究阶段的审计后报告使用；后续如需强引用结论，应补充结构化 citation 证据。";
        }
        return "可以作为弱证据研究报告使用；在补齐证据缺口和来源限制前，不应把它当作强引用结论。";
    }

    private String userFacingEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "";
        }
        for (String marker : List.of("content=", "snippet=", "summary=")) {
            int index = evidence.indexOf(marker);
            if (index >= 0) {
                return cleanEvidenceText(evidence.substring(index + marker.length()));
            }
        }
        return cleanEvidenceText(evidence);
    }

    private String reportTitle(String title, String question) {
        String candidate = title == null || title.isBlank() ? question : title.trim();
        String source = (candidate + "\n" + Objects.toString(question, "")).toLowerCase();
        if (source.contains("近邻星干涉")) {
            return "近邻星干涉研究路线评估报告";
        }
        return candidate == null || candidate.isBlank() ? "研究报告" : candidate;
    }

    private boolean isSubstantiveEvidence(String evidence) {
        String normalized = evidence.toLowerCase(Locale.ROOT);
        return List.of(
                "supported by",
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
        ).stream().noneMatch(normalized::contains);
    }

    private String cleanEvidenceText(String evidence) {
        String cleaned = evidence == null ? "" : evidence.trim();
        cleaned = trimBeforeReferences(cleaned);
        cleaned = extractAbstract(cleaned);
        return cleaned
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .replace("Abstract—", "")
                .replace("Abstract-", "")
                .trim();
    }

    private String trimBeforeReferences(String evidence) {
        String normalized = evidence.toLowerCase(Locale.ROOT);
        int referencesIndex = normalized.indexOf("references");
        if (referencesIndex < 0) {
            return evidence;
        }
        return evidence.substring(0, referencesIndex).trim();
    }

    private String extractAbstract(String evidence) {
        String normalized = evidence.toLowerCase(Locale.ROOT);
        int abstractIndex = normalized.indexOf("abstract");
        if (abstractIndex < 0) {
            return evidence;
        }
        String abstractText = evidence.substring(abstractIndex);
        int dashIndex = Math.max(abstractText.indexOf('—'), abstractText.indexOf('-'));
        return dashIndex >= 0 ? abstractText.substring(dashIndex + 1).trim() : abstractText.trim();
    }

    private boolean hasReportEvidenceShape(String evidence) {
        if (evidence.length() < 10) {
            return false;
        }
        char first = evidence.charAt(0);
        if (Character.isLowerCase(first)) {
            return false;
        }
        String normalized = evidence.toLowerCase(Locale.ROOT);
        return !normalized.contains("references")
                && !normalized.matches(".*\\[[0-9]+].*")
                && !normalized.contains("ieee transactions");
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
