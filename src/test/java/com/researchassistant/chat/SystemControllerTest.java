package com.researchassistant.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemStatusService systemStatusService;

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
}
