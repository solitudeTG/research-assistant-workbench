package com.researchassistant.orchestrator.support;

import com.researchassistant.ingest.DocumentAnalysisService;
import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.model.DocumentAnalysis;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.ResearchDocument;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentMetadataServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentAnalysisService documentAnalysisService = mock(DocumentAnalysisService.class);
    private final DocumentMetadataService documentMetadataService =
            new DocumentMetadataService(documentRepository, documentAnalysisService);

    @Test
    void treatsAbstractQuestionsAsOverviewQuestions() {
        assertThat(documentMetadataService.isOverviewQuestion("摘要的内容是什么?"))
                .isTrue();
    }

    @Test
    void answerOverviewQuestionPrefersStructuredSummary() {
        ResearchDocument document = indexedDocument(1L, "satellite-selection.pdf");
        DocumentAnalysis analysis = new DocumentAnalysis(
                1L,
                "Abstract text",
                "这篇论文主要研究低轨卫星网络中的联合波束成形与卫星选择。",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(documentAnalysisService.findByDocumentId(1L)).thenReturn(Optional.of(analysis));

        Optional<String> overview = documentMetadataService.answerOverviewQuestion(document);

        assertThat(overview).contains("这篇论文主要研究低轨卫星网络中的联合波束成形与卫星选择。");
    }

    @Test
    void answerOverviewQuestionFallsBackToAbstractWhenSummaryIsMissing() {
        ResearchDocument document = indexedDocument(2L, "satellite-selection.pdf");
        DocumentAnalysis analysis = new DocumentAnalysis(
                2L,
                "This paper studies joint beamforming and satellite selection in LEO satellite networks.",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(documentAnalysisService.findByDocumentId(2L)).thenReturn(Optional.of(analysis));

        Optional<String> overview = documentMetadataService.answerOverviewQuestion(document);

        assertThat(overview).isPresent();
        assertThat(overview.orElseThrow()).contains("joint beamforming");
    }

    @Test
    void answerOverviewQuestionReturnsEmptyWhenNoStructuredAnalysisExists() {
        ResearchDocument document = indexedDocument(3L, "satellite-selection.pdf");
        when(documentAnalysisService.findByDocumentId(3L)).thenReturn(Optional.empty());

        Optional<String> overview = documentMetadataService.answerOverviewQuestion(document);

        assertThat(overview).isEmpty();
    }

    @Test
    void treatsMethodQuestionsAsMethodQuestions() {
        assertThat(documentMetadataService.isMethodQuestion("这篇论文的方法是什么？"))
                .isTrue();
    }

    @Test
    void answerMethodQuestionFormatsStructuredMethods() {
        ResearchDocument document = indexedDocument(4L, "satellite-selection.pdf");
        DocumentAnalysis analysis = new DocumentAnalysis(
                4L,
                "Abstract text",
                "Summary text",
                List.of(
                        "The paper proposes a joint beamforming optimization module.",
                        "It combines satellite selection with a deep learning scorer."
                ),
                List.of(),
                List.of(),
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(documentAnalysisService.findByDocumentId(4L)).thenReturn(Optional.of(analysis));

        Optional<String> answer = documentMetadataService.answerMethodQuestion(document);

        assertThat(answer).isPresent();
        assertThat(answer.orElseThrow()).contains("方法");
        assertThat(answer.orElseThrow()).contains("joint beamforming");
        assertThat(answer.orElseThrow()).contains("deep learning scorer");
    }

    @Test
    void treatsContributionQuestionsAsContributionQuestions() {
        assertThat(documentMetadataService.isContributionQuestion("这篇论文的主要贡献是什么？"))
                .isTrue();
    }

    @Test
    void answerContributionQuestionFormatsStructuredContributions() {
        ResearchDocument document = indexedDocument(5L, "satellite-selection.pdf");
        DocumentAnalysis analysis = new DocumentAnalysis(
                5L,
                "Abstract text",
                "Summary text",
                List.of(),
                List.of(
                        "It introduces a deep learning based satellite selector.",
                        "It reports stronger positioning performance in LEO networks."
                ),
                List.of(),
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(documentAnalysisService.findByDocumentId(5L)).thenReturn(Optional.of(analysis));

        Optional<String> answer = documentMetadataService.answerContributionQuestion(document);

        assertThat(answer).isPresent();
        assertThat(answer.orElseThrow()).contains("贡献");
        assertThat(answer.orElseThrow()).contains("satellite selector");
        assertThat(answer.orElseThrow()).contains("positioning performance");
    }

    private ResearchDocument indexedDocument(long documentId, String title) {
        return new ResearchDocument(
                documentId,
                title,
                title,
                "storage/" + title,
                DocumentStatus.INDEXED,
                null,
                null,
                10,
                1200,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
