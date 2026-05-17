---
id: PLAN-F015
doc_kind: plan
status: completed
updated: 2026-05-12
feature_ids: [F015]
---
# F015 Agent Trace Live SSE Implementation Plan

## Completion Note

Executed scope completed live-tail SSE, backend trace event publication, frontend model folding, and the follow-up visual research process module under assistant answers. The UI consumes `state.agentTraces[runId]` without claiming true parallel subagents.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade project run events from replay-only SSE into a real live Agent Trace stream that can honestly drive the session research-process UI.

**Architecture:** Keep the existing `WorkbenchEvent` envelope and project run SSE endpoint. Add a bounded “wait for new run events” capability to the event publisher, teach the SSE controller to replay then live-tail until a terminal run event, and publish trace events from the current main-Agent/tool path without pretending true parallel subagents exist.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC `SseEmitter`, Redis Stream via `StringRedisTemplate`, JUnit 5, Mockito, AssertJ, existing static frontend model tests with Node `--test`.

---

## File Structure

- Modify: `src/main/java/com/researchassistant/events/WorkbenchEventType.java`
- Modify: `src/main/java/com/researchassistant/events/WorkbenchEventPublisher.java`
- Modify: `src/main/java/com/researchassistant/events/InMemoryWorkbenchEventPublisher.java`
- Modify: `src/main/java/com/researchassistant/events/RedisStreamWorkbenchEventPublisher.java`
- Modify: `src/main/java/com/researchassistant/events/SseProjectionController.java`
- Create: `src/main/java/com/researchassistant/events/WorkbenchRunEventStream.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AgentTraceContext.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AgentTracePublisher.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/ProjectAgentRequest.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/DefaultProjectAgentToolLoop.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/ProjectAgentTools.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Modify: `src/main/resources/static/js/workbench-model.js`
- Modify: `src/main/resources/static/js/workbench-app.js`
- Test: `src/test/java/com/researchassistant/events/StreamEventEnvelopeTest.java`
- Test: `src/test/java/com/researchassistant/events/SseProjectionControllerTest.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectAgentToolsTest.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java`
- Test: `src/main/resources/static/tests/f002-workbench-model.test.mjs`
- Modify after implementation: `docs/evidence/EV-013-f015-agent-trace-live-sse.md`
- Modify after implementation: `docs/features/F015-agent-trace-live-sse.md`
- Modify after implementation: `docs/BACKLOG.md`

## Task 1: Event Contract Constants

**Files:**
- Modify: `src/main/java/com/researchassistant/events/WorkbenchEventType.java`
- Test: `src/test/java/com/researchassistant/events/StreamEventEnvelopeTest.java`

- [ ] **Step 1: Write the failing event-type contract test**

Add the new wire names to the existing `StreamEventEnvelopeTest` assertion:

```java
assertThat(WorkbenchEventType.values())
        .extracting(WorkbenchEventType::wireName)
        .contains(
                "run.cancelled",
                "agent.step.completed",
                "agent.step.failed",
                "tool.called",
                "tool.completed",
                "tool.failed",
                "retrieval.query.rewritten",
                "retrieval.hit",
                "memory.hit",
                "memory.completed",
                "evidence.gap.detected"
        );

assertThat(WorkbenchEventType.fromWireName("tool.called"))
        .isEqualTo(WorkbenchEventType.TOOL_CALLED);
assertThat(WorkbenchEventType.fromWireName("retrieval.hit"))
        .isEqualTo(WorkbenchEventType.RETRIEVAL_HIT);
```

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest test
```

Expected: compile failure because the new enum constants do not exist.

- [ ] **Step 2: Add the minimal enum constants**

Update `WorkbenchEventType`:

```java
RUN_CANCELLED("run.cancelled"),
AGENT_STEP_COMPLETED("agent.step.completed"),
AGENT_STEP_FAILED("agent.step.failed"),
TOOL_CALLED("tool.called"),
TOOL_COMPLETED("tool.completed"),
TOOL_FAILED("tool.failed"),
RETRIEVAL_QUERY_REWRITTEN("retrieval.query.rewritten"),
RETRIEVAL_HIT("retrieval.hit"),
MEMORY_HIT("memory.hit"),
MEMORY_COMPLETED("memory.completed"),
EVIDENCE_GAP_DETECTED("evidence.gap.detected"),
```

