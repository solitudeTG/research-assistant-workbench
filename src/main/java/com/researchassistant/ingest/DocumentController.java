package com.researchassistant.ingest;

import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestService documentIngestService;

    public DocumentController(DocumentIngestService documentIngestService) {
        this.documentIngestService = documentIngestService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) {
        Map<String, Object> response = documentIngestService.registerUpload(file);
        if ("FAILED".equals(response.get("status"))) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable long documentId) {
        Optional<com.researchassistant.ingest.model.ResearchDocument> document = documentIngestService.findDocument(documentId);
        if (document.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("documentId", document.get().id());
        body.put("title", document.get().title());
        body.put("status", document.get().status().name());
        if (document.get().failureStage() != null) {
            body.put("failureStage", document.get().failureStage().name());
        }
        if (document.get().parseError() != null && !document.get().parseError().isBlank()) {
            body.put("parseError", document.get().parseError());
        }
        return ResponseEntity.ok(body);
    }
}
