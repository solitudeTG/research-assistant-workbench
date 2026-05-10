package com.researchassistant.chat;

import com.researchassistant.chat.dto.ProjectMessageRequest;
import com.researchassistant.chat.dto.ProjectMessageResponse;
import com.researchassistant.orchestrator.SupervisorService;
import com.researchassistant.project.ProjectRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProjectChatController {

    private final SupervisorService supervisorService;
    private final ProjectRepository projectRepository;

    public ProjectChatController(
            SupervisorService supervisorService,
            ProjectRepository projectRepository) {
        this.supervisorService = supervisorService;
        this.projectRepository = projectRepository;
    }

    @PostMapping("/api/projects/{projectId}/sessions/{sessionId}/messages")
    public ProjectMessageResponse createMessage(
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @Valid @RequestBody ProjectMessageRequest request) {
        if (projectRepository.findProject(projectId).isEmpty()
                || projectRepository.findSession(projectId, sessionId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project session not found");
        }
        if (request.sourceFilters() != null && !request.sourceFilters().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sourceFilters are not supported until F007"
            );
        }
        return supervisorService.answerProject(projectId, sessionId, request);
    }
}