Keep existing constants and wire names unchanged.

- [ ] **Step 3: Run the contract test**

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest test
```

Expected: PASS.

## Task 2: Run Event Stream Helper

**Files:**
- Create: `src/main/java/com/researchassistant/events/WorkbenchRunEventStream.java`
- Test: `src/test/java/com/researchassistant/events/StreamEventEnvelopeTest.java`

- [ ] **Step 1: Write the failing terminal-event test**

Add:

```java
@Test
void terminalRunEventsAreExplicit() {
    assertThat(WorkbenchRunEventStream.isTerminal(WorkbenchEventType.RUN_COMPLETED)).isTrue();
    assertThat(WorkbenchRunEventStream.isTerminal(WorkbenchEventType.RUN_FAILED)).isTrue();
    assertThat(WorkbenchRunEventStream.isTerminal(WorkbenchEventType.RUN_CANCELLED)).isTrue();
    assertThat(WorkbenchRunEventStream.isTerminal(WorkbenchEventType.ANSWER_COMPLETED)).isFalse();
    assertThat(WorkbenchRunEventStream.isTerminal(WorkbenchEventType.RETRIEVAL_COMPLETED)).isFalse();
}
```

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest#terminalRunEventsAreExplicit test
```

Expected: compile failure because `WorkbenchRunEventStream` does not exist.

- [ ] **Step 2: Add the helper**

Create:

```java
package com.researchassistant.events;

public final class WorkbenchRunEventStream {

    private WorkbenchRunEventStream() {
    }

    public static boolean isTerminal(WorkbenchEventType eventType) {
        return eventType == WorkbenchEventType.RUN_COMPLETED
                || eventType == WorkbenchEventType.RUN_FAILED
                || eventType == WorkbenchEventType.RUN_CANCELLED;
    }
}
```

- [ ] **Step 3: Run the helper test**

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest#terminalRunEventsAreExplicit test
```

Expected: PASS.

## Task 3: Blocking Read Seam For Live SSE

**Files:**
- Modify: `src/main/java/com/researchassistant/events/WorkbenchEventPublisher.java`
- Modify: `src/main/java/com/researchassistant/events/InMemoryWorkbenchEventPublisher.java`
- Modify: `src/main/java/com/researchassistant/events/RedisStreamWorkbenchEventPublisher.java`
- Test: `src/test/java/com/researchassistant/events/StreamEventEnvelopeTest.java`

- [ ] **Step 1: Write the failing in-memory live-read test**

Add:

```java
@Test
void inMemoryPublisherWaitsForRunEventsPublishedAfterTheReadStarts() throws Exception {
    InMemoryWorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
        Future<List<WorkbenchEvent>> future = executor.submit(() ->
                publisher.readRunEventsAfter("run-live", null, Duration.ofSeconds(1))
        );

        Thread.sleep(100);
        WorkbenchEvent published = publisher.publish(WorkbenchEvent.pending(
                WorkbenchEventType.RUN_STARTED,
                "project-1",
                "session-1",
                "run-live",
                "supervisor",
                Map.of("question", "why?")
        ));

        assertThat(future.get(2, TimeUnit.SECONDS))
                .extracting(WorkbenchEvent::eventId)
                .containsExactly(published.eventId());
    } finally {
        executor.shutdownNow();
    }
}
```

Add imports:

```java
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
```

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest#inMemoryPublisherWaitsForRunEventsPublishedAfterTheReadStarts test
```

Expected: compile failure because `readRunEventsAfter(String, String, Duration)` does not exist.

- [ ] **Step 2: Extend the publisher interface**

Modify `WorkbenchEventPublisher`:

```java
import java.time.Duration;
import java.util.List;

public interface WorkbenchEventPublisher {

    WorkbenchEvent publish(WorkbenchEvent event);

    List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId);

    default List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId, Duration wait) {
        return readRunEventsAfter(runId, lastEventId);
    }
}
```

- [ ] **Step 3: Implement in-memory wait/notify**

In `InMemoryWorkbenchEventPublisher.publish(...)`, call `notifyAll()` after adding the event.

Override the new method:

