package com.researchassistant.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class KnowledgeBoardController {

    private final KnowledgeBoardRepository knowledgeBoardRepository;

    public KnowledgeBoardController(KnowledgeBoardRepository knowledgeBoardRepository) {
        this.knowledgeBoardRepository = knowledgeBoardRepository;
    }

    @GetMapping("/api/projects/{projectId}/knowledge-board")
    public List<KnowledgeBoardSection> listBoard(@PathVariable String projectId) {
        return knowledgeBoardRepository.listBoard(projectId);
    }

    @PostMapping("/api/projects/{projectId}/knowledge-board/entries")
    public ResponseEntity<KnowledgeEntryRecord> createEntry(
            @PathVariable String projectId,
            @Valid @RequestBody EntryRequest request) {
        KnowledgeEntryRecord entry = knowledgeBoardRepository.createEntry(
                projectId,
                request.section(),
                request.title(),
                request.content(),
                request.evidenceStatus(),
                null,
                request.evidenceSourceIds() == null ? List.of() : request.evidenceSourceIds()
        );
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/knowledge-board/entries/" + entry.id()))
                .body(entry);
    }

    @PatchMapping("/api/projects/{projectId}/knowledge-board/entries/{entryId}")
    public KnowledgeEntryRecord patchEntry(
            @PathVariable String projectId,
            @PathVariable String entryId,
            @Valid @RequestBody EntryRequest request) {
        return knowledgeBoardRepository.patchEntry(
                        projectId,
                        entryId,
                        request.section(),
                        request.title(),
                        request.content(),
                        request.evidenceStatus()
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge entry not found"));
    }

    @DeleteMapping("/api/projects/{projectId}/knowledge-board/entries/{entryId}")
    public Map<String, Boolean> archiveEntry(
            @PathVariable String projectId,
            @PathVariable String entryId) {
        if (!knowledgeBoardRepository.archiveEntry(projectId, entryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge entry not found");
        }
        return Map.of("archived", true);
    }

    public record EntryRequest(
            @NotBlank String section,
            @NotBlank String title,
            @NotBlank String content,
            @NotBlank String evidenceStatus,
            List<String> evidenceSourceIds
    ) {
    }
}
