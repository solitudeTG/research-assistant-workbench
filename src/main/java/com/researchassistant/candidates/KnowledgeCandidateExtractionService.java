package com.researchassistant.candidates;

import com.researchassistant.evidence.EvidenceAssessment;
import com.researchassistant.evidence.EvidenceLevel;
import com.researchassistant.evidence.EvidenceSourceRecord;
import com.researchassistant.evidence.EvidenceSourceRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeCandidateExtractionService {

    private final EvidenceSourceRepository evidenceSourceRepository;
    private final KnowledgeCandidateRepository candidateRepository;

    public KnowledgeCandidateExtractionService(
            EvidenceSourceRepository evidenceSourceRepository,
            KnowledgeCandidateRepository candidateRepository) {
        this.evidenceSourceRepository = evidenceSourceRepository;
        this.candidateRepository = candidateRepository;
    }

    public List<KnowledgeCandidateRecord> extractFromAnswer(
            String projectId,
            String sessionId,
            String runId,
            String answerId,
            String answer,
            EvidenceAssessment assessment,
            boolean enabled) {
        if (!enabled || answer == null || answer.isBlank()) {
            return List.of();
        }
        if (assessment == null || assessment.evidenceLevel() != EvidenceLevel.SUFFICIENT) {
            return List.of();
        }
        List<EvidenceSourceRecord> evidenceSources = evidenceSourceRepository.findByAnswer(projectId, answerId);
        if (evidenceSources.isEmpty()) {
            return List.of();
        }
        KnowledgeCandidateRecord candidate = candidateRepository.createCandidate(
                projectId,
                sessionId,
                answerId,
                titleFromAnswer(answer),
                answer.strip(),
                "confirmed_finding",
                sourceTypes(evidenceSources),
                evidenceSources.stream().map(EvidenceSourceRecord::id).toList(),
                runId
        );
        return List.of(candidate);
    }

    private String titleFromAnswer(String answer) {
        String firstLine = answer.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("Research finding");
        String cleaned = firstLine.replaceFirst("^#+\\s*", "").strip();
        if (cleaned.length() <= 500) {
            return cleaned;
        }
        return cleaned.substring(0, 500).strip();
    }

    private List<String> sourceTypes(List<EvidenceSourceRecord> evidenceSources) {
        LinkedHashSet<String> sourceTypes = new LinkedHashSet<>();
        for (EvidenceSourceRecord source : evidenceSources) {
            if (source.sourceType() != null && !source.sourceType().isBlank()) {
                sourceTypes.add(source.sourceType());
            }
        }
        return List.copyOf(sourceTypes);
    }
}
