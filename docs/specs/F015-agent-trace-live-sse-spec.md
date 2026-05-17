---
id: SPEC-F015
doc_kind: spec
status: completed
updated: 2026-05-12
feature_ids: [F015]
---
# F015 Agent Trace Event Contract and Live SSE Spec

## Purpose

F015 定义项目会话回答的 Agent Trace 合同。它把 F004/F006 的事件骨架升级为可直播的研究执行轨迹，让前端能够真实展示“研究过程、证据矩阵、技术轨迹”，而不是在回答完成后模拟过程。

核心原则：

- 事件必须来自真实后端步骤。
- 当前没有真实并发子 Agent 时，不得假装有并发 worker。
- Redis Stream 是运行期事件总线和短期恢复机制，不是长期审计数据库。
- UI 只消费 SSE 投影，不直接读取 Redis。

## Product Boundary

### In Scope

- Agent Trace event envelope。
- Live-tail SSE projection。
- `Last-Event-ID` replay + continue。
- Main Agent 工具调用、检索、记忆、证据、回答、候选知识的真实事件。
- 可由前端恢复出的 timeline、task tree、evidence matrix、technical trace 基础模型。
- Redis Stream backend 的 live read 语义。
- In-memory backend 的 focused test seam。

### Out of Scope

- 完整 Supervisor-Worker 并发 runtime。
- 长任务队列、暂停/恢复、人类 checkpoint。
- 独立 Python agent runtime。
- Redis Stream 长期归档。
- 新的 UI 大改。UI 可后续消费合同。

## Trace Envelope

所有 trace 事件沿用 `WorkbenchEvent` 的外层身份，并在 payload 中稳定表达 actor 与 step。

Required top-level fields already owned by the event backbone:

```json
{
  "eventId": "evt_01",
  "projectId": "project_01",
  "sessionId": "session_01",
  "runId": "run_01",
  "sequence": 12,
  "eventType": "retrieval.hit",
  "payload": {},
  "createdAt": "2026-05-11T10:00:00Z"
}
```

F015 payload common shape:

```json
{
  "messageId": "msg_01",
  "answerId": "ans_01",
  "actor": {
    "agentId": "agent_main",
    "agentRole": "main_agent",
    "displayName": "主 Agent"
  },
  "step": {
    "stepId": "step_retrieval",
    "parentStepId": "step_plan",
    "label": "资料库检索",
    "status": "running"
  },
  "data": {}
}
```

Rules:

- `sequence` remains monotonically increasing within `runId`.
- `eventId` remains the SSE `id`.
- `eventType` remains the SSE `event` name.
- `actor.agentRole` may be logical in Phase 1, but display names must not imply real parallel subagent runtime unless implemented.
- `step.parentStepId` is optional for flat events but required for task-tree rendering when a child step exists.

## Event Types

### Run Events

```text
run.started
run.completed
run.failed
run.cancelled
```

`run.started.data`:

```json
{
  "question": "当前资料库有哪些论文支持多 Agent 协作架构？",
  "mode": "project_message",
  "traceLevel": "summary"
}
```

`run.completed.data`:

```json
{
  "answerId": "ans_01",
  "durationMs": 8400,
  "terminal": true
}
```

`run.failed.data`:

```json
{
  "errorCode": "TOOL_TIMEOUT",
  "userMessage": "资料检索超时，已返回可用上下文。",
  "recoverable": true,
  "terminal": true
}
```

### Agent Events

```text
agent.plan.created
agent.step.started
agent.step.completed
agent.step.failed
```

`agent.plan.created.data`:

```json
{
  "intent": "PROJECT_RAG",
  "summary": "查找项目资料中支持多 Agent 协作架构的证据。",
  "steps": [
    {
      "stepId": "step_retrieval",
      "label": "资料库检索",
      "actorRole": "retrieval_worker"
    },
    {
      "stepId": "step_evidence",
      "label": "证据评估",
      "actorRole": "evidence_worker"
    }
  ]
}
```

