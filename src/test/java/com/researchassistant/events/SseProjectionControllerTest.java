package com.researchassistant.events;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SseProjectionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SseProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkbenchEventPublisher publisher;

    @MockBean(name = "streamingExecutor")
    private Executor streamingExecutor;

    @Test
    void projectsRunEventsAsSseUsingEnvelopeIdAndType() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(streamingExecutor).execute(any(Runnable.class));

        when(publisher.readRunEventsAfter("run-1", null)).thenReturn(List.of(
                event("event-1", WorkbenchEventType.RUN_STARTED, 1, Map.of("question", "why?")),
                event("event-2", WorkbenchEventType.ANSWER_DELTA, 2, Map.of("delta", "hello"))
        ));

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events",
                        "project-1", "session-1", "run-1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:event-1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:run.started")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"eventType\":\"run.started\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"eventType\":\"RUN_STARTED\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:event-2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:answer.delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"eventType\":\"answer.delta\"")));
    }

    @Test
    void lastEventIdSkipsAlreadyConsumedEvents() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(streamingExecutor).execute(any(Runnable.class));

        when(publisher.readRunEventsAfter("run-1", "event-1")).thenReturn(List.of(
                event("event-2", WorkbenchEventType.ANSWER_COMPLETED, 2, Map.of("answerId", "answer-1"))
        ));

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events",
                        "project-1", "session-1", "run-1")
                        .header("Last-Event-ID", "event-1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id:event-1"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:event-2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:answer.completed")));

        verify(publisher).readRunEventsAfter("run-1", "event-1");
    }

    private WorkbenchEvent event(
            String eventId,
            WorkbenchEventType eventType,
            long sequence,
            Map<String, Object> payload) {
        return new WorkbenchEvent(
                eventId,
                eventType,
                "project-1",
                "session-1",
                "run-1",
                "test",
                sequence,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-05-09T12:00:00Z"),
                payload
        );
    }
}
