package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.memory.GlobalKnowledgeSnapshot;
import com.researchassistant.knowledge.KnowledgeEntryRecord;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.websearch.WebSearchPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class DefaultProjectAgentToolLoop implements ProjectAgentToolLoop {

    private final ChatClient chatClient;
    private final PaperRagService paperRagService;
    private final MemoryRecallPort memoryRecallPort;
    private final WebSearchPort webSearchPort;
    private final ObjectMapper objectMapper;
    private final AgentTracePublisher tracePublisher;

    public DefaultProjectAgentToolLoop(ChatClient chatClient,
                                       PaperRagService paperRagService,
                                       MemoryRecallPort memoryRecallPort,
                                       WebSearchPort webSearchPort,
                                       ObjectMapper objectMapper,
                                       AgentTracePublisher tracePublisher) {
        this.chatClient = chatClient;
        this.paperRagService = paperRagService;
        this.memoryRecallPort = memoryRecallPort;
        this.webSearchPort = webSearchPort;
        this.objectMapper = objectMapper;
        this.tracePublisher = tracePublisher;
    }

    @Override
    public ProjectAgentRun run(ProjectAgentRequest request) {
        AgentTraceContext traceContext = new AgentTraceContext(
                request.projectId(),
                request.sessionId(),
                request.runId(),
                request.messageId(),
                request.answerId()
        );
        ProjectAgentTools tools = new ProjectAgentTools(
                request.memory().sessionId(),
                request.evidenceScope(),
                paperRagService,
                memoryRecallPort,
                webSearchPort,
                objectMapper,
                tracePublisher,
                traceContext
        );
        String answer = chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt(request))
                .tools(tools)
                .call()
                .content();
        return new ProjectAgentRun(
                answer == null ? "" : answer,
                tools.ragResult(),
                tools.webSearchResult(),
                tools.memoryRecallResult(),
                tools.toolsUsed()
        );
    }

    private String systemPrompt() {
        return """
                You are the main project research agent. Decide which tools to call from the user's request.
                Use web_search for external, fresh, real-time, or non-project facts.
                Use paper_rag for current project paper/source evidence.
                Use memory_recall for previous project discussion.
                Do not claim that you searched, queried, retrieved, or checked external facts unless a tool result is present.
                Separate local paper evidence, web supplements, and memory/project knowledge context in the final answer.
                """;
    }

    private String userPrompt(ProjectAgentRequest request) {
        WorkingMemory memory = request.memory();
        GlobalKnowledgeSnapshot globalKnowledge = request.globalKnowledge();
        return "Question: " + request.question()
                + "\n\nWorking memory:\n" + safe(memory.rollingSummary())
                + "\n\nCurrent task:\n" + safe(memory.currentTask())
                + "\n\nGlobal user knowledge:\n" + safe(globalKnowledge.user())
                + "\n\nGlobal research state:\n" + safe(globalKnowledge.researchState())
                + "\n\nConfirmed project knowledge context (not citation evidence):\n" + projectKnowledgeBlock(request.projectKnowledge())
                + "\n\nScoped indexed paper document ids:\n" + request.evidenceScope().indexedDocumentIds()
                + "\n\nWeb supplement allowed by user: " + request.allowWebSupplement();
    }

    private String projectKnowledgeBlock(java.util.List<KnowledgeEntryRecord> entries) {
        if (entries == null || entries.isEmpty()) {
            return "(empty)";
        }
        return entries.stream()
                .map(entry -> "- " + safe(entry.title()) + ": " + safe(entry.content()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }
}