### Tool Events

```text
tool.called
tool.completed
tool.failed
```

`tool.called.data`:

```json
{
  "toolName": "paper_rag",
  "toolDisplayName": "论文资料检索",
  "argumentsPreview": {
    "query": "多 Agent 协作架构 Supervisor Worker",
    "maxResults": 6
  }
}
```

`tool.completed.data`:

```json
{
  "toolName": "paper_rag",
  "durationMs": 1320,
  "resultSummary": "命中 6 篇资料，重排后保留 12 条证据。",
  "artifactIds": ["retrieval_batch_01"]
}
```

### Retrieval Events

```text
retrieval.started
retrieval.query.rewritten
retrieval.hit
retrieval.completed
```

`retrieval.query.rewritten.data`:

```json
{
  "originalQuery": "有哪些论文支持多 Agent 协作架构？",
  "rewrittenQueries": [
    "Supervisor Worker multi-agent architecture",
    "multi-agent task decomposition research assistant"
  ],
  "strategy": "llm_rewrite"
}
```

`retrieval.hit.data`:

```json
{
  "sourceType": "paper",
  "sourceId": "source_01",
  "title": "Supervisor-Worker Agents for Research Planning",
  "snippet": "The supervisor decomposes research goals into worker tasks...",
  "score": 0.87,
  "rank": 1,
  "retrievalMode": "vector"
}
```

`retrieval.completed.data`:

```json
{
  "retrievalMode": "HYBRID_RAG",
  "toolsUsed": ["paper_rag"],
  "paperEvidenceCount": 12,
  "webEvidenceCount": 0,
  "memoryRecallCount": 3,
  "sourceTypes": ["paper", "conversation_memory"],
  "coverage": "partial"
}
```

### Memory Events

```text
memory.hit
memory.completed
```

`memory.hit.data`:

```json
{
  "memoryLayer": "L2",
  "label": "全局认知摘要",
  "snippet": "本项目采用三层记忆：L1 短期会话、L2 全局认知、L3 长期沉淀。",
  "score": 0.78
}
```

Rules:

- L1/L2/L3 memory can inform continuity and retrieval expansion.
- Memory hits must not be counted as paper evidence.

### Evidence Events

```text
evidence.evaluated
evidence.gap.detected
```

`evidence.evaluated.data`:

```json
{
  "evidenceState": "WEAK",
  "outputMode": "LOCAL_WEAK_EVIDENCE",
  "citationCount": 12,
  "sourceTypes": ["paper"],
  "weakClaims": 1,
  "unsupportedClaims": 0,
  "requiresUserConfirmation": true
}
```

`evidence.gap.detected.data`:

```json
{
  "claim": "该架构能显著降低所有研究任务耗时。",
  "reason": "当前资料只支持复杂任务拆解，不支持所有任务耗时降低。",
  "severity": "medium"
}
```

### Answer Events

```text
answer.delta
answer.completed
```

`answer.delta.data`:

```json
{
  "delta": "当前资料库中有两类论文支持 Supervisor-Worker 协作架构：",
  "index": 3
}
```

`answer.completed.data`:

```json
{
  "answerId": "ans_01",
  "answerMode": "LOCAL_WEAK_EVIDENCE",
  "citationCount": 12,
  "candidateCount": 3
}
```

Rules:

- `answer.delta` must be emitted during answer generation when streaming is available.
- If the configured model provider cannot stream token deltas, backend may emit coarse paragraph deltas, but must not wait until all trace work is complete to emit only one final answer event.

### Candidate Events

```text
candidate.created
```

`candidate.created.data`:

```json
{
  "candidateId": "cand_01",
  "section": "core_concept",
  "title": "Supervisor-Worker 多 Agent 协作架构",
  "evidenceCount": 3,
  "status": "pending"
}
```

## SSE Live-Tail Semantics

Endpoint:

```text
GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events
```

Behavior:

