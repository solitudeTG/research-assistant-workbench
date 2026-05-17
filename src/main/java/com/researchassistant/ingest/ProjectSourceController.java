package com.researchassistant.ingest;

import com.researchassistant.ingest.model.SourceDocument;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/sources")
public class ProjectSourceController {

    private final DocumentIngestService documentIngestService;

    public ProjectSourceController(DocumentIngestService documentIngestService) {
        this.documentIngestService = documentIngestService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importFileSource(
            @PathVariable String projectId,
            @RequestPart("file") MultipartFile file) {
        SourceDocument source = documentIngestService.importProjectFileSource(projectId, file);
        return responseFor(source);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importJsonSource(
            @PathVariable String projectId,
            @RequestBody Map<String, String> request) {
        try {
            SourceDocument source = documentIngestService.importProjectWebOrNoteSource(
                    projectId,
                    request.get("type"),
                    request.get("title"),
                    request.get("uri"),
                    request.get("content")
            );
            return responseFor(source);
        } catch (UnsupportedSourceTypeException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "unsupported_source_type",
                    "message", exception.getMessage()
            ));
        }
    }

    @GetMapping
    public ResponseEntity<List<SourceDocument>> listSources(@PathVariable String projectId) {
        return ResponseEntity.ok(documentIngestService.listProjectSources(projectId));
    }

    @GetMapping("/{sourceId}")
    public ResponseEntity<Map<String, Object>> getSource(
            @PathVariable String projectId,
            @PathVariable String sourceId) {
        return documentIngestService.findProjectSource(projectId, sourceId)
                .map(source -> ResponseEntity.ok(bodyFor(source)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{sourceId}/retry")
    public ResponseEntity<Map<String, Object>> retrySource(
            @PathVariable String projectId,
            @PathVariable String sourceId) {
        Optional<SourceDocument> source = documentIngestService.retryProjectSource(projectId, sourceId);
        return source.map(this::responseFor)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<Map<String, Object>> responseFor(SourceDocument source) {
        HttpStatus status = "failed".equals(source.status())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(bodyFor(source));
    }

    private Map<String, Object> bodyFor(SourceDocument source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", source.id());
        body.put("sourceId", source.id());
        body.put("projectId", source.projectId());
        body.put("type", source.type());
        body.put("title", source.title());
        body.put("uri", source.uri());
        body.put("status", source.status());
        if (source.failureStage() != null) {
            body.put("failureStage", source.failureStage());
        }
        if (source.errorMessage() != null && !source.errorMessage().isBlank()) {
            body.put("errorMessage", source.errorMessage());
        }
        body.put("depositedKnowledgeCount", source.depositedKnowledgeCount());
        body.put("createdAt", source.createdAt());
        body.put("updatedAt", source.updatedAt());
        return body;
    }
}
