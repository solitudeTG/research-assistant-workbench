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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TraceController.class)
@AutoConfigureMockMvc(addFilters = false)
class TraceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionWorkspaceService sessionWorkspaceService;

    @Test
    void listTracesReturnsTracePayload() throws Exception {
        when(sessionWorkspaceService.sessionTraces("session-1")).thenReturn(List.of(
                new com.researchassistant.rag.RetrievalTraceView(
                        3L,
                        1L,
                        "attention",
                        Map.of("documentIds", List.of(1L)),
                        List.of(),
                        List.of(),
                        java.time.OffsetDateTime.parse("2026-04-18T10:20:00+08:00")
                )
        ));

        mockMvc.perform(get("/api/traces/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traces[0].queryText").value("attention"));
    }
}