1. Replay events with `eventId` greater than `Last-Event-ID`, or all currently available run events if no header is present.
2. If no terminal event has been emitted, keep the SSE connection open and continue reading new events.
3. Stop after `run.completed`, `run.failed`, or `run.cancelled`.
4. Send periodic keepalive comments while waiting, so intermediaries do not close idle connections.
5. Apply a bounded server-side timeout for abandoned runs and emit or expose a recoverable timeout state.

Terminal event detection:

```text
run.completed
run.failed
run.cancelled
```

## Redis Stream Semantics

Recommended keys:

```text
project:{projectId}:events
run:{runId}:events
source:{sourceId}:events
```

F015 focuses on `run:{runId}:events`.

Redis Stream responsibilities:

- Accept events from the main Agent and later worker processes.
- Provide ordered run-scoped reads for SSE live-tail.
- Support `Last-Event-ID` resume through event id mapping or stored envelope event id.
- Keep only short-term operational history according to configured retention.

Redis Stream non-responsibilities:

- Long-term audit retention.
- Final answer persistence.
- Knowledge board persistence.
- Evidence source persistence.

Long-term persistence remains in existing relational repositories. If durable trace replay becomes necessary, a later Feature should define a trace snapshot table rather than silently turning Redis into the audit database.

## Frontend Projection Model

The frontend should derive four display models from events:

### Summary Row

Fields:

- `stepsCompleted`
- `toolsUsed`
- `evidenceCount`
- `candidateCount`
- `weakClaims`
- `requiresConfirmation`

### Execution Timeline

Source events:

- `agent.plan.created`
- `agent.step.started`
- `agent.step.completed`
- `agent.step.failed`
- `tool.*`

### Evidence Matrix

Source events:

- `retrieval.hit`
- `retrieval.completed`
- `evidence.evaluated`
- `evidence.gap.detected`
- `candidate.created`

### Technical Trace

Source events:

- `actor`
- `step`
- `tool.*`
- `memory.hit`
- `retrieval.*`
- `run.completed`
- `run.failed`

Rules:

- UI may show logical workers in Phase 1, but labels should remain honest, for example “资料检索步骤” rather than “并发子 Agent” unless true worker runtime exists.
- UI should display raw JSON only in developer/debug affordances, not in the default research process surface.

## Error Handling

- Tool failure emits `tool.failed` and either continues with degraded context or ends with `run.failed`.
- Retrieval timeout emits `tool.failed` or `agent.step.failed` with `recoverable=true` when partial answer is possible.
- SSE disconnect does not cancel the run.
- Client reconnection uses `Last-Event-ID`.
- Duplicate events are ignored by `eventId`.
- Out-of-order delivery within one run is a backend bug; tests must enforce monotonic sequence.

## Testing Strategy

Backend focused tests:

- Event envelope serialization for all F015 event types.
- Monotonic per-run sequence with mixed event types.
- SSE live-tail emits new events after the connection opens.
- `Last-Event-ID` replays only missing events, then continues live-tail.
- Terminal events close the SSE stream.
- Redis backend can read run events after the current last event.
- In-memory backend supports deterministic live-tail tests.
- F013 tool loop publishes `tool.called`, `tool.completed`, `retrieval.*`, `memory.hit`, `evidence.evaluated`, and `answer.delta` where applicable.

Frontend/model tests:

- Events fold into a summary row.
- Events fold into timeline steps without duplicating repeated SSE event ids.
- Retrieval hits and evidence events fold into an evidence matrix.
- Phase 1 logical actors do not render as real parallel subagents.

Harness validation:

```text
python scripts/knowledge_check.py
```

## Acceptance Evidence

- Focused backend tests pass.
- Existing F004/F006/F013 acceptance tests remain green.
- Frontend model tests for trace folding pass when UI consumption is implemented.
- Browser/manual verification confirms the research process module updates incrementally from SSE when UI work lands.
- Harness validation passes for updated docs.
