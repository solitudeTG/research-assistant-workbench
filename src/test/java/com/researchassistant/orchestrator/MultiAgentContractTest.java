package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiAgentContractTest {

    @Test
    void researchPacketEmptyWithEvidenceGapPreservesSourceSeparation() {
        ResearchPacket packet = ResearchPacket.empty("question")
                .withEvidenceGap("No local paper evidence supports the claim.");

        assertThat(packet.paperEvidence()).isEmpty();
        assertThat(packet.webEvidence()).isEmpty();
        assertThat(packet.memoryContext()).isEmpty();
        assertThat(packet.evidenceGaps()).containsExactly("No local paper evidence supports the claim.");
        assertThat(packet.recommendedAnswerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
    }

    @Test
    void researchPacketDefensivelyCopiesCallerLists() {
        List<String> claims = new ArrayList<>(List.of("claim"));
        List<String> paperEvidence = new ArrayList<>(List.of("paper"));
        List<String> webEvidence = new ArrayList<>(List.of("web"));
        List<String> memoryContext = new ArrayList<>(List.of("memory"));
        List<String> conflicts = new ArrayList<>(List.of("conflict"));
        List<String> evidenceGaps = new ArrayList<>(List.of("gap"));

        ResearchPacket packet = new ResearchPacket(
                "question",
                claims,
                paperEvidence,
                webEvidence,
                memoryContext,
                conflicts,
                evidenceGaps,
                AnswerMode.LOCAL_EVIDENCE.name()
        );

        claims.add("mutated claim");
        paperEvidence.add("mutated paper");
        webEvidence.add("mutated web");
        memoryContext.add("mutated memory");
        conflicts.add("mutated conflict");
        evidenceGaps.add("mutated gap");

        assertThat(packet.claims()).containsExactly("claim");
        assertThat(packet.paperEvidence()).containsExactly("paper");
        assertThat(packet.webEvidence()).containsExactly("web");
        assertThat(packet.memoryContext()).containsExactly("memory");
        assertThat(packet.conflicts()).containsExactly("conflict");
        assertThat(packet.evidenceGaps()).containsExactly("gap");
    }

    @Test
    void researchPacketAccessorListsAreUnmodifiable() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("claim"),
                List.of("paper"),
                List.of("web"),
                List.of("memory"),
                List.of("conflict"),
                List.of("gap"),
                AnswerMode.LOCAL_EVIDENCE.name()
        );

        assertThatThrownBy(() -> packet.claims().add("mutated")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packet.paperEvidence().add("mutated")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packet.webEvidence().add("mutated")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packet.memoryContext().add("mutated")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packet.conflicts().add("mutated")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packet.evidenceGaps().add("mutated")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void researchPacketWithEvidenceReplacesOnlyEvidenceListsAndPreservesContext() {
        ResearchPacket packet = new ResearchPacket(
                "satellite interference question",
                List.of("claim"),
                List.of("raw paper"),
                List.of("raw web"),
                List.of("memory"),
                List.of("conflict"),
                List.of("old gap"),
                AnswerMode.WEB_SUPPLEMENT.name()
        );

        ResearchPacket curated = packet.withEvidence(
                List.of("curated paper"),
                List.of(),
                List.of("old gap", "No gate-accepted evidence survived curation.")
        );

        assertThat(curated.question()).isEqualTo("satellite interference question");
        assertThat(curated.claims()).containsExactly("claim");
        assertThat(curated.paperEvidence()).containsExactly("curated paper");
        assertThat(curated.webEvidence()).isEmpty();
        assertThat(curated.memoryContext()).containsExactly("memory");
        assertThat(curated.conflicts()).containsExactly("conflict");
        assertThat(curated.evidenceGaps())
                .containsExactly("old gap", "No gate-accepted evidence survived curation.");
        assertThat(curated.recommendedAnswerMode()).isEqualTo(AnswerMode.WEB_SUPPLEMENT.name());
    }

    @Test
    void curatedEvidenceSetSeparatesAcceptedAndRejectedSourceTexts() {
        CuratedEvidenceSet set = new CuratedEvidenceSet(List.of(
                new CuratedEvidenceItem("paper", "LEO satellite interference evidence.", true, "", List.of("satellite")),
                new CuratedEvidenceItem("web", "No information is available for this page.", false, "EMPTY_OR_NAVIGATION_PAGE", List.of())
        ));

        assertThat(set.acceptedCount()).isEqualTo(1);
        assertThat(set.rejectedCount()).isEqualTo(1);
        assertThat(set.acceptedPaperEvidence()).containsExactly("LEO satellite interference evidence.");
        assertThat(set.acceptedWebEvidence()).isEmpty();
        assertThat(set.rejectedItems())
                .extracting(CuratedEvidenceItem::rejectReason)
                .containsExactly("EMPTY_OR_NAVIGATION_PAGE");
    }

    @Test
    void auditVerdictDefensivelyCopiesListsAndClassifiesPassingVerdicts() {
        List<String> unsupportedClaims = new ArrayList<>(List.of("unsupported"));
        List<String> sourcePolicyIssues = new ArrayList<>(List.of("policy"));
        List<String> requiredRevisions = new ArrayList<>(List.of("revision"));

        AuditVerdict passWithCautions = new AuditVerdict(
                "pass_with_cautions",
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                unsupportedClaims,
                sourcePolicyIssues,
                requiredRevisions
        );
        AuditVerdict fail = new AuditVerdict("fail", AnswerMode.REFUSAL.name(), List.of(), List.of(), List.of());
        AuditVerdict requiresRevision = new AuditVerdict(
                "requires_revision",
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        );

        unsupportedClaims.add("mutated unsupported");
        sourcePolicyIssues.add("mutated policy");
        requiredRevisions.add("mutated revision");

        assertThat(passWithCautions.isPassing()).isTrue();
        assertThat(fail.isPassing()).isFalse();
        assertThat(requiresRevision.isPassing()).isFalse();
        assertThat(passWithCautions.unsupportedClaims()).containsExactly("unsupported");
        assertThat(passWithCautions.sourcePolicyIssues()).containsExactly("policy");
        assertThat(passWithCautions.requiredRevisions()).containsExactly("revision");
    }

    @Test
    void auditVerdictRejectsNullVerdict() {
        assertThatThrownBy(() -> new AuditVerdict(
                null,
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("verdict");
    }

    @Test
    void auditVerdictAccessorListsAreUnmodifiable() {
        AuditVerdict verdict = new AuditVerdict(
                "pass",
                AnswerMode.LOCAL_EVIDENCE.name(),
                List.of("unsupported"),
                List.of("policy"),
                List.of("revision")
        );

        assertThatThrownBy(() -> verdict.unsupportedClaims().add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> verdict.sourcePolicyIssues().add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> verdict.requiredRevisions().add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void documentDraftDefensivelyCopiesSections() {
        List<DocumentDraft.Section> sections = new ArrayList<>(List.of(
                new DocumentDraft.Section("Summary", "Body")
        ));

        DocumentDraft draft = new DocumentDraft("markdown", "Title", "Full body", sections);

        sections.add(new DocumentDraft.Section("Mutated", "Mutated body"));

        assertThat(draft.sections()).containsExactly(new DocumentDraft.Section("Summary", "Body"));
    }

    @Test
    void documentDraftAccessorSectionsAreUnmodifiable() {
        DocumentDraft draft = new DocumentDraft(
                "markdown",
                "Title",
                "Full body",
                List.of(new DocumentDraft.Section("Summary", "Body"))
        );

        assertThatThrownBy(() -> draft.sections().add(new DocumentDraft.Section("Mutated", "Mutated body")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
