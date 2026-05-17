---
id: F006
doc_kind: feature
status: completed
owner: codex
created: 2026-05-10
updated: 2026-05-10
parent_feature: F002
---
# Agent Run Event Flow

## Goal

Make project-scoped research questions observable as a Supervisor-led run. A project message creates a run, returns the stream metadata the workbench needs, and publishes the minimum process timeline through the existing F004 workbench event and SSE contract.

## Current Status

Completed for the F006 boundary. The project-scoped message endpoint wraps the existing Supervisor answer path, persists an `assistant_answer` row, emits the required ordered run events, and reuses the F004 SSE replay projection.

## Scope

- In scope: `POST /api/projects/{projectId}/sessions/{sessionId}/messages`.
- In scope: response fields `messageId`, `answerId`, `streamRunId`, and `sseUrl`.
- In scope: run-scoped events `run.started`, `agent.plan.created`, `agent.step.started`, `retrieval.started`, `retrieval.completed`, `evidence.evaluated`, `answer.delta`, `answer.completed`, and `run.completed`.
- In scope: stable actors for Supervisor, retrieval, evidence boundary, and writing events.
- Out of scope: F007 evidence persistence/modeling beyond `evidence.evaluated` metadata, F008 candidates/knowledge board, F009 feedback, F010 UI, and F004 live-tail SSE.

## Acceptance Criteria

- Project/session IDs come from the path, not the request body.
- A project message creates one project-scoped run response with stream metadata.
- `WorkbenchEventPublisher` observes the required event wire names in order for the returned `streamRunId`.
- `GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events` replays the same event names using the F004 SSE event format.
- Required Maven acceptance command passes while legacy `/api/chat` and `/api/chat/stream` tests remain green.

## Contract

- API: `POST /api/projects/{projectId}/sessions/{sessionId}/messages`.
- SSE: `GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events`.
- Events: `run.*`, `agent.plan.created`, `agent.step.started`, `retrieval.*`, `evidence.evaluated`, and `answer.*`.
- Data: `assistant_answer` receives the generated answer, answer mode, and evidence-state summary.

## Evidence

- [EV-005-f006-agent-run-event-flow.md](../evidence/EV-005-f006-agent-run-event-flow.md)

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- Evidence: [EV-005-f006-agent-run-event-flow.md](../evidence/EV-005-f006-agent-run-event-flow.md)

## Next Step

Continue with F007 retrieval and evidence boundary. Do not expand F006 into candidate confirmation, feedback, or UI.
