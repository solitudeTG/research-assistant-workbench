package com.researchassistant.events;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkbenchEvent(
        String eventId,
        WorkbenchEventType eventType,
        String projectId,
        String sessionId,
        String runId,
        String actor,
        long sequence,
        String sourceId,
        String answerId,
        String turnId,
        OffsetDateTime createdAt,
        Map<String, Object> payload
) {

    public WorkbenchEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static WorkbenchEvent pending(
            WorkbenchEventType eventType,
            String projectId,
            String sessionId,
            String runId,
            String actor,
            Map<String, Object> payload) {
        return new WorkbenchEvent(
                null,
                eventType,
                projectId,
                sessionId,
                runId,
                actor,
                0,
                null,
                null,
                null,
                null,
                payload == null ? Map.of() : new LinkedHashMap<>(payload)
        );
    }

    public WorkbenchEvent withPublishedEnvelope(String eventId, long sequence, OffsetDateTime createdAt) {
        return new WorkbenchEvent(
                eventId,
                eventType,
                projectId,
                sessionId,
                runId,
                actor,
                sequence,
                sourceId,
                answerId,
                turnId,
                createdAt,
                payload
        );
    }
}
