package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.rag.RagResult;
import com.researchassistant.websearch.WebSearchHit;
import com.researchassistant.websearch.WebSearchPort;
import com.researchassistant.websearch.WebSearchResult;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiAgentSubagentTest {

    private final PaperRagService paperRagService = mock(PaperRagService.class);
    private final WebSearchPort webSearchPort = mock(WebSearchPort.class);
    private final MemoryRecallPort memoryRecallPort = mock(MemoryRecallPort.class);

    @Test
    void deepResearchAgentCallsPermittedPortsAndReturnsSourceSeparatedPacket() {
        ProjectEvidenceScope scope = new ProjectEvidenceScope(List.of(10L), Map.of(10L, "source-10"));
        when(paperRagService.retrieve(42L, "What supports multi-agent evidence review?", List.of(10L), 5))
                .thenReturn(new RagResult(
                        "What supports multi-agent evidence review?",
                        List.of(10L),
                        List.of(new RagChunk(1L, 10L, 0, "Scoped paper evidence.", 0.91))
                ));
        when(webSearchPort.search("What supports multi-agent evidence review?", 5))
                .thenReturn(new WebSearchResult(
                        "What supports multi-agent evidence review?",
                        List.of(new WebSearchHit("External corroboration", "https://example.test/research", "Web evidence.", 0.82)),
                        "test",
                        false,
                        ""
                ));
        when(memoryRecallPort.recall(42L, "What supports multi-agent evidence review?", 4))
                .thenReturn(new MemoryRecallResult(
                        "What supports multi-agent evidence review?",
                        List.of(new MemoryRecallHit(memoryEntry(), 0.77))
                ));
        DeepResearchAgent agent = new DeepResearchAgent(paperRagService, webSearchPort, memoryRecallPort);

        ResearchPacket packet = agent.research(
                42L,
                "What supports multi-agent evidence review?",
                scope,
                true
        );

        verify(paperRagService).retrieve(42L, "What supports multi-agent evidence review?", List.of(10L), 5);
        verify(webSearchPort).search("What supports multi-agent evidence review?", 5);
        verify(memoryRecallPort).recall(42L, "What supports multi-agent evidence review?", 4);
        assertThat(packet.question()).isEqualTo("What supports multi-agent evidence review?");
        assertThat(packet.paperEvidence()).containsExactly("paper: documentId=10 chunkIndex=0 score=0.91 content=Scoped paper evidence.");
        assertThat(packet.webEvidence()).containsExactly("web: title=External corroboration url=https://example.test/research score=0.82 snippet=Web evidence.");
        assertThat(packet.memoryContext()).containsExactly("memory: topic=Prior workflow summary score=0.77 summary=Keep subagents source separated.");
        assertThat(packet.claims()).isEmpty();
        assertThat(packet.conflicts()).isEmpty();
        assertThat(packet.evidenceGaps()).isEmpty();
        assertThat(packet.recommendedAnswerMode()).isEqualTo(AnswerMode.WEB_SUPPLEMENT.name());
    }

    @Test
    void deepResearchAgentSkipsPaperAndWebWhenNotPermittedButStillRecallsMemory() {
        ProjectEvidenceScope emptyScope = new ProjectEvidenceScope(List.of(), Map.of());
        when(memoryRecallPort.recall(42L, "local only?", 4))
                .thenReturn(new MemoryRecallResult("local only?", List.of()));
        DeepResearchAgent agent = new DeepResearchAgent(paperRagService, webSearchPort, memoryRecallPort);

        ResearchPacket packet = agent.research(42L, "local only?", emptyScope, false);

        verify(paperRagService, never()).retrieve(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt());
        verify(webSearchPort, never()).search(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(memoryRecallPort).recall(42L, "local only?", 4);
        assertThat(packet.paperEvidence()).isEmpty();
        assertThat(packet.webEvidence()).isEmpty();
        assertThat(packet.memoryContext()).isEmpty();
        assertThat(packet.evidenceGaps()).containsExactly("No paper or web evidence was available for the question.");
        assertThat(packet.recommendedAnswerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
    }

    @Test
    void evidenceAuditAgentFlagsUnsupportedClaimsAndRecommendsDowngradeWithoutEvidence() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("The system is fully proven."),
                List.of(),
                List.of(),
                List.of("memory is context only"),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
        EvidenceAuditAgent agent = new EvidenceAuditAgent();

        AuditVerdict verdict = agent.audit("question", "The system is fully proven.", packet);

        assertThat(verdict.isPassing()).isFalse();
        assertThat(verdict.verdict()).isEqualTo("requires_revision");
        assertThat(verdict.recommendedAnswerMode()).isEqualTo(AnswerMode.REFUSAL.name());
        assertThat(verdict.unsupportedClaims()).containsExactly("The system is fully proven.");
        assertThat(verdict.requiredRevisions())
                .contains("Remove or qualify claims until paper or web evidence supports them.");
    }

    @Test
    void evidenceAuditAgentPassesWithCautionsWhenEvidenceGapsRemain() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of(),
                List.of("paper evidence"),
                List.of(),
                List.of(),
                List.of(),
                List.of("Missing recent replication evidence."),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
        EvidenceAuditAgent agent = new EvidenceAuditAgent();

        AuditVerdict verdict = agent.audit("question", "", packet);

        assertThat(verdict.isPassing()).isTrue();
        assertThat(verdict.verdict()).isEqualTo("pass_with_cautions");
        assertThat(verdict.recommendedAnswerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        assertThat(verdict.requiredRevisions()).containsExactly("Address evidence gap: Missing recent replication evidence.");
    }

    @Test
    void evidenceAuditAgentFlagsUnsupportedClaimEvenWhenUnrelatedPaperEvidenceExists() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("Claim A is supported."),
                List.of("paper: content=This evidence supports a different claim."),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
        EvidenceAuditAgent agent = new EvidenceAuditAgent();

        AuditVerdict verdict = agent.audit("question", "Claim A is supported.", packet);

        assertThat(verdict.isPassing()).isFalse();
        assertThat(verdict.verdict()).isEqualTo("requires_revision");
        assertThat(verdict.recommendedAnswerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        assertThat(verdict.unsupportedClaims()).containsExactly("Claim A is supported.");
        assertThat(verdict.requiredRevisions()).containsExactly("Support or revise unsupported claim: Claim A is supported.");
    }

    @Test
    void evidenceAuditAgentPassesClaimWhenExactTextAppearsInEvidence() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("Claim A is supported."),
                List.of("paper: content=Claim A is supported."),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
        EvidenceAuditAgent agent = new EvidenceAuditAgent();

        AuditVerdict verdict = agent.audit("question", "Claim A is supported.", packet);

        assertThat(verdict.isPassing()).isTrue();
        assertThat(verdict.verdict()).isEqualTo("pass");
        assertThat(verdict.unsupportedClaims()).isEmpty();
        assertThat(verdict.requiredRevisions()).isEmpty();
    }

    @Test
    void documentComposerPassingOutputDoesNotPresentMemoryContextAsEvidenceOrSection() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of(),
                List.of("paper: content=Supported evidence."),
                List.of(),
                List.of("memory: background only"),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass",
                AnswerMode.LOCAL_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        );
        DocumentComposerAgent agent = new DocumentComposerAgent();

        DocumentDraft draft = agent.compose("markdown", "Passing result", packet, verdict);

        assertThat(draft.sections()).extracting(DocumentDraft.Section::heading)
                .doesNotContain("Memory Context", "Memory Context (background only)");
        assertThat(draft.body()).doesNotContain("memory: background only");
    }

    @Test
    void documentComposerGeneratesRevisionDraftWhenVerdictDoesNotPass() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of("Sensitive unsupported claim"),
                List.of(),
                List.of(),
                List.of("memory context"),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
        AuditVerdict verdict = new AuditVerdict(
                "requires_revision",
                AnswerMode.REFUSAL.name(),
                List.of("Sensitive unsupported claim"),
                List.of(),
                List.of("Remove unsupported claim before composing conclusions.")
        );
        DocumentComposerAgent agent = new DocumentComposerAgent();

        DocumentDraft draft = agent.compose("markdown", "Audit result", packet, verdict);

        assertThat(draft.format()).isEqualTo("markdown");
        assertThat(draft.title()).isEqualTo("Audit result");
        assertThat(draft.body()).contains("Required revisions");
        assertThat(draft.body()).contains("Remove unsupported claim before composing conclusions.");
        assertThat(draft.body()).doesNotContain("Sensitive unsupported claim");
        assertThat(draft.sections()).extracting(DocumentDraft.Section::heading)
                .containsExactly("Audit Status", "Required Revisions");
    }

    @Test
    void documentComposerConstructorDoesNotRequireRetrievalPorts() {
        List<Class<?>> constructorParameterTypes = Arrays.stream(DocumentComposerAgent.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .toList();

        assertThat(constructorParameterTypes)
                .doesNotContain(PaperRagService.class, WebSearchPort.class, MemoryRecallPort.class);
        assertThat(new DocumentComposerAgent()).isNotNull();
    }

    private MemoryEntry memoryEntry() {
        return new MemoryEntry(
                7L,
                42L,
                "COMPACTION",
                "Prior workflow summary",
                "Keep subagents source separated.",
                List.of(),
                List.of(),
                List.of("multi-agent"),
                1L,
                2L,
                OffsetDateTime.parse("2026-05-13T00:00:00Z"),
                OffsetDateTime.parse("2026-05-13T00:00:00Z")
        );
    }
}
