package com.researchassistant.chat;

import com.researchassistant.memory.GlobalKnowledgeService;
import com.researchassistant.memory.GlobalKnowledgeNoteType;
import com.researchassistant.memory.GlobalKnowledgeSnapshot;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatusService systemStatusService;
    private final GlobalKnowledgeService globalKnowledgeService;

    public SystemController(SystemStatusService systemStatusService,
                            GlobalKnowledgeService globalKnowledgeService) {
        this.systemStatusService = systemStatusService;
        this.globalKnowledgeService = globalKnowledgeService;
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

    @GetMapping("/global-knowledge")
    public GlobalKnowledgeSnapshot globalKnowledge() {
        return globalKnowledgeService.snapshot();
    }

    @PatchMapping("/global-knowledge")
    public GlobalKnowledgeSnapshot updateGlobalKnowledge(@RequestBody GlobalKnowledgeRequest request) {
        globalKnowledgeService.set(request.noteType(), request.content());
        return globalKnowledgeService.snapshot();
    }

    public record GlobalKnowledgeRequest(
            GlobalKnowledgeNoteType noteType,
            String content
    ) {
    }
}
