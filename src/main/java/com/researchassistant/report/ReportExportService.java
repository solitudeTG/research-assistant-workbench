package com.researchassistant.report;

import com.researchassistant.ingest.DocumentIngestService;
import com.researchassistant.memory.SessionWorkspaceService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {

    private final SessionWorkspaceService sessionWorkspaceService;
    private final DocumentIngestService documentIngestService;
    private final Path reportRoot;

    public ReportExportService(SessionWorkspaceService sessionWorkspaceService,
                               DocumentIngestService documentIngestService,
                               @Value("${app.storage.root}") String storageRoot) {
        this.sessionWorkspaceService = sessionWorkspaceService;
        this.documentIngestService = documentIngestService;
        this.reportRoot = Paths.get(storageRoot).toAbsolutePath().normalize().resolve("reports");
    }

    public ReportExportResult export(String sessionKey) {
        Map<String, Object> session = sessionWorkspaceService.sessionDetail(sessionKey);
        var messages = sessionWorkspaceService.sessionMessages(sessionKey);
        var traces = sessionWorkspaceService.sessionTraces(sessionKey);
        var documents = documentIngestService.listDocuments();

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Research Assistant Report").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("- Session Key: ").append(sessionKey).append(System.lineSeparator());
        markdown.append("- Current Task: ").append(session.get("currentTask")).append(System.lineSeparator());
        markdown.append("- Message Count: ").append(session.get("messageCount")).append(System.lineSeparator()).append(System.lineSeparator());

        markdown.append("## Working Memory").append(System.lineSeparator());
        markdown.append(session.get("rollingSummary")).append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("### Salient Facts").append(System.lineSeparator());
        markdown.append(session.get("salientFacts")).append(System.lineSeparator()).append(System.lineSeparator());

        markdown.append("## Conversation").append(System.lineSeparator());
        for (Map<String, Object> message : messages) {
            markdown.append("### ").append(message.get("role")).append(System.lineSeparator());
            markdown.append(message.get("content")).append(System.lineSeparator()).append(System.lineSeparator());
        }

        markdown.append("## Retrieval Traces").append(System.lineSeparator());
        traces.forEach(trace -> markdown.append("- ").append(trace.queryText()).append(" @ ").append(trace.createdAt()).append(System.lineSeparator()));
        markdown.append(System.lineSeparator());

        markdown.append("## Indexed Documents").append(System.lineSeparator());
        documents.forEach(document -> markdown.append("- ").append(document.title())
                .append(" [").append(document.status()).append("] ")
                .append(document.totalChunks()).append(" chunks")
                .append(System.lineSeparator()));

        OffsetDateTime now = OffsetDateTime.now();
        String fileName = sessionKey + "-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md";
        Path target = reportRoot.resolve(fileName);
        try {
            Files.createDirectories(reportRoot);
            Files.writeString(target, markdown.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export report", e);
        }

        return new ReportExportResult(sessionKey, target.toString(), markdown.toString(), now);
    }
}
