package com.researchassistant.report;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportExportService reportExportService;

    public ReportController(ReportExportService reportExportService) {
        this.reportExportService = reportExportService;
    }

    @PostMapping("/{sessionKey}/export")
    public ResponseEntity<Map<String, Object>> export(@PathVariable String sessionKey) {
        ReportExportResult result = reportExportService.export(sessionKey);
        return ResponseEntity.ok(Map.of(
                "sessionKey", result.sessionKey(),
                "reportPath", result.reportPath(),
                "createdAt", result.createdAt().toString(),
                "content", result.content()
        ));
    }
}
