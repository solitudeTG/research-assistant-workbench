package com.researchassistant.orchestrator;

import com.researchassistant.events.InMemoryWorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTracePublisherTest {

    @Test
    void publishesCommonActorStepAndDataPayloadShapeWithImmutableNestedMaps() {
        InMemoryWorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();
        AgentTracePublisher trace = new AgentTracePublisher(publisher);
        AgentTraceContext context = new AgentTraceContext(
                "project-1",
                "session-1",
                "run-1",
                "msg-1",
                "ans-1"
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", "paper_rag");

        trace.publish(
                context,
                WorkbenchEventType.TOOL_CALLED,
                "retrieval_worker",
                "Retrieval step",
                "step_retrieval",
                "step_plan",
                "running",
                data
        );

        data.put("toolName", "mutated_after_publish");

        WorkbenchEvent event = publisher.readRunEventsAfter("run-1", null).get(0);
        assertThat(event.actor()).isEqualTo("retrieval_worker");
        assertThat(event.answerId()).isEqualTo("ans-1");
        assertThat(event.turnId()).isEqualTo("msg-1");
        assertThat(event.payload())
                .containsEntry("messageId", "msg-1")
                .containsEntry("answerId", "ans-1")
                .containsKeys("actor", "step", "data");

        Map<String, Object> actor = dataMap(event.payload().get("actor"));
        Map<String, Object> step = dataMap(event.payload().get("step"));
        Map<String, Object> eventData = dataMap(event.payload().get("data"));

        assertThat(actor)
                .containsEntry("agentId", "retrieval_worker")
                .containsEntry("agentRole", "retrieval_worker")
                .containsEntry("displayName", "Retrieval step");
        assertThat(step)
                .containsEntry("stepId", "step_retrieval")
                .containsEntry("parentStepId", "step_plan")
                .containsEntry("label", "Retrieval step")
                .containsEntry("status", "running");
        assertThat(eventData).containsEntry("toolName", "paper_rag");

        assertUnmodifiable(actor);
        assertUnmodifiable(step);
        assertUnmodifiable(eventData);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void assertUnmodifiable(Map<?, ?> payloadMap) {
        assertThatThrownBy(() -> ((Map) payloadMap).put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
