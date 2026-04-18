package com.researchassistant.chat;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatusService systemStatusService;

    public SystemController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return systemStatusService.ping();
    }

    @GetMapping("/model-config")
    public Map<String, Object> modelConfig() {
        return systemStatusService.modelConfig();
    }

    @GetMapping("/workspace-metrics")
    public Map<String, Object> workspaceMetrics() {
        return systemStatusService.workspaceMetrics();
    }
}
