package com.researchassistant.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.events.backend", havingValue = "redis")
public class RedisStreamWorkbenchEventPublisher implements WorkbenchEventPublisher {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisStreamWorkbenchEventPublisher(StringRedisTemplate redisTemplate) {
        this(redisTemplate, new ObjectMapper());
    }

    RedisStreamWorkbenchEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkbenchEvent publish(WorkbenchEvent event) {
        long sequence = Objects.requireNonNull(
                redisTemplate.opsForValue().increment(sequenceKey(event.runId())),
                "Redis did not return a sequence value"
        );
        WorkbenchEvent published = event.withPublishedEnvelope(
                UUID.randomUUID().toString(),
                sequence,
                OffsetDateTime.now()
        );
        Map<String, String> fields = fieldsFor(published);
        redisTemplate.opsForStream().add(projectStreamKey(published.projectId()), fields);
        redisTemplate.opsForStream().add(runStreamKey(published.runId()), fields);
        if (published.sourceId() != null) {
            redisTemplate.opsForStream().add(sourceStreamKey(published.sourceId()), fields);
        }
        return published;
    }

    @Override
    public List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .read(StreamOffset.fromStart(runStreamKey(runId)));
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<WorkbenchEvent> events = records.stream()
                .map(MapRecord::getValue)
                .map(this::eventFromFields)
                .toList();
        if (lastEventId == null || lastEventId.isBlank()) {
            return events;
        }

        int lastSeenIndex = -1;
        for (int index = 0; index < events.size(); index++) {
            if (lastEventId.equals(events.get(index).eventId())) {
                lastSeenIndex = index;
                break;
            }
        }
        if (lastSeenIndex < 0) {
            return events;
        }
        return events.subList(lastSeenIndex + 1, events.size());
    }

    private Map<String, String> fieldsFor(WorkbenchEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIfPresent(fields, "eventId", event.eventId());
        putIfPresent(fields, "eventType", event.eventType().wireName());
        putIfPresent(fields, "projectId", event.projectId());
        putIfPresent(fields, "sessionId", event.sessionId());
        putIfPresent(fields, "runId", event.runId());
        putIfPresent(fields, "actor", event.actor());
        putIfPresent(fields, "sourceId", event.sourceId());
        putIfPresent(fields, "answerId", event.answerId());
        putIfPresent(fields, "turnId", event.turnId());
        putIfPresent(fields, "sequence", Long.toString(event.sequence()));
        putIfPresent(fields, "createdAt", event.createdAt().toString());
        putIfPresent(fields, "payload", payloadJson(event.payload()));
        return fields;
    }

    private WorkbenchEvent eventFromFields(Map<Object, Object> fields) {
        return new WorkbenchEvent(
                value(fields, "eventId"),
                WorkbenchEventType.fromWireName(value(fields, "eventType")),
                value(fields, "projectId"),
                value(fields, "sessionId"),
                value(fields, "runId"),
                value(fields, "actor"),
                Long.parseLong(value(fields, "sequence")),
                value(fields, "sourceId"),
                value(fields, "answerId"),
                value(fields, "turnId"),
                OffsetDateTime.parse(value(fields, "createdAt")),
                payloadFromJson(value(fields, "payload"))
        );
    }

    private String payloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workbench event payload must be JSON serializable", exception);
        }
    }

    private Map<String, Object> payloadFromJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Redis workbench event payload is not valid JSON", exception);
        }
    }

    private String value(Map<Object, Object> fields, String name) {
        Object value = fields.get(name);
        return value == null ? null : value.toString();
    }

    private void putIfPresent(Map<String, String> fields, String name, String value) {
        if (value != null) {
            fields.put(name, value);
        }
    }

    private String projectStreamKey(String projectId) {
        return "project:" + projectId + ":events";
    }

    private String runStreamKey(String runId) {
        return "run:" + runId + ":events";
    }

    private String sourceStreamKey(String sourceId) {
        return "source:" + sourceId + ":events";
    }

    private String sequenceKey(String runId) {
        return "run:" + runId + ":sequence";
    }
}
