package com.researchassistant.memory;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionWorkspaceService sessionWorkspaceService;

    @Test
    void listSessionsReturnsWorkspacePayload() throws Exception {
        when(sessionWorkspaceService.listSessions()).thenReturn(List.of(Map.of(
                "sessionId", 1L,
                "sessionKey", "session-1",
                "currentTask", "Summarize paper",
                "rollingSummary", "summary",
                "messageCount", 4,
                "lastDepositAt", ""
        )));

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].sessionKey").value("session-1"));
    }

    @Test
    void closeSessionReturnsFlushSnapshot() throws Exception {
        when(sessionWorkspaceService.flushAndClose("session-1")).thenReturn(Map.of(
                "sessionKey", "session-1",
                "lastDepositedMessageId", 8,
                "lastDepositAt", "2026-04-18T10:20:00+08:00"
        ));

        mockMvc.perform(post("/api/sessions/session-1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastDepositedMessageId").value(8));
    }
}
