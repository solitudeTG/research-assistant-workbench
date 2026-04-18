package com.researchassistant.orchestrator.support;

import com.researchassistant.ingest.DocumentAnalysisService;
import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.model.DocumentAnalysis;
import com.researchassistant.ingest.model.ResearchDocument;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DocumentMetadataService {

    private final DocumentRepository documentRepository;
    private final DocumentAnalysisService documentAnalysisService;

    public DocumentMetadataService(
            DocumentRepository documentRepository,
            DocumentAnalysisService documentAnalysisService) {
        this.documentRepository = documentRepository;
        this.documentAnalysisService = documentAnalysisService;
    }

    public boolean isTitleQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("title")
                || normalized.contains("题目")
                || normalized.contains("标题")
                || normalized.contains("论文名");
    }

    public boolean isOverviewQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("what is this paper about")
                || normalized.contains("what does this paper study")
                || normalized.contains("what problem does this paper study")
                || normalized.contains("abstract")
                || normalized.contains("summary")
                || normalized.contains("研究了什么")
                || normalized.contains("主要研究")
                || normalized.contains("讲了什么")
                || normalized.contains("关于什么")
                || normalized.contains("摘要")
                || normalized.contains("总结")
                || normalized.contains("概述")
                || normalized.contains("主要内容");
    }

    public ResearchDocument findPrimaryDocument(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }
        return documentRepository.findById(documentIds.get(0)).orElse(null);
    }

    public String answerTitleQuestion(ResearchDocument document) {
        if (document == null || document.title() == null || document.title().isBlank()) {
            return "当前索引元数据里还没有可用的论文题目。";
        }
        return "论文题目是：" + document.title();
    }

    public Optional<String> answerOverviewQuestion(ResearchDocument document) {
        if (document == null) {
            return Optional.empty();
        }

        Optional<DocumentAnalysis> analysis = documentAnalysisService.findByDocumentId(document.id());
        if (analysis.isEmpty()) {
            return Optional.empty();
        }

        DocumentAnalysis value = analysis.get();
        if (value.summary() != null && !value.summary().isBlank()) {
            return Optional.of(value.summary().trim());
        }
        if (value.abstractText() != null && !value.abstractText().isBlank()) {
            return Optional.of(value.abstractText().trim());
        }
        return Optional.empty();
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT);
    }
}
