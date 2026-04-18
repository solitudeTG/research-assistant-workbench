package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentAnalysis;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DocumentAnalysisService {

    private final DocumentStructureAnalyzer documentStructureAnalyzer;
    private final DocumentAnalysisRepository documentAnalysisRepository;

    public DocumentAnalysisService(
            DocumentStructureAnalyzer documentStructureAnalyzer,
            DocumentAnalysisRepository documentAnalysisRepository) {
        this.documentStructureAnalyzer = documentStructureAnalyzer;
        this.documentAnalysisRepository = documentAnalysisRepository;
    }

    public void analyzeAndStore(long documentId, String title, String rawText) {
        DocumentAnalysisDraft draft = documentStructureAnalyzer.analyze(title, rawText);
        documentAnalysisRepository.upsert(documentId, draft);
    }

    public Optional<DocumentAnalysis> findByDocumentId(long documentId) {
        return documentAnalysisRepository.findByDocumentId(documentId);
    }
}
