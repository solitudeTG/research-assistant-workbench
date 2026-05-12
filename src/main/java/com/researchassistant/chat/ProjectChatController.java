package com.researchassistant.chat;

import com.researchassistant.chat.dto.ProjectMessageRequest;
import com.researchassistant.chat.dto.ProjectMessageResponse;
import com.researchassistant.chat.dto.ProjectSessionMessageResponse;
import com.researchassistant.memory.ChatMessageRecord;
import com.researchassistant.memory.WorkingMemoryService;
import com.researchassistant.orchestrator.SupervisorService;
import com.researchassistant.project.ProjectRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final WorkingMemoryService workingMemoryService;

    public ProjectChatController(
            SupervisorService supervisorService,
            ProjectRepository projectRepository,
            WorkingMemoryService workingMemoryService) {
        this.supervisorService = supervisorService;
        this.projectRepository = projectRepository;
        this.workingMemoryService = workingMemoryService;
    }

    @GetMapping("/api/projects/{projectId}/sessions/{sessionId}/messages")
    public List<ProjectSessionMessageResponse> listMessages(
            @PathVariable String projectId,
            @PathVariable String sessionId) {
        if (projectRepository.findProject(projectId).isEmpty()
                || projectRepository.findSession(projectId, sessionId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project session not found");
        }
        return workingMemoryService.listMessages(sessionId).stream()
                .map(message -> toProjectMessage(sessionId, message))
                .toList();
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
                    "sourceFilters require project-source-to-index mapping and are not supported yet"
            );
        }
        return supervisorService.answerProject(projectId, sessionId, request);
    }

    private ProjectSessionMessageResponse toProjectMessage(String sessionId, ChatMessageRecord message) {
        return new ProjectSessionMessageResponse(
                Long.toString(message.id()),
                sessionId,
                message.role().toLowerCase(Locale.ROOT),
                message.content(),
                message.answerMode(),
                message.createdAt()
        );
    }
}
