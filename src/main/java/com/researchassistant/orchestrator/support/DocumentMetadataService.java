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
        return containsAny(normalized, "title", "题目", "标题", "论文名", "论文标题");
    }

    public boolean isOverviewQuestion(String question) {
        String normalized = normalize(question);
        return containsAny(
                normalized,
                "what is this paper about",
                "what does this paper study",
                "what problem does this paper study",
                "abstract",
                "summary",
                "研究了什么",
                "主要研究",
                "讲了什么",
                "关于什么",
                "摘要",
                "总结",
                "概述",
                "主要内容");
    }

    public boolean isMethodQuestion(String question) {
        String normalized = normalize(question);
        return containsAny(
                normalized,
                "method",
                "methods",
                "approach",
                "framework",
                "algorithm",
                "pipeline",
                "方法",
                "怎么做",
                "用了什么",
                "模型",
                "算法",
                "技术路线");
    }

    public boolean isContributionQuestion(String question) {
        String normalized = normalize(question);
        return containsAny(
                normalized,
                "contribution",
                "contributions",
                "novelty",
                "main contribution",
                "贡献",
                "创新点",
                "亮点",
                "贡献点");
    }

    public ResearchDocument findPrimaryDocument(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }
        return documentRepository.findById(documentIds.get(0)).orElse(null);
    }

    public String answerTitleQuestion(ResearchDocument document) {
        if (document == null || document.title() == null || document.title().isBlank()) {
            return "当前索引元数据里还没有可用的论文标题。";
        }
        return "论文题目是：" + document.title();
    }

    public Optional<String> answerOverviewQuestion(ResearchDocument document) {
        return findAnalysis(document)
                .flatMap(analysis -> firstNonBlank(analysis.summary(), analysis.abstractText()));
    }

    public Optional<String> answerMethodQuestion(ResearchDocument document) {
        return findAnalysis(document).flatMap(analysis -> {
            List<String> methods = sanitizeItems(analysis.methods());
            if (!methods.isEmpty()) {
                return Optional.of(formatListAnswer("这篇论文的方法主要包括：", methods));
            }
            return firstNonBlank(analysis.abstractText())
                    .map(value -> "从摘要来看，这篇论文的方法重点是：" + value);
        });
    }

    public Optional<String> answerContributionQuestion(ResearchDocument document) {
        return findAnalysis(document).flatMap(analysis -> {
            List<String> contributions = sanitizeItems(analysis.contributions());
            if (!contributions.isEmpty()) {
                return Optional.of(formatListAnswer("这篇论文的主要贡献包括：", contributions));
            }
            return firstNonBlank(analysis.summary(), analysis.abstractText())
                    .map(value -> "从结构化分析来看，这篇论文的贡献重点是：" + value);
        });
    }

    private Optional<DocumentAnalysis> findAnalysis(ResearchDocument document) {
        if (document == null) {
            return Optional.empty();
        }
        return documentAnalysisService.findByDocumentId(document.id());
    }

    private boolean containsAny(String normalized, String... patterns) {
        for (String pattern : patterns) {
            if (normalized.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(trimForAnswer(value));
            }
        }
        return Optional.empty();
    }

    private List<String> sanitizeItems(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(this::trimForAnswer)
                .distinct()
                .limit(3)
                .toList();
    }

    private String formatListAnswer(String prefix, List<String> items) {
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = 0; i < items.size(); i++) {
            builder.append(System.lineSeparator())
                    .append(i + 1)
                    .append(". ")
                    .append(items.get(i));
        }
        return builder.toString();
    }

    private String trimForAnswer(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 600) {
            return normalized;
        }
        return normalized.substring(0, 597).trim() + "...";
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT);
    }
}
