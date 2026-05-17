package com.researchassistant.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostMapping
    public ResponseEntity<ProjectRecord> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectRecord project = projectRepository.createProject(request.topic(), request.summary());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    public List<ProjectRecord> listProjects() {
        return projectRepository.listProjects();
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectRecord> getProject(@PathVariable String projectId) {
        return projectRepository.findProject(projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{projectId}/sessions")
    public ResponseEntity<ResearchSessionRecord> createSession(
            @PathVariable String projectId,
            @Valid @RequestBody CreateSessionRequest request
    ) {
        if (projectRepository.findProject(projectId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ResearchSessionRecord session = projectRepository.createSession(projectId, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/{projectId}/sessions")
    public ResponseEntity<List<ResearchSessionRecord>> listSessions(@PathVariable String projectId) {
        if (projectRepository.findProject(projectId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(projectRepository.listSessions(projectId));
    }

    @PatchMapping("/{projectId}/sessions/{sessionId}")
    public ResponseEntity<ResearchSessionRecord> renameSession(
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @Valid @RequestBody RenameSessionRequest request
    ) {
        if (projectRepository.findProject(projectId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return projectRepository.renameSession(projectId, sessionId, request.title())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{projectId}/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String projectId,
            @PathVariable String sessionId
    ) {
        if (projectRepository.findProject(projectId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return projectRepository.deleteSession(projectId, sessionId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    public record CreateProjectRequest(
            @NotBlank String topic,
            String summary
    ) {
        public CreateProjectRequest {
            summary = summary == null ? "" : summary;
        }
    }

    public record CreateSessionRequest(@NotBlank String title) {
    }

    public record RenameSessionRequest(@NotBlank String title) {
        public RenameSessionRequest {
            title = title.trim();
        }
    }
}
