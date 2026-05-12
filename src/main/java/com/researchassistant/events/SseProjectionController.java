package com.researchassistant.events;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events")
public class SseProjectionController {

    private static final long LIVE_TAIL_TIMEOUT_MS = 0L;

    private final WorkbenchEventPublisher eventPublisher;
    private final Executor streamingExecutor;

    public SseProjectionController(
            WorkbenchEventPublisher eventPublisher,
            @Qualifier("streamingExecutor") Executor streamingExecutor) {
        this.eventPublisher = eventPublisher;
        this.streamingExecutor = streamingExecutor;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @PathVariable String runId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter = new SseEmitter(LIVE_TAIL_TIMEOUT_MS);

        streamingExecutor.execute(() -> {
            try {
                String cursor = lastEventId;
                boolean terminal = false;

                for (WorkbenchEvent event : eventPublisher.readRunEventsAfter(runId, cursor)) {
                    cursor = event.eventId();
                    if (belongsToPath(event, projectId, sessionId, runId)) {
                        sendEvent(emitter, event);
                        terminal = WorkbenchRunEventStream.isTerminal(event.eventType());
                        if (terminal) {
                            break;
                        }
                    }
                }

                while (!terminal) {
                    List<WorkbenchEvent> nextEvents = eventPublisher.readRunEventsAfter(
                            runId,
                            cursor,
                            Duration.ofSeconds(15)
                    );
                    if (nextEvents.isEmpty()) {
                        emitter.send(SseEmitter.event().comment("keepalive"));
                        continue;
                    }
                    for (WorkbenchEvent event : nextEvents) {
                        cursor = event.eventId();
                        if (belongsToPath(event, projectId, sessionId, runId)) {
                            sendEvent(emitter, event);
                            terminal = WorkbenchRunEventStream.isTerminal(event.eventType());
                            if (terminal) {
                                break;
                            }
                        }
                    }
                }

                emitter.complete();
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });

        return emitter;
    }

    private boolean belongsToPath(WorkbenchEvent event, String projectId, String sessionId, String runId) {
        return projectId.equals(event.projectId())
                && sessionId.equals(event.sessionId())
                && runId.equals(event.runId());
    }

    private void sendEvent(SseEmitter emitter, WorkbenchEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(event.eventId())
                .name(event.eventType().wireName())
                .data(sseData(event)));
    }

    private Map<String, Object> sseData(WorkbenchEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", event.eventId());
        data.put("eventType", event.eventType().wireName());
        data.put("projectId", event.projectId());
        data.put("sessionId", event.sessionId());
        data.put("runId", event.runId());
        data.put("actor", event.actor());
        data.put("sequence", event.sequence());
        data.put("sourceId", event.sourceId());
        data.put("answerId", event.answerId());
        data.put("turnId", event.turnId());
        data.put("createdAt", event.createdAt());
        data.put("payload", event.payload());
        return data;
    }
}