```java
@Override
public synchronized List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId, Duration wait) {
    List<WorkbenchEvent> current = readRunEventsAfter(runId, lastEventId);
    if (!current.isEmpty() || wait == null || wait.isZero() || wait.isNegative()) {
        return current;
    }
    long deadline = System.currentTimeMillis() + wait.toMillis();
    while (current.isEmpty()) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            return List.of();
        }
        try {
            wait(remaining);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        current = readRunEventsAfter(runId, lastEventId);
    }
    return current;
}
```

Ensure the synchronized method calls the existing synchronized `readRunEventsAfter` reentrantly.

- [ ] **Step 4: Implement bounded Redis polling seam**

In `RedisStreamWorkbenchEventPublisher`, override the new method with a bounded wait loop:

```java
@Override
public List<WorkbenchEvent> readRunEventsAfter(String runId, String lastEventId, Duration wait) {
    List<WorkbenchEvent> current = readRunEventsAfter(runId, lastEventId);
    if (!current.isEmpty() || wait == null || wait.isZero() || wait.isNegative()) {
        return current;
    }
    long deadline = System.nanoTime() + wait.toNanos();
    while (System.nanoTime() < deadline) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        current = readRunEventsAfter(runId, lastEventId);
        if (!current.isEmpty()) {
            return current;
        }
    }
    return List.of();
}
```

This keeps Phase 1 simple and truthful. A future optimization may map Workbench `eventId` to Redis stream IDs for native blocking reads.

- [ ] **Step 5: Run event tests**

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest test
```

Expected: PASS.

## Task 4: Live-Tail SSE Projection

**Files:**
- Modify: `src/main/java/com/researchassistant/events/SseProjectionController.java`
- Test: `src/test/java/com/researchassistant/events/SseProjectionControllerTest.java`

- [ ] **Step 1: Write the failing live-tail controller test**

Add a test with a fake publisher that returns one replay event, then one delayed terminal event from the duration overload:

```java
@Test
void sseReplaysExistingEventsThenContinuesUntilTerminalRunEvent() throws Exception {
    when(publisher.readRunEventsAfter(eq("run-1"), isNull()))
            .thenReturn(List.of(event("event-1", WorkbenchEventType.RUN_STARTED, 1, Map.of("question", "why?"))));
    when(publisher.readRunEventsAfter(eq("run-1"), eq("event-1"), any(Duration.class)))
            .thenReturn(List.of(event("event-2", WorkbenchEventType.ANSWER_DELTA, 2, Map.of("delta", "hello"))));
    when(publisher.readRunEventsAfter(eq("run-1"), eq("event-2"), any(Duration.class)))
            .thenReturn(List.of(event("event-3", WorkbenchEventType.RUN_COMPLETED, 3, Map.of("status", "completed"))));

    mockMvc.perform(get("/api/projects/project-1/sessions/session-1/runs/run-1/events")
                    .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("event:run.started")))
            .andExpect(content().string(containsString("event:answer.delta")))
            .andExpect(content().string(containsString("event:run.completed")));
}
```

Add imports:

```java
import java.time.Duration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
```

Run:

```powershell
mvn -Dtest=SseProjectionControllerTest#sseReplaysExistingEventsThenContinuesUntilTerminalRunEvent test
```

Expected: FAIL because the controller currently completes after replay.

- [ ] **Step 2: Change controller loop to replay then live-tail**

In `SseProjectionController`, replace the single `for` loop + immediate `complete()` with:

```java
String cursor = lastEventId;
boolean terminal = false;
for (WorkbenchEvent event : eventPublisher.readRunEventsAfter(runId, cursor)) {
    if (belongsToPath(event, projectId, sessionId, runId)) {
        sendEvent(emitter, event);
        cursor = event.eventId();
        terminal = WorkbenchRunEventStream.isTerminal(event.eventType());
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
        if (belongsToPath(event, projectId, sessionId, runId)) {
            sendEvent(emitter, event);
            cursor = event.eventId();
            terminal = WorkbenchRunEventStream.isTerminal(event.eventType());
        }
    }
}
emitter.complete();
```

Extract:

```java
private void sendEvent(SseEmitter emitter, WorkbenchEvent event) throws IOException {
    emitter.send(SseEmitter.event()
            .id(event.eventId())
            .name(event.eventType().wireName())
            .data(sseData(event)));
}
```

- [ ] **Step 3: Preserve Last-Event-ID behavior**

Add or update a controller test proving `Last-Event-ID: event-1` skips `event-1`, sends later replay events, and then live-tails to terminal.

Run:

```powershell
mvn -Dtest=SseProjectionControllerTest test
```

Expected: PASS.

## Task 5: Trace Context and Publisher

**Files:**
- Create: `src/main/java/com/researchassistant/orchestrator/AgentTraceContext.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AgentTracePublisher.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java`

- [ ] **Step 1: Write a failing trace payload test**

In `ProjectRunEventFlowTest`, add a test that publishes a tool event through `AgentTracePublisher` and asserts the common payload shape:

```java
@Test
void agentTracePublisherUsesCommonActorAndStepPayloadShape() {
    InMemoryWorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();
    AgentTracePublisher trace = new AgentTracePublisher(publisher);
    AgentTraceContext context = new AgentTraceContext(
            "project-1",
            "session-1",
            "run-1",
            "msg-1",
            "ans-1"
    );

    trace.publish(
            context,
            WorkbenchEventType.TOOL_CALLED,
            "retrieval_worker",
            "资料检索步骤",
            "step_retrieval",
            "step_plan",
            "running",
            Map.of("toolName", "paper_rag")
    );

    WorkbenchEvent event = publisher.readRunEventsAfter("run-1", null).get(0);
    assertThat(event.actor()).isEqualTo("retrieval_worker");
    assertThat(event.payload()).containsEntry("messageId", "msg-1");
    assertThat(event.payload()).containsEntry("answerId", "ans-1");
    assertThat(event.payload()).containsKey("actor");
    assertThat(event.payload()).containsKey("step");
    assertThat((Map<?, ?>) event.payload().get("data")).containsEntry("toolName", "paper_rag");
}
```

Expected: compile failure because the new classes do not exist.

- [ ] **Step 2: Add `AgentTraceContext`**

Create:

```java
package com.researchassistant.orchestrator;

