package com.researchassistant.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamEventEnvelopeTest {

    private final ApplicationContextRunner eventPublisherContextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EventPublisherContextConfiguration.class);

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRedisMocks() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(streamOperations.add(any(String.class), any(Map.class))).thenReturn(RecordId.of("1-0"));
    }

    @Test
    void exposesEveryF002EventTypeWithWireName() {
        assertThat(WorkbenchEventType.values())
                .extracting(WorkbenchEventType::wireName)
                .containsExactlyInAnyOrder(
                        "run.started",
                        "run.completed",
                        "run.failed",
                        "agent.plan.created",
                        "agent.step.started",
                        "agent.step.completed",
                        "retrieval.started",
                        "retrieval.completed",
                        "evidence.evaluated",
                        "answer.delta",
                        "answer.completed",
                        "candidate.created",
                        "knowledge.entry.created",
                        "source.status.changed",
                        "memory.flush.started",
                        "memory.flush.completed",
                        "feedback.applied"
                );
    }

    @Test
    void parsesEventTypeFromWireName() {
        assertThat(WorkbenchEventType.fromWireName("run.started"))
                .isEqualTo(WorkbenchEventType.RUN_STARTED);
        assertThat(WorkbenchEventType.fromWireName("answer.delta"))
                .isEqualTo(WorkbenchEventType.ANSWER_DELTA);
    }

    @Test
    void publishCompletesEnvelopeAndIncrementsSequenceWithinRun() {
        WorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();

        WorkbenchEvent first = publisher.publish(WorkbenchEvent.pending(
                WorkbenchEventType.RUN_STARTED,
                "project-1",
                "session-1",
                "run-1",
                "supervisor",
                Map.of("question", "What changed?")
        ));
        WorkbenchEvent second = publisher.publish(WorkbenchEvent.pending(
                WorkbenchEventType.AGENT_PLAN_CREATED,
                "project-1",
                "session-1",
                "run-1",
                "planner",
                Map.of("stepCount", 2)
        ));
        WorkbenchEvent otherRun = publisher.publish(WorkbenchEvent.pending(
                WorkbenchEventType.RUN_STARTED,
                "project-1",
                "session-1",
                "run-2",
                "supervisor",
                Map.of("question", "Other run")
        ));

        assertThat(first.eventId()).isNotBlank();
        assertThat(first.eventType()).isEqualTo(WorkbenchEventType.RUN_STARTED);
        assertThat(first.projectId()).isEqualTo("project-1");
        assertThat(first.sessionId()).isEqualTo("session-1");
        assertThat(first.runId()).isEqualTo("run-1");
        assertThat(first.actor()).isEqualTo("supervisor");
        assertThat(first.sequence()).isEqualTo(1);
        assertThat(first.createdAt()).isNotNull();
        assertThat(first.payload()).containsEntry("question", "What changed?");

        assertThat(second.sequence()).isEqualTo(2);
        assertThat(otherRun.sequence()).isEqualTo(1);
    }

    @Test
    void readRunEventsAfterSkipsAlreadyConsumedEvents() {
        WorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();

        WorkbenchEvent first = publisher.publish(WorkbenchEvent.pending(
                WorkbenchEventType.RUN_STARTED,
                "project-1",
                "session-1",
                "run-1",
                "supervisor",
                Map.of()
        ));
        WorkbenchEvent second = publisher.publish(WorkbenchEvent.pending(
                WorkbenchEventType.ANSWER_DELTA,
                "project-1",
                "session-1",
                "run-1",
                "answerer",
                new LinkedHashMap<>(Map.of("delta", "hello"))
        ));
        WorkbenchEvent third = publisher.publish(WorkbenchEvent.pending(
                WorkbenchEventType.ANSWER_COMPLETED,
                "project-1",
                "session-1",
                "run-1",
                "answerer",
                Map.of("answerId", "answer-1")
        ));

        List<WorkbenchEvent> afterFirst = publisher.readRunEventsAfter("run-1", first.eventId());
        List<WorkbenchEvent> fromBeginning = publisher.readRunEventsAfter("run-1", null);

        assertThat(afterFirst).extracting(WorkbenchEvent::eventId)
                .containsExactly(second.eventId(), third.eventId());
        assertThat(fromBeginning).extracting(WorkbenchEvent::eventId)
                .containsExactly(first.eventId(), second.eventId(), third.eventId());
        assertThat(second.payload()).containsEntry("delta", "hello");
    }

    @Test
    void redisAdapterStaysBehindPublisherPortAndIsNotDefaultBackend() {
        assertThat(WorkbenchEventRepository.class).isInterface();
        assertThat(WorkbenchEventPublisher.class).isAssignableFrom(RedisStreamWorkbenchEventPublisher.class);

        ConditionalOnProperty condition = RedisStreamWorkbenchEventPublisher.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).contains("app.events.backend");
        assertThat(condition.havingValue()).isEqualTo("redis");
    }

    @Test
    void missingBackendCreatesOnlyTheInMemoryPublisher() {
        eventPublisherContextRunner.run(context -> {
            assertThat(context).hasSingleBean(WorkbenchEventPublisher.class);
            assertThat(context).hasSingleBean(InMemoryWorkbenchEventPublisher.class);
            assertThat(context).doesNotHaveBean(RedisStreamWorkbenchEventPublisher.class);
            assertThat(context.getBean(WorkbenchEventPublisher.class))
                    .isInstanceOf(InMemoryWorkbenchEventPublisher.class);
        });
    }

    @Test
    void memoryBackendCreatesOnlyTheInMemoryPublisher() {
        eventPublisherContextRunner
                .withPropertyValues("app.events.backend=memory")
                .run(context -> {
                    assertThat(context).hasSingleBean(WorkbenchEventPublisher.class);
                    assertThat(context).hasSingleBean(InMemoryWorkbenchEventPublisher.class);
                    assertThat(context).doesNotHaveBean(RedisStreamWorkbenchEventPublisher.class);
                    assertThat(context.getBean(WorkbenchEventPublisher.class))
                            .isInstanceOf(InMemoryWorkbenchEventPublisher.class);
                });
    }

    @Test
    void redisBackendCreatesOnlyTheRedisStreamPublisher() {
        eventPublisherContextRunner
                .withPropertyValues("app.events.backend=redis")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(WorkbenchEventPublisher.class);
                    assertThat(context).hasSingleBean(RedisStreamWorkbenchEventPublisher.class);
                    assertThat(context).doesNotHaveBean(InMemoryWorkbenchEventPublisher.class);
                    assertThat(context.getBean(WorkbenchEventPublisher.class))
                            .isInstanceOf(RedisStreamWorkbenchEventPublisher.class);
                });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void redisAdapterStoresWireTypeJsonPayloadRedisSequenceAndAllScopedStreams() {
        when(valueOperations.increment("run:run-1:sequence")).thenReturn(42L);
        RedisStreamWorkbenchEventPublisher publisher = new RedisStreamWorkbenchEventPublisher(redisTemplate);

        WorkbenchEvent published = publisher.publish(new WorkbenchEvent(
                null,
                WorkbenchEventType.RUN_STARTED,
                "project-1",
                "session-1",
                "run-1",
                "supervisor",
                0,
                "source-1",
                null,
                null,
                null,
                Map.of("question", "why?", "stepCount", 2)
        ));

        assertThat(published.sequence()).isEqualTo(42);
        verify(valueOperations).increment("run:run-1:sequence");
        verify(streamOperations).add(eq("project:project-1:events"), any(Map.class));
        verify(streamOperations).add(eq("run:run-1:events"), any(Map.class));
        verify(streamOperations).add(eq("source:source-1:events"), any(Map.class));

        org.mockito.ArgumentCaptor<Map> fieldsCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(eq("run:run-1:events"), fieldsCaptor.capture());
        Map<String, String> fields = fieldsCaptor.getValue();
        assertThat(fields)
                .containsEntry("eventType", "run.started")
                .containsEntry("sequence", "42");
        assertThat(fields.get("payload")).contains("\"question\":\"why?\"", "\"stepCount\":2");
    }

    @Test
    void redisAdapterHydratesRunEventsAndSkipsLastEventId() {
        RedisStreamWorkbenchEventPublisher publisher = new RedisStreamWorkbenchEventPublisher(redisTemplate);
        when(streamOperations.read(StreamOffset.fromStart("run:run-1:events"))).thenReturn(List.of(
                redisRecord("run:run-1:events", "1-0", Map.of(
                        "eventId", "event-1",
                        "eventType", "run.started",
                        "projectId", "project-1",
                        "sessionId", "session-1",
                        "runId", "run-1",
                        "actor", "supervisor",
                        "sequence", "1",
                        "createdAt", "2026-05-09T12:00:00Z",
                        "payload", "{\"question\":\"why?\"}"
                )),
                redisRecord("run:run-1:events", "2-0", Map.of(
                        "eventId", "event-2",
                        "eventType", "answer.delta",
                        "projectId", "project-1",
                        "sessionId", "session-1",
                        "runId", "run-1",
                        "actor", "answerer",
                        "sequence", "2",
                        "createdAt", "2026-05-09T12:00:01Z",
                        "payload", "{\"delta\":\"hello\"}"
                ))
        ));

        List<WorkbenchEvent> events = publisher.readRunEventsAfter("run-1", "event-1");

        assertThat(events).hasSize(1);
        WorkbenchEvent event = events.get(0);
        assertThat(event.eventId()).isEqualTo("event-2");
        assertThat(event.eventType()).isEqualTo(WorkbenchEventType.ANSWER_DELTA);
        assertThat(event.payload()).containsEntry("delta", "hello");
    }

    private MapRecord<String, Object, Object> redisRecord(
            String streamKey,
            String recordId,
            Map<String, String> fields) {
        return MapRecord.<String, Object, Object>create(streamKey, new LinkedHashMap<>(fields))
                .withId(RecordId.of(recordId));
    }

    @Configuration
    @ComponentScan(
            basePackageClasses = WorkbenchEventPublisher.class,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            InMemoryWorkbenchEventPublisher.class,
                            RedisStreamWorkbenchEventPublisher.class
                    }),
            useDefaultFilters = false
    )
    static class EventPublisherContextConfiguration {
    }
}
