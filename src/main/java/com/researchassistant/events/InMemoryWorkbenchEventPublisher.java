package com.researchassistant.events;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.events.backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryWorkbenchEventPublisher implements WorkbenchEventPublisher {

    private final Map<String, AtomicLong> runSequences = new ConcurrentHashMap<>();
    private final Map<String, List<WorkbenchEvent>> eventsByRun = new ConcurrentHashMap<>();

    @Override
    public synchronized WorkbenchEvent publish(WorkbenchEvent event) {
        String streamKey = streamKey(event);
        long sequence = runSequences
                .computeIfAbsent(streamKey, ignored -> new AtomicLong())
                .incrementAndGet();
        WorkbenchEvent published = event.withPublishedEnvelope(
                UUID.randomUUID().toString(),
                sequence,
                OffsetDateTime.now()
        );
        eventsByRun.computeIfAbsent(streamKey, ignored -> new ArrayList<>()).add(published);
        return published;
    }

    @Override
    public synchronized List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId) {
        List<WorkbenchEvent> runEvents = eventsByRun.getOrDefault(runStreamKey(runId), List.of()).stream()
                .sorted(Comparator.comparingLong(WorkbenchEvent::sequence))
                .toList();
        if (lastEventId == null || lastEventId.isBlank()) {
            return runEvents;
        }

        int lastSeenIndex = -1;
        for (int index = 0; index < runEvents.size(); index++) {
            if (lastEventId.equals(runEvents.get(index).eventId())) {
                lastSeenIndex = index;
                break;
            }
        }
        if (lastSeenIndex < 0) {
            return runEvents;
        }
        return runEvents.subList(lastSeenIndex + 1, runEvents.size());
    }

    private String streamKey(WorkbenchEvent event) {
        if (event.runId() != null) {
            return runStreamKey(event.runId());
        }
        if (event.sourceId() != null) {
            return "source:" + event.sourceId();
        }
        return "project:" + event.projectId();
    }

    private String runStreamKey(String runId) {
        return "run:" + runId;
    }
}
