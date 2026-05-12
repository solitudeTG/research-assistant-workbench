---
id: EV-013
doc_kind: evidence
status: completed
created: 2026-05-12
updated: 2026-05-12
feature_ids: [F015]
---
# EV-013 F015 Agent Trace Live SSE

## Scope Verified

F015 Phase 1 upgrades the project run event path from replay-only SSE to replay plus live-tail, and adds a truthful Agent Trace contract over the current main-Agent tool loop.

Implemented capability:

- Added F015 trace event wire names: `run.cancelled`, `agent.step.completed`, `agent.step.failed`, `tool.called`, `tool.completed`, `tool.failed`, `retrieval.query.rewritten`, `retrieval.hit`, `memory.hit`, `memory.completed`, and `evidence.gap.detected`.
- Added terminal run-event helper for `run.completed`, `run.failed`, and `run.cancelled`.
- Added bounded wait read support to the event publisher interface, in-memory backend, and Redis Stream backend.
- Updated project run SSE to replay existing events, continue live-tail reads, send keepalive comments, honor `Last-Event-ID`, advance the cursor past filtered events, and complete after terminal run events.
- Added `AgentTraceContext` and `AgentTracePublisher` with common actor/step/data payload shape.
- Wired trace context through `ProjectAgentRequest`, `SupervisorService`, `DefaultProjectAgentToolLoop`, and `ProjectAgentTools`.
- Published best-effort tool trace events for `paper_rag`, `web_search`, and `memory_recall`; trace backend failure no longer breaks the main tool path.
- Published true backend detail events for memory hits, retrieval hits, conservative evidence gaps, and paragraph-sized answer deltas.
- Added frontend model folding for `agentTraces[runId]` so the UI can consume tools, timeline, retrieval hits, memory hits, evidence events, and answer deltas without directly reading Redis.

## Evidence

The commands below are the reproducible evidence for the implemented live SSE and trace projection behavior.

## Verification Commands

Backend focused verification:

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest,DefaultProjectAgentToolLoopTest,ProjectAgentToolsTest,ProjectAgentRoutingTest,ProjectEvidenceBoundaryTest,AgentTracePublisherTest' test
```

Result:

```text
BUILD SUCCESS
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
```

Frontend model verification:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
node --test src/main/resources/static/tests/workbench-model.test.mjs
```

Result:

```text
f002-workbench-model.test.mjs: 14/14 passing
workbench-model.test.mjs: 3/3 passing
```

Diff hygiene:

```powershell
git diff --check -- src/main/java/com/researchassistant/orchestrator/SupervisorService.java src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java
git diff --check -- src/main/resources/static/js/workbench-model.js src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Result: exit code `0`; only CRLF normalization warnings.

Harness validation:

```powershell
python scripts\knowledge_check.py
```

Result:

```text
knowledge_check: ok
```

## Review Evidence

Subagent-driven review loops were completed for the high-risk slices:

- Task 6 trace wiring: initial review caught missing `errorType` on `tool.failed` and trace backend coupling to the main tool path. Both were fixed and re-reviewed as passing.
- Task 7 detail events: initial review caught memory hit truncation and over-eager evidence-gap emission for no-tool/simple messages. Both were fixed, tested, and re-reviewed as passing.
- Task 8 frontend model folding: initial review caught missing test coverage for `tool.failed`, `memory.hit`, `memory.completed`, and default `agentTraces`; coverage was added and re-reviewed as passing.

## Residual Limits

- F015 Phase 1 does not implement a true parallel Supervisor-Worker runtime. Logical roles such as `retrieval_worker` and `memory_worker` are observable step labels over the current main-Agent tool loop.
- Provider token streaming is still not implemented. `answer.delta` is emitted as coarse paragraph deltas after the current answer is available.
- Redis Stream native blocking reads are not implemented. The Redis adapter uses bounded polling through the new wait seam.
- The initial EV-013 slice stopped at the tested `agentTraces` projection model. EV-014 adds the visual research process module that renders that model under assistant answers.
- Historical `SupervisorService` helper code from earlier routing work remains and should be cleaned in a separate focused refactor, not inside F015.

## Closeout Judgment

F015 Phase 1 is complete for live SSE semantics, backend trace publication, frontend model projection, and the follow-up research process UI recorded in EV-014. The system must still avoid claiming real parallel subagents until Phase 2 implements them.
