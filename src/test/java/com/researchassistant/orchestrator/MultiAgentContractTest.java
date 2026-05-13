package com.researchassistant.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAgentContractTest {

    @Test
    void researchPacketEmptyWithEvidenceGapPreservesSourceSeparation() {
        ResearchPacket packet = ResearchPacket.empty("question")
                .withEvidenceGap("No local paper evidence supports the claim.");

        assertThat(packet.paperEvidence()).isEmpty();
        assertThat(packet.webEvidence()).isEmpty();
        assertThat(packet.memoryContext()).isEmpty();
        assertThat(packet.evidenceGaps()).containsExactly("No local paper evidence supports the claim.");
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
                "grounded"
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
    void auditVerdictDefensivelyCopiesListsAndClassifiesPassingVerdicts() {
        List<String> unsupportedClaims = new ArrayList<>(List.of("unsupported"));
        List<String> sourcePolicyIssues = new ArrayList<>(List.of("policy"));
        List<String> requiredRevisions = new ArrayList<>(List.of("revision"));

        AuditVerdict passWithCautions = new AuditVerdict(
                "pass_with_cautions",
                "grounded",
                unsupportedClaims,
                sourcePolicyIssues,
                requiredRevisions
        );
        AuditVerdict fail = new AuditVerdict("fail", "refuse", List.of(), List.of(), List.of());
        AuditVerdict requiresRevision = new AuditVerdict("requires_revision", "revise", List.of(), List.of(), List.of());

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
    void documentDraftDefensivelyCopiesSections() {
        List<DocumentDraft.Section> sections = new ArrayList<>(List.of(
                new DocumentDraft.Section("Summary", "Body")
        ));

        DocumentDraft draft = new DocumentDraft("markdown", "Title", "Full body", sections);

        sections.add(new DocumentDraft.Section("Mutated", "Mutated body"));

        assertThat(draft.sections()).containsExactly(new DocumentDraft.Section("Summary", "Body"));
    }
}