public record AgentTraceContext(
        String projectId,
        String sessionId,
        String runId,
        String messageId,
        String answerId
) {
}
```

- [ ] **Step 3: Add `AgentTracePublisher`**

Create:

```java
package com.researchassistant.orchestrator;

import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentTracePublisher {

    private final WorkbenchEventPublisher eventPublisher;

    public AgentTracePublisher(WorkbenchEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public WorkbenchEvent publish(
            AgentTraceContext context,
            WorkbenchEventType eventType,
            String agentRole,
            String displayName,
            String stepId,
            String parentStepId,
            String status,
            Map<String, Object> data) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("agentId", agentRole);
        actor.put("agentRole", agentRole);
        actor.put("displayName", displayName);

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stepId", stepId);
        if (parentStepId != null && !parentStepId.isBlank()) {
            step.put("parentStepId", parentStepId);
        }
        step.put("label", displayName);
        step.put("status", status);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", context.messageId());
        payload.put("answerId", context.answerId());
        payload.put("actor", actor);
        payload.put("step", step);
        payload.put("data", data == null ? Map.of() : new LinkedHashMap<>(data));

        return eventPublisher.publish(new WorkbenchEvent(
                null,
                eventType,
                context.projectId(),
                context.sessionId(),
                context.runId(),
                agentRole,
                0,
                null,
                context.answerId(),
                context.messageId(),
                null,
                payload
        ));
    }
}
```

- [ ] **Step 4: Run the trace test**

Run:

```powershell
mvn -Dtest=ProjectRunEventFlowTest#agentTracePublisherUsesCommonActorAndStepPayloadShape test
```

Expected: PASS.

## Task 6: Wire Trace Context Into Main Agent Tool Loop

**Files:**
- Modify: `src/main/java/com/researchassistant/orchestrator/ProjectAgentRequest.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/DefaultProjectAgentToolLoop.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/ProjectAgentTools.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectAgentToolsTest.java`

- [ ] **Step 1: Write failing tool trace tests**

In `ProjectAgentToolsTest`, update the helper to pass an `AgentTraceContext` and `AgentTracePublisher` backed by `InMemoryWorkbenchEventPublisher`. Add:

```java
@Test
void paperRagToolPublishesCalledAndCompletedTraceEvents() {
    InMemoryWorkbenchEventPublisher publisher = new InMemoryWorkbenchEventPublisher();
    ProjectAgentTools tools = tools(new ProjectEvidenceScope(List.of(101L), Map.of(101L, "source-101")), publisher);

    tools.paperRag("multi agent", 5);

    assertThat(publisher.readRunEventsAfter("run-trace", null))
            .extracting(event -> event.eventType().wireName())
            .contains("tool.called", "tool.completed");
}
```

Expected: compile failure until constructors and request carry trace context.

- [ ] **Step 2: Extend `ProjectAgentRequest`**

Add fields:

```java
String projectId,
String sessionId,
String runId,
String messageId,
String answerId
```

If `ProjectAgentRequest` is a record, place these before question so call sites make ownership explicit.

- [ ] **Step 3: Pass trace context from `SupervisorService`**

When building `ProjectAgentRequest`, pass:

```java
projectId,
sessionId,
runId,
messageId,
answerId,
request.question(),
memory,
globalKnowledgeService.snapshot(),
evidenceScope,
allowWebSupplement
```

- [ ] **Step 4: Inject `AgentTracePublisher` into `DefaultProjectAgentToolLoop`**

Add constructor dependency:

```java
private final AgentTracePublisher tracePublisher;
```

Create context in `run(...)`:

```java
AgentTraceContext traceContext = new AgentTraceContext(
        request.projectId(),
        request.sessionId(),
        request.runId(),
        request.messageId(),
        request.answerId()
);
```

Pass it to `ProjectAgentTools`.

- [ ] **Step 5: Publish tool events in `ProjectAgentTools`**

Wrap each public tool method with:

```java
tracePublisher.publish(
        traceContext,
        WorkbenchEventType.TOOL_CALLED,
        "retrieval_worker",
        "资料检索步骤",
        "step_retrieval",
        "step_plan",
        "running",
        Map.of("toolName", "paper_rag", "query", query, "maxResults", maxResults)
);
```

After success:

```java
tracePublisher.publish(
        traceContext,
        WorkbenchEventType.TOOL_COMPLETED,
        "retrieval_worker",
        "资料检索步骤",
        "step_retrieval",
        "step_plan",
        "completed",
        Map.of("toolName", "paper_rag", "resultSummary", "paper_rag completed")
);
```

On exception, publish `TOOL_FAILED` with `recoverable=true` when the existing tool path degrades instead of throwing.

- [ ] **Step 6: Run tool tests**

Run:

```powershell
mvn -Dtest=ProjectAgentToolsTest,DefaultProjectAgentToolLoopTest test
```

Expected: PASS after constructor updates.

## Task 7: Retrieval, Memory, Evidence, and Answer Trace Events

**Files:**
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java`
- Test: `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`

