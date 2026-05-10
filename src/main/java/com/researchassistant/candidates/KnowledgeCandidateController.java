package com.researchassistant.candidates;

import com.researchassistant.knowledge.KnowledgeBoardRepository;
import com.researchassistant.knowledge.KnowledgeEntryRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class KnowledgeCandidateController {

    private final KnowledgeCandidateRepository candidateRepository;
    private final KnowledgeBoardRepository knowledgeBoardRepository;

    public KnowledgeCandidateController(
            KnowledgeCandidateRepository candidateRepository,
            KnowledgeBoardRepository knowledgeBoardRepository) {
        this.candidateRepository = candidateRepository;
        this.knowledgeBoardRepository = knowledgeBoardRepository;
    }

    @GetMapping("/api/projects/{projectId}/answers/{answerId}/candidates")
    public List<KnowledgeCandidateRecord> listByAnswer(
            @PathVariable String projectId,
            @PathVariable String answerId) {
        return candidateRepository.listByAnswer(projectId, answerId);
    }

    @GetMapping("/api/projects/{projectId}/candidates")
    public List<KnowledgeCandidateRecord> listByProject(@PathVariable String projectId) {
        return candidateRepository.listByProject(projectId);
    }

    @PostMapping("/api/projects/{projectId}/candidates/{candidateId}/accept")
    @Transactional
    public KnowledgeEntryRecord accept(
            @PathVariable String projectId,
            @PathVariable String candidateId) {
        KnowledgeCandidateRecord candidate = findCandidate(projectId, candidateId);
        transitionPending(projectId, candidateId, "accepted");
        KnowledgeEntryRecord entry = knowledgeBoardRepository.createEntry(
                projectId,
                candidate.suggestedSection(),
                candidate.title(),
                candidate.statement(),
                "confirmed",
                candidate.id(),
                candidate.evidenceSourceIds()
        );
        return entry;
    }

    @PostMapping("/api/projects/{projectId}/candidates/{candidateId}/edit-and-accept")
    @Transactional
    public KnowledgeEntryRecord editAndAccept(
            @PathVariable String projectId,
            @PathVariable String candidateId,
            @Valid @RequestBody EditAndAcceptRequest request) {
        KnowledgeCandidateRecord candidate = findCandidate(projectId, candidateId);
        transitionPending(projectId, candidateId, "edited_accepted");
        KnowledgeEntryRecord entry = knowledgeBoardRepository.createEntry(
                projectId,
                request.section(),
                request.title(),
                request.content(),
                request.evidenceStatus(),
                candidate.id(),
                candidate.evidenceSourceIds()
        );
        return entry;
    }

    @PostMapping("/api/projects/{projectId}/candidates/{candidateId}/mark-unverified")
    public KnowledgeCandidateRecord markUnverified(
            @PathVariable String projectId,
            @PathVariable String candidateId) {
        findCandidate(projectId, candidateId);
        return transitionPending(projectId, candidateId, "marked_unverified");
    }

    @PostMapping("/api/projects/{projectId}/candidates/{candidateId}/ignore")
    public KnowledgeCandidateRecord ignore(
            @PathVariable String projectId,
            @PathVariable String candidateId) {
        findCandidate(projectId, candidateId);
        return transitionPending(projectId, candidateId, "ignored");
    }

    private KnowledgeCandidateRecord findCandidate(String projectId, String candidateId) {
        return candidateRepository.findByProject(projectId, candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge candidate not found"));
    }

    private KnowledgeCandidateRecord transitionPending(String projectId, String candidateId, String status) {
        return candidateRepository.transitionPending(projectId, candidateId, status)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Knowledge candidate is no longer pending"
                ));
    }

    public record EditAndAcceptRequest(
            @NotBlank String title,
            @NotBlank String content,
            @NotBlank String section,
            @NotBlank String evidenceStatus
    ) {
    }
}
