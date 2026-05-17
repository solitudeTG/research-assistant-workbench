package com.researchassistant.events;

import java.time.Duration;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
                event("event-2", WorkbenchEventType.ANSWER_DELTA, 2, Map.of("delta", "hello")),
                event("event-3", WorkbenchEventType.RUN_COMPLETED, 3, Map.of("status", "completed"))
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
    void sseReplaysExistingEventsThenContinuesUntilTerminalRunEvent() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(streamingExecutor).execute(any(Runnable.class));

        when(publisher.readRunEventsAfter(eq("run-1"), isNull()))
                .thenReturn(List.of(event("event-1", WorkbenchEventType.RUN_STARTED, 1, Map.of("question", "why?"))));
        when(publisher.readRunEventsAfter(eq("run-1"), eq("event-1"), any(Duration.class)))
                .thenReturn(List.of(event("event-2", WorkbenchEventType.ANSWER_DELTA, 2, Map.of("delta", "hello"))));
        when(publisher.readRunEventsAfter(eq("run-1"), eq("event-2"), any(Duration.class)))
                .thenReturn(List.of(event("event-3", WorkbenchEventType.RUN_COMPLETED, 3, Map.of("status", "completed"))));

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events",
                        "project-1", "session-1", "run-1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:run.started")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:answer.delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:run.completed")));
    }

    @Test
    void lastEventIdSkipsAlreadyConsumedEventsAndContinuesLiveTail() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(streamingExecutor).execute(any(Runnable.class));

        when(publisher.readRunEventsAfter("run-1", "event-1")).thenReturn(List.of(
                event("event-2", WorkbenchEventType.ANSWER_COMPLETED, 2, Map.of("answerId", "answer-1"))
        ));
        when(publisher.readRunEventsAfter(eq("run-1"), eq("event-2"), any(Duration.class)))
                .thenReturn(List.of(event("event-3", WorkbenchEventType.RUN_COMPLETED, 3, Map.of("status", "completed"))));

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:answer.completed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:event-3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:run.completed")));

        verify(publisher).readRunEventsAfter("run-1", "event-1");
    }

    @Test
    void liveTailStopsSendingEventsAfterTerminalEventInSameBatch() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(streamingExecutor).execute(any(Runnable.class));

        when(publisher.readRunEventsAfter("run-1", null)).thenReturn(List.of(
                event("event-1", WorkbenchEventType.RUN_STARTED, 1, Map.of("question", "why?"))
        ));
        when(publisher.readRunEventsAfter(eq("run-1"), eq("event-1"), any(Duration.class)))
                .thenReturn(List.of(
                        event("event-3", WorkbenchEventType.RUN_COMPLETED, 3, Map.of("status", "completed")),
                        event("event-4", WorkbenchEventType.ANSWER_DELTA, 4, Map.of("delta", "late"))
                ));

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events",
                        "project-1", "session-1", "run-1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:event-3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:run.completed")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id:event-4"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"delta\":\"late\""))));
    }

    @Test
    void liveTailAdvancesCursorPastFilteredEventsWithoutSendingThem() throws Exception {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(streamingExecutor).execute(any(Runnable.class));

        WorkbenchEvent filteredEvent = event(
                "event-filtered",
                WorkbenchEventType.ANSWER_DELTA,
                "project-2",
                "session-2",
                "run-1",
                2,
                Map.of("delta", "wrong path"));
        WorkbenchEvent terminalEvent = event(
                "event-3",
                WorkbenchEventType.RUN_COMPLETED,
                "project-1",
                "session-1",
                "run-1",
                3,
                Map.of("status", "completed"));

        when(publisher.readRunEventsAfter("run-1", null)).thenReturn(List.of(
                event("event-1", WorkbenchEventType.RUN_STARTED, 1, Map.of("question", "why?"))
        ));
        when(publisher.readRunEventsAfter(eq("run-1"), eq("event-1"), any(Duration.class)))
                .thenReturn(List.of(filteredEvent))
                .thenReturn(List.of(terminalEvent));
        when(publisher.readRunEventsAfter(eq("run-1"), eq("event-filtered"), any(Duration.class)))
                .thenReturn(List.of(terminalEvent));

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events",
                        "project-1", "session-1", "run-1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id:event-filtered"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("wrong path"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:event-3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:run.completed")));

        verify(publisher).readRunEventsAfter(eq("run-1"), eq("event-filtered"), any(Duration.class));
    }

    private WorkbenchEvent event(
            String eventId,
            WorkbenchEventType eventType,
            long sequence,
            Map<String, Object> payload) {
        return event(eventId, eventType, "project-1", "session-1", "run-1", sequence, payload);
    }

    private WorkbenchEvent event(
            String eventId,
            WorkbenchEventType eventType,
            String projectId,
            String sessionId,
            String runId,
            long sequence,
            Map<String, Object> payload) {
        return new WorkbenchEvent(
                eventId,
                eventType,
                projectId,
                sessionId,
                runId,
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
