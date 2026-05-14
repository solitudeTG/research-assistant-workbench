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
    void evidenceAuditAgentFlagsUnsupportedDraftAnswerWhenNoClaimsAndUnrelatedEvidenceExists() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of(),
                List.of("paper: content=Unrelated method note."),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_EVIDENCE.name()
        );
        EvidenceAuditAgent agent = new EvidenceAuditAgent();

        AuditVerdict verdict = agent.audit("question", "The system is fully proven.", packet);

        assertThat(verdict.isPassing()).isFalse();
        assertThat(verdict.verdict()).isEqualTo("requires_revision");
        assertThat(verdict.recommendedAnswerMode()).isEqualTo(AnswerMode.LOCAL_WEAK_EVIDENCE.name());
        assertThat(verdict.unsupportedClaims()).isEmpty();
        assertThat(verdict.sourcePolicyIssues()).containsExactly("Draft answer is not directly supported by paper or web evidence.");
        assertThat(verdict.requiredRevisions()).containsExactly("Support or revise unsupported draft answer.");
    }

    @Test
    void evidenceAuditAgentPassesDraftAnswerWhenExactTextAppearsInEvidence() {
        ResearchPacket packet = new ResearchPacket(
                "question",
                List.of(),
                List.of(),
                List.of("web: snippet=The system is fully proven."),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.WEB_SUPPLEMENT.name()
        );
        EvidenceAuditAgent agent = new EvidenceAuditAgent();

        AuditVerdict verdict = agent.audit("question", "The system is fully proven.", packet);

        assertThat(verdict.isPassing()).isTrue();
        assertThat(verdict.verdict()).isEqualTo("pass");
        assertThat(verdict.requiredRevisions()).isEmpty();
        assertThat(verdict.sourcePolicyIssues()).isEmpty();
    }

    @Test
    void evidenceCuratorRejectsEmptyPagesAndOffTopicWebForSatelliteInterferenceQuestion() {
        ResearchPacket packet = new ResearchPacket(
                "Assess LEO satellite interference and beamforming research route.",
                List.of(),
                List.of("paper: content=Simulation results show adaptive beamforming reduces inter-satellite interference in LEO constellations."),
                List.of(
                        "web: title=Search result snippet=No information is available for this page. Learn why",
                        "web: title=Ukraine update snippet=The latest news discussed Russia, Trump, and European diplomatic pressure."
                ),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.WEB_SUPPLEMENT.name()
        );
        EvidenceCurator curator = new EvidenceCurator();

        CuratedEvidenceSet curated = curator.curate(packet.question(), packet);

        assertThat(curated.acceptedPaperEvidence())
                .containsExactly("paper: content=Simulation results show adaptive beamforming reduces inter-satellite interference in LEO constellations.");
        assertThat(curated.acceptedWebEvidence()).isEmpty();
        assertThat(curated.rejectedItems())
                .extracting(CuratedEvidenceItem::rejectReason)
                .containsExactly("EMPTY_OR_NAVIGATION_PAGE", "OFF_TOPIC");
    }

    @Test
    void evidenceCuratorRetainsRelevantSatelliteWebSupplement() {
        ResearchPacket packet = new ResearchPacket(
                "Assess LEO satellite interference and beamforming research route.",
                List.of(),
                List.of(),
                List.of("web: title=LEO beamforming snippet=Satellite beamforming can mitigate inter-satellite interference in dense LEO communication networks."),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.WEB_SUPPLEMENT.name()
        );
        EvidenceCurator curator = new EvidenceCurator();

        CuratedEvidenceSet curated = curator.curate(packet.question(), packet);

        assertThat(curated.acceptedWebEvidence())
                .containsExactly("web: title=LEO beamforming snippet=Satellite beamforming can mitigate inter-satellite interference in dense LEO communication networks.");
        assertThat(curated.rejectedItems()).isEmpty();
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
    void documentComposerPassingOutputRendersUserFacingReportWithoutInternalPacketFields() {
        ResearchPacket packet = new ResearchPacket(
                "请输出一份 markdown 研究报告",
                List.of("当前证据只能支持谨慎结论。"),
                List.of("paper: documentId=2 chunkIndex=2 score=0.31 content=论文证据显示该路线仍需要更多仿真验证。"),
                List.of("web: title=TowardsDataScience url=https://example.test score=0.02 snippet=外部资料只适合作为背景补充。"),
                List.of("memory: background only"),
                List.of(),
                List.of("缺少直接实验或高质量本地论文证据。"),
                AnswerMode.WEB_SUPPLEMENT.name()
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass_with_cautions",
                AnswerMode.WEB_SUPPLEMENT.name(),
                List.of(),
                List.of("联网资料只能作为补充。"),
                List.of("补充更强的本地论文证据后再给强结论。")
        );
        DocumentComposerAgent agent = new DocumentComposerAgent();

        DocumentDraft draft = agent.compose("markdown", "近邻星干涉研究路线评估报告", packet, verdict);

        assertThat(draft.sections()).extracting(DocumentDraft.Section::heading)
                .containsExactly("结论摘要", "主要证据", "证据不足", "当前判断");
        assertThat(draft.body())
                .contains("# 近邻星干涉研究路线评估报告")
                .contains("论文证据显示该路线仍需要更多仿真验证。")
                .contains("外部资料只适合作为背景补充。")
                .contains("缺少直接实验或高质量本地论文证据。")
                .contains("联网资料只能作为补充。")
                .doesNotContain("Answer Mode")
                .doesNotContain("Recommended answer mode")
                .doesNotContain("documentId=")
                .doesNotContain("chunkIndex=")
                .doesNotContain("score=")
                .doesNotContain("content=")
                .doesNotContain("snippet=")
                .doesNotContain("url=")
                .doesNotContain("memory: background only");
    }

    @Test
    void documentComposerUsesDemoFriendlyTitleAndFiltersAdministrativeEvidence() {
        ResearchPacket packet = new ResearchPacket(
                "请对近邻星干涉相关论文做系统分析和对比，判断当前研究路线是否成立，并输出一份 markdown 研究报告，要求说明证据不足之处。",
                List.of(),
                List.of(
                        "paper: documentId=2 chunkIndex=2 score=0.31 content=This work was supported by MSIT grants and the corresponding author is Namyoon Lee.",
                        "paper: documentId=3 chunkIndex=4 score=0.42 content=Simulation results show that adaptive beamforming reduces inter-satellite interference under high mobility.",
                        "paper: documentId=4 chunkIndex=1 score=0.35 content=The proposed interference coordination method improves link robustness in dense LEO constellations."
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of("缺少直接面向近邻星干涉路线的端到端实验。"),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass_with_cautions",
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        );
        DocumentComposerAgent agent = new DocumentComposerAgent();

        DocumentDraft draft = agent.compose("markdown", packet.question(), packet, verdict);

        assertThat(draft.title()).isEqualTo("近邻星干涉研究路线评估报告");
        assertThat(draft.body())
                .contains("# 近邻星干涉研究路线评估报告")
                .contains("adaptive beamforming reduces inter-satellite interference")
                .contains("interference coordination method improves link robustness")
                .contains("缺少直接面向近邻星干涉路线的端到端实验")
                .doesNotContain("MSIT grants")
                .doesNotContain("corresponding author")
                .doesNotContain("Namyoon Lee");
    }

    @Test
    void documentComposerExtractsAbstractEvidenceAndSkipsReferenceFragments() {
        ResearchPacket packet = new ResearchPacket(
                "请输出一份 markdown 研究报告",
                List.of(),
                List.of(
                        "paper: content=9500 IEEE TRANSACTIONS ON WIRELESS COMMUNICATIONS, VOL. 25, 2026\n"
                                + "Beamforming Design and Satellite Selection for Realizing Integrated Communication and Navigation in LEO Satellite Networks\n"
                                + "Abstract—Relying on powerful communication capabilities and rapidly changing geometric configurations, LEO satellites can support integrated communication and navigation services.\n"
                                + "REFERENCES\n[1] J. Yim, J. Park, and N. Lee, Space-time beamforming.",
                        "paper: content=throughput, demonstrating the technique's adaptability and effectiveness for next-generation satellite communications.\n"
                                + "REFERENCES\n[2] S. Cioni, On the satellite role in the era of 5G.",
                        "paper: content=Simulation results show that adaptive beamforming reduces inter-satellite interference under high mobility."
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                AnswerMode.LOCAL_WEAK_EVIDENCE.name()
        );
        AuditVerdict verdict = new AuditVerdict(
                "pass_with_cautions",
                AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                List.of(),
                List.of(),
                List.of()
        );
        DocumentComposerAgent agent = new DocumentComposerAgent();

        DocumentDraft draft = agent.compose("markdown", "近邻星干涉研究路线评估报告", packet, verdict);

        assertThat(draft.body())
                .contains("Relying on powerful communication capabilities")
                .contains("adaptive beamforming reduces inter-satellite interference")
                .doesNotContain("9500 IEEE TRANSACTIONS")
                .doesNotContain("REFERENCES")
                .doesNotContain("throughput, demonstrating")
                .doesNotContain("J. Yim")
                .doesNotContain("S. Cioni");
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
