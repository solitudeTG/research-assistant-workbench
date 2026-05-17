package com.researchassistant.evidence;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceSourceController {

    private final EvidenceSourceRepository evidenceSourceRepository;

    public EvidenceSourceController(EvidenceSourceRepository evidenceSourceRepository) {
        this.evidenceSourceRepository = evidenceSourceRepository;
    }

    @GetMapping("/api/projects/{projectId}/answers/{answerId}/evidence")
    public ResponseEntity<List<EvidenceSourceRecord>> listAnswerEvidence(
            @PathVariable String projectId,
            @PathVariable String answerId) {
        return ResponseEntity.ok(evidenceSourceRepository.findByAnswer(projectId, answerId));
    }
}
