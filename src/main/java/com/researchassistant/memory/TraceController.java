package com.researchassistant.memory;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traces")
public class TraceController {

    private final SessionWorkspaceService sessionWorkspaceService;

    public TraceController(SessionWorkspaceService sessionWorkspaceService) {
        this.sessionWorkspaceService = sessionWorkspaceService;
    }

    @GetMapping("/{sessionKey}")
    public ResponseEntity<Map<String, Object>> listTraces(@PathVariable String sessionKey) {
        return ResponseEntity.ok(Map.of("traces", sessionWorkspaceService.sessionTraces(sessionKey)));
    }
}
