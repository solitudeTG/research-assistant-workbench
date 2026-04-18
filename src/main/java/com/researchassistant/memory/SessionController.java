package com.researchassistant.memory;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionWorkspaceService sessionWorkspaceService;

    public SessionController(SessionWorkspaceService sessionWorkspaceService) {
        this.sessionWorkspaceService = sessionWorkspaceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listSessions() {
        return ResponseEntity.ok(Map.of("sessions", sessionWorkspaceService.listSessions()));
    }

    @GetMapping("/{sessionKey}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionKey) {
        return ResponseEntity.ok(sessionWorkspaceService.sessionDetail(sessionKey));
    }

    @GetMapping("/{sessionKey}/messages")
    public ResponseEntity<Map<String, Object>> getSessionMessages(@PathVariable String sessionKey) {
        return ResponseEntity.ok(Map.of("messages", sessionWorkspaceService.sessionMessages(sessionKey)));
    }

    @PostMapping("/{sessionKey}/close")
    public ResponseEntity<Map<String, Object>> closeSession(@PathVariable String sessionKey) {
        return ResponseEntity.ok(sessionWorkspaceService.flushAndClose(sessionKey));
    }
}