- [ ] **Step 1: Write failing ordered trace test**

In `ProjectRunEventFlowTest`, extend the project message test to expect the new trace wire names:

```java
assertThat(events)
        .extracting(event -> event.eventType().wireName())
        .containsSubsequence(
                "run.started",
                "agent.plan.created",
                "agent.step.started",
                "retrieval.started",
                "retrieval.completed",
                "evidence.evaluated",
                "answer.delta",
                "answer.completed",
                "run.completed"
        );
```

Add a separate assertion for optional detail events when tools are used:

```java
assertThat(events)
        .extracting(event -> event.eventType().wireName())
        .contains("tool.called", "tool.completed");
```

- [ ] **Step 2: Add memory hit events after agent run**

In `SupervisorService.answerProject(...)`, after `memoryRecallResult` is available, publish one `MEMORY_HIT` event per returned memory item when the result is non-empty. Use actor `memory_worker`, step `step_memory`, parent `step_plan`.

Payload data:

```java
Map.of(
        "memoryLayer", "L3",
        "label", "长期记忆召回",
        "snippet", bounded(memoryText),
        "score", memoryScore
)
```

If the existing `MemoryRecallResult` does not expose scores, omit `score` rather than inventing one.

- [ ] **Step 3: Add retrieval hit events from paper/web results**

After `ragResult` and `webSearchResult` are known, publish `RETRIEVAL_HIT` events for the top bounded hits:

```java
payload(
        "sourceType", "paper",
        "sourceId", evidenceScope.sourceIdByIndexedDocumentId().get(chunk.documentId()),
        "title", chunkTitle,
        "snippet", bounded(chunk.text()),
        "score", chunk.score(),
        "rank", rank,
        "retrievalMode", "vector"
)
```

For web hits, use `sourceType=web`, URL/title/provider metadata, and rank.

- [ ] **Step 4: Add evidence gap event for weak evidence**

