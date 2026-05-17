package com.researchassistant.chat;

import com.researchassistant.memory.GlobalKnowledgeService;
import com.researchassistant.memory.GlobalKnowledgeNoteType;
import com.researchassistant.memory.GlobalKnowledgeSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemStatusService systemStatusService;

    @MockBean
    private GlobalKnowledgeService globalKnowledgeService;

    @Test
    void pingReturnsOkPayload() throws Exception {
        when(systemStatusService.ping()).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(get("/api/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void workspaceMetricsReturnsCounts() throws Exception {
        when(systemStatusService.workspaceMetrics()).thenReturn(Map.of("documents", 3, "sessions", 2));

        mockMvc.perform(get("/api/system/workspace-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(3))
                .andExpect(jsonPath("$.sessions").value(2));
    }

    @Test
    void globalKnowledgeReturnsL2CognitionSnapshot() throws Exception {
        when(globalKnowledgeService.snapshot()).thenReturn(new GlobalKnowledgeSnapshot(
                "Prefers concise explanations.",
                "Act as a rigorous research assistant.",
                "Current focus is memory visualization."
        ));

        mockMvc.perform(get("/api/system/global-knowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("Prefers concise explanations."))
                .andExpect(jsonPath("$.soul").value("Act as a rigorous research assistant."))
                .andExpect(jsonPath("$.researchState").value("Current focus is memory visualization."));
    }

    @Test
    void patchGlobalKnowledgeUpdatesOneL2CognitionNoteAndReturnsSnapshot() throws Exception {
        when(globalKnowledgeService.snapshot()).thenReturn(new GlobalKnowledgeSnapshot(
                "USER note",
                "SOUL note",
                "Research state note"
        ));

        mockMvc.perform(patch("/api/system/global-knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "noteType": "USER",
                                  "content": "USER note"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("USER note"))
                .andExpect(jsonPath("$.soul").value("SOUL note"))
                .andExpect(jsonPath("$.researchState").value("Research state note"));

        verify(globalKnowledgeService).set(GlobalKnowledgeNoteType.USER, "USER note");
    }
}
