package com.researchassistant.chat;

import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.orchestrator.SupervisorService;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatStreamController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupervisorService supervisorService;

    @MockBean(name = "streamingExecutor")
    private Executor streamingExecutor;

    @Test
    void streamEndpointUsesEventStreamContentType() throws Exception {
        when(supervisorService.answer(any())).thenReturn(new ChatResponse(
                "sse-1",
                "LOCAL_EVIDENCE",
                "hello from stream",
                List.of()
        ));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(streamingExecutor).execute(any(Runnable.class));

        MvcResult result = mockMvc.perform(get("/api/chat/stream")
                        .queryParam("sessionKey", "sse-1")
                        .queryParam("question", "hello"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:message")));
    }
}
