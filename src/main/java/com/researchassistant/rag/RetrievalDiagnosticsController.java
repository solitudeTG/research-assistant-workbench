package com.researchassistant.rag;

import com.researchassistant.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RetrievalDiagnosticsController {

    private final ProjectRepository projectRepository;
    private final RetrievalDiagnosticsService retrievalDiagnosticsService;

    public RetrievalDiagnosticsController(
            ProjectRepository projectRepository,
            RetrievalDiagnosticsService retrievalDiagnosticsService) {
        this.projectRepository = projectRepository;
        this.retrievalDiagnosticsService = retrievalDiagnosticsService;
    }

    @GetMapping("/api/projects/{projectId}/sessions/{sessionId}/retrieval-diagnostics")
    public RetrievalDiagnosticsResponse sessionDiagnostics(
            @PathVariable String projectId,
            @PathVariable String sessionId
    ) {
        if (projectRepository.findProject(projectId).isEmpty()
                || projectRepository.findSession(projectId, sessionId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project session not found");
        }
        return retrievalDiagnosticsService.sessionDiagnostics(projectId, sessionId);
    }
}