When `assessment.evidenceLevel()` is weak or citation count is zero for a research request, publish `EVIDENCE_GAP_DETECTED`:

```java
payload(
        "claim", "当前回答存在证据边界",
        "reason", "本轮回答缺少足够本地论文证据或只获得弱证据。",
        "severity", "medium"
)
```

Keep this event conservative. Do not generate per-claim hallucination labels unless the backend has actual claim-level evidence analysis.

- [ ] **Step 5: Split answer delta into coarse chunks**

Replace the single full-answer `ANSWER_DELTA` payload with paragraph-sized deltas when provider streaming is unavailable:

```java
for (String delta : answerDeltas(answer)) {
    publishRunEvent(
            WorkbenchEventType.ANSWER_DELTA,
            projectId,
            sessionId,
            runId,
            "writing-agent",
            answerId,
            payload("delta", delta)
    );
}
```

Add helper:

```java
private List<String> answerDeltas(String answer) {
    if (answer == null || answer.isBlank()) {
        return List.of("");
    }
    return Arrays.stream(answer.split("\\n\\s*\\n"))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();
}
```

If existing frontend still expects `text`, include both keys for compatibility:

```java
payload("delta", delta, "text", delta)
```

- [ ] **Step 6: Run focused flow tests**

Run:

```powershell
mvn -Dtest=ProjectRunEventFlowTest,ProjectEvidenceBoundaryTest test
```

Expected: PASS.

## Task 8: Frontend Trace Folding Model

**Files:**
- Modify: `src/main/resources/static/js/workbench-model.js`
- Modify: `src/main/resources/static/tests/f002-workbench-model.test.mjs`

- [ ] **Step 1: Write failing model test for trace folding**

Add:

```javascript
test("agent trace events fold into research process summary", () => {
  const state = createWorkbenchState({
    selectedSessionId: "session-1",
    sessions: [{ id: "session-1", title: "会话1", updatedAt: "刚刚", messages: [] }],
  });

  applyWorkbenchEvent(state, {
    eventId: "evt-1",
    eventType: "tool.called",
    runId: "run-1",
    answerId: "ans-1",
    payload: { data: { toolName: "paper_rag" } },
  });
  applyWorkbenchEvent(state, {
    eventId: "evt-2",
    eventType: "retrieval.hit",
    runId: "run-1",
    answerId: "ans-1",
    payload: { data: { sourceType: "paper", title: "Agent Paper", score: 0.87 } },
  });
  applyWorkbenchEvent(state, {
    eventId: "evt-3",
    eventType: "evidence.evaluated",
    runId: "run-1",
    answerId: "ans-1",
    payload: { citationCount: 1, weakClaims: 1, requiresUserConfirmation: true },
  });

  const trace = state.agentTraces["run-1"];
  assert.equal(trace.tools.length, 1);
  assert.equal(trace.retrievalHits.length, 1);
  assert.equal(trace.summary.evidenceCount, 1);
  assert.equal(trace.summary.requiresConfirmation, true);
});
```

Run:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Expected: FAIL because `agentTraces` folding does not exist.

- [ ] **Step 2: Add trace state**

In `createWorkbenchState`, add:

```javascript
agentTraces: {},
```

In the SSE event reducer, initialize:

```javascript
function ensureAgentTrace(state, runId) {
  if (!state.agentTraces[runId]) {
    state.agentTraces[runId] = {
      tools: [],
      timeline: [],
      retrievalHits: [],
      evidenceEvents: [],
      memoryHits: [],
      answerDeltas: [],
      summary: {
        toolCount: 0,
        evidenceCount: 0,
        weakClaims: 0,
        requiresConfirmation: false,
      },
    };
  }
  return state.agentTraces[runId];
}
```

- [ ] **Step 3: Fold event types**

Handle:

```javascript
if (eventType === "tool.called" || eventType === "tool.completed" || eventType === "tool.failed") {
  trace.tools.push(payload.data || payload);
  trace.summary.toolCount = trace.tools.length;
}

if (eventType === "retrieval.hit") {
  trace.retrievalHits.push(payload.data || payload);
  trace.summary.evidenceCount = trace.retrievalHits.length;
}

if (eventType === "memory.hit") {
  trace.memoryHits.push(payload.data || payload);
}

if (eventType === "evidence.evaluated" || eventType === "evidence.gap.detected") {
  trace.evidenceEvents.push(payload.data || payload);
  trace.summary.weakClaims += Number(payload.weakClaims || payload.data?.weakClaims || 0);
  trace.summary.requiresConfirmation ||= Boolean(payload.requiresUserConfirmation || payload.data?.requiresUserConfirmation);
}
```

