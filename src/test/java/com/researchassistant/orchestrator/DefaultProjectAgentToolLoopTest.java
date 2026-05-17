package com.researchassistant.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.events.InMemoryWorkbenchEventPublisher;
import com.researchassistant.memory.GlobalKnowledgeSnapshot;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.websearch.WebSearchPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultProjectAgentToolLoopTest {

    private final ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
    private final PaperRagService paperRagService = mock(PaperRagService.class);
    private final MemoryRecallPort memoryRecallPort = mock(MemoryRecallPort.class);
    private final WebSearchPort webSearchPort = mock(WebSearchPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runExposesProjectToolsToTheMainAgentInsteadOfPreRouting() {
        when(chatClient.prompt().system(anyString()).user(anyString()).tools(org.mockito.ArgumentMatchers.any()).call().content())
                .thenReturn("深圳今天需要参考实时天气。");
        DefaultProjectAgentToolLoop toolLoop = new DefaultProjectAgentToolLoop(
                chatClient,
                paperRagService,
                memoryRecallPort,
                webSearchPort,
                objectMapper,
                new AgentTracePublisher(new InMemoryWorkbenchEventPublisher())
        );

        ProjectAgentRun result = toolLoop.run(request("你能帮我去查询一下深圳今天的天气吗"));

        ArgumentCaptor<Object> toolsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(chatClient.prompt().system(anyString()).user(anyString()), atLeastOnce()).tools(toolsCaptor.capture());
        assertThat(toolsCaptor.getAllValues()).anyMatch(ProjectAgentTools.class::isInstance);
        assertThat(result.answer()).isEqualTo("深圳今天需要参考实时天气。");
    }

    private ProjectAgentRequest request(String question) {
        return new ProjectAgentRequest(
                "project-42",
                "session-42",
                "run-42",
                "msg-42",
                "ans-42",
                question,
                new WorkingMemory(42L, "session-42", "weather context", null, List.of(), List.of(), 0L, null, 0),
                new GlobalKnowledgeSnapshot("", "", ""),
                new ProjectEvidenceScope(List.of(), Map.of()),
                true
        );
    }
}
