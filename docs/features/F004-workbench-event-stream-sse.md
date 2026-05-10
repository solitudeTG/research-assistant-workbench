---
id: F004
status: completed
owner: codex
updated: 2026-05-10
parent_feature: F002
---
# Workbench Event Backbone and SSE Projection

## Goal

Establish the backend process-event backbone for the F002 project workbench so Agent, retrieval, evidence, source status, candidate, knowledge-board, memory, answer, and feedback processes can share one documented event envelope and be projected to the frontend through SSE.

## Scope

- In scope: event envelope, event type enum, publisher/repository port, in-memory test/default backend, Redis Stream adapter, and `/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events` SSE projection.
- In scope: Redis Stream keys for project, run, and source event scopes.
- Out of scope: concrete Agent orchestration, source ingestion status-machine behavior, retrieval/evidence business logic, candidate confirmation, feedback scoring, and three-column UI implementation.

## Acceptance Criteria

- Events in the same `runId` receive monotonically increasing `sequence` values.
- SSE event `id` equals `eventId`.
- SSE event name equals the event type wire name.
- `Last-Event-ID` skips already consumed events.
- The frontend consumes only the SSE projection and does not read Redis directly.
- `mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest test` passes.

## Contract

- API: `GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events`.
- Events: F002 `run.*`, `agent.*`, `retrieval.*`, `evidence.*`, `answer.*`, `candidate.*`, `knowledge.*`, `source.*`, `memory.*`, and `feedback.*` wire names.
- Data: `WorkbenchEvent` envelope, `WorkbenchEventPublisher`/`WorkbenchEventRepository` port, in-memory backend, and Redis Stream records under `project:{projectId}:events`, `run:{runId}:events`, and `source:{sourceId}:events`.
- UI: SSE projection for later frontend process-timeline consumption.

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- Evidence: [EV-003-f004-workbench-event-stream-sse.md](../evidence/EV-003-f004-workbench-event-stream-sse.md)

## Completion Notes

- Implemented the event envelope, event wire-name contract, event publisher/repository port, in-memory default backend, Redis Stream backend, and project run SSE projection endpoint.
- Redis backend is property-gated with `app.events.backend=redis`; memory backend is the default and is explicitly selected by `app.events.backend=memory`.
- Verified SSE `id`, SSE event name, wire-name `eventType` data, Redis payload JSON hydration, Redis-backed sequence, scoped stream writes, and `Last-Event-ID` replay filtering.
- Known limitation: current SSE projection replays currently available run events and completes; continuous live tailing remains a follow-up concern for later run/UI slices, not a blocker for the verified F004 acceptance.

## Next Step

Proceed to F005 project-scoped source status machine. Do not extend F004 into source processing, Agent orchestration, evidence logic, knowledge board, feedback, or UI.
