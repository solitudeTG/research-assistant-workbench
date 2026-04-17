package com.researchassistant.orchestrator.support;

import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.model.ResearchDocument;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DocumentMetadataService {

    private final DocumentRepository documentRepository;

    public DocumentMetadataService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public boolean isTitleQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("title")
                || normalized.contains("\u9898\u76ee")
                || normalized.contains("\u6807\u9898")
                || normalized.contains("\u8bba\u6587\u540d");
    }

    public boolean isOverviewQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("what is this paper about")
                || normalized.contains("what does this paper study")
                || normalized.contains("what problem does this paper study")
                || normalized.contains("abstract")
                || normalized.contains("summary")
                || normalized.contains("\u7814\u7a76\u4e86\u4ec0\u4e48")
                || normalized.contains("\u4e3b\u8981\u7814\u7a76")
                || normalized.contains("\u8bb2\u4e86\u4ec0\u4e48")
                || normalized.contains("\u5173\u4e8e\u4ec0\u4e48")
                || normalized.contains("\u6458\u8981")
                || normalized.contains("\u603b\u7ed3")
                || normalized.contains("\u6982\u8ff0")
                || normalized.contains("\u4e3b\u8981\u5185\u5bb9");
    }

    public ResearchDocument findPrimaryDocument(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }
        return documentRepository.findById(documentIds.get(0)).orElse(null);
    }

    public String answerTitleQuestion(ResearchDocument document) {
        if (document == null || document.title() == null || document.title().isBlank()) {
            return "The paper title is not available in the indexed metadata yet.";
        }
        return "The paper title is: " + document.title();
    }

    public String answerOverviewQuestion(ResearchDocument document) {
        if (document == null || document.title() == null || document.title().isBlank()) {
            return "\u5f53\u524d\u8fd8\u65e0\u6cd5\u4ec5\u57fa\u4e8e\u6807\u9898\u6982\u8ff0\u8fd9\u7bc7\u8bba\u6587\u7684\u4e3b\u9898\u3002";
        }
        return "\u4ece\u8bba\u6587\u6807\u9898\u770b\uff0c\u8fd9\u7bc7\u8bba\u6587\u4e3b\u8981\u7814\u7a76\uff1a" + stripExtension(document.title()) + "\u3002";
    }

    private String stripExtension(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceFirst("(?i)\\.pdf$", "");
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT);
    }
}