Keep duplicate event id filtering unchanged.

- [ ] **Step 4: Run frontend tests**

Run:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
node --test src/main/resources/static/tests/workbench-model.test.mjs
```

Expected: PASS.

## Task 9: Minimal UI Consumption

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/css/workbench.css`
- Modify: `src/main/resources/static/js/workbench-app.js`

- [ ] **Step 1: Add static module shell under AI answer**

Add a reusable container in the answer rendering path:

```html
<section class="research-process" data-research-process>
  <button class="research-process__summary" type="button" data-trace-toggle>
    研究过程 · 等待事件
  </button>
  <div class="research-process__body" hidden>
    <div data-trace-timeline></div>
    <div data-trace-evidence-matrix></div>
  </div>
</section>
```

- [ ] **Step 2: Render trace summary from `state.agentTraces[runId]`**

In `workbench-app.js`, derive:

```javascript
const summary = trace?.summary || {};
summaryButton.textContent = `研究过程 · 工具 ${summary.toolCount || 0} · 证据 ${summary.evidenceCount || 0} · 弱证据 ${summary.weakClaims || 0}`;
```

- [ ] **Step 3: Render timeline and evidence matrix without technical overclaiming**

For Phase 1, label logical workers as “步骤”:

```javascript
timeline.textContent = "规划步骤 / 资料检索步骤 / 证据评估步骤 / 回答生成步骤";
```

Do not render “并发子 Agent” unless later backend events prove real worker runtime.

- [ ] **Step 4: Run browser check**

Start the static server:

```powershell
Start-Process -WindowStyle Hidden node -ArgumentList "-e", "require('http').createServer((req,res)=>require('fs').createReadStream('src/main/resources/static/index.html').pipe(res)).listen(4173)"
```

Then verify manually or with Playwright:

- Session view still loads.
- Sending a message causes research process summary to update from SSE.
- Switching away and back does not duplicate trace events.
- Sources/Knowledge workspaces remain separate from the session UI.

## Task 10: Evidence and Closeout

**Files:**
- Create: `docs/evidence/EV-013-f015-agent-trace-live-sse.md`
- Modify: `docs/features/F015-agent-trace-live-sse.md`
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Run focused backend tests**

Run:

```powershell
mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest,ProjectRunEventFlowTest,ProjectAgentToolsTest,DefaultProjectAgentToolLoopTest test
```

Expected: PASS.

- [ ] **Step 2: Run frontend tests**

Run:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
node --test src/main/resources/static/tests/workbench-model.test.mjs
```

Expected: PASS.

- [ ] **Step 3: Run Harness validation**

Run:

```powershell
python scripts/knowledge_check.py
```

Expected: `knowledge_check: ok`.

- [ ] **Step 4: Write Evidence**

Create `EV-013-f015-agent-trace-live-sse.md` with:

- commands run and results,
- implemented behavior,
- screenshots or browser notes if UI consumption lands,
- residual limitations, especially “no true parallel Supervisor-Worker runtime in Phase 1”.

- [ ] **Step 5: Update Feature and Backlog**

Set F015 status according to actual completion:

- `completed` only if live SSE, trace events, and minimal frontend folding are all verified.
- `active` if implementation stops after backend contract or live SSE only.

Update `docs/BACKLOG.md` so future sessions can recover the exact next step.

- [ ] **Step 6: Run final diff checks**

Run:

```powershell
git diff --check
python scripts/knowledge_check.py
```

Expected: both pass.

## Self-Review Notes

- Spec coverage: the plan covers event contract, live-tail SSE, Last-Event-ID continuation, Redis/in-memory backend seams, main Agent trace events, frontend folding, UI honesty, tests, and Harness closeout.
- Intent guardrail: Phase 1 must not label logical steps as real parallel subagents. Real Supervisor-Worker concurrency remains a later Feature.
- Implementation risk: Redis Stream native blocking read is intentionally not required in this first plan because current event IDs are application UUIDs, not Redis stream IDs. Bounded wait polling is acceptable for Phase 1 and can be optimized later without changing the SSE/UI contract.
