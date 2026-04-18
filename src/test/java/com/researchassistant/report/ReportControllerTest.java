package com.researchassistant.report;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportExportService reportExportService;

    @Test
    void exportReturnsReportPathAndContent() throws Exception {
        when(reportExportService.export("session-1")).thenReturn(new ReportExportResult(
                "session-1",
                "/tmp/report.md",
                "# Report",
                OffsetDateTime.parse("2026-04-18T10:30:00+08:00")
        ));

        mockMvc.perform(post("/api/reports/session-1/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportPath").value("/tmp/report.md"))
                .andExpect(jsonPath("$.content").value("# Report"));
    }
}
