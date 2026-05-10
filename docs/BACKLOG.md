# Work Backlog

This file records active engineering state that future sessions must be able to recover. It is not an unlimited wishlist.

## Active Work

### F002 Next-Generation Research Workbench

- Status: Epic in progress, split into F003-F011 child Features.
- Feature page: [F002-next-generation-research-workbench.md](features/F002-next-generation-research-workbench.md)
- Current intent: execute the child Features sequentially with one recoverable commit per Feature.
- Completed slices: F003 project workbench model API; F004 workbench event stream and SSE projection; F005 project-scoped source status machine; F006 agent run event flow; F007 retrieval evidence boundary.
- Next step: start F008 candidate confirmation and knowledge board. Do not implement feedback scoring, broad source search UI, or F010 live timeline inside F008.

## Recently Completed

### F007 Retrieval Evidence Boundary

- Status: completed
- Feature page: [F007-retrieval-evidence-boundary.md](features/F007-retrieval-evidence-boundary.md)
- Evidence: [EV-006-f007-retrieval-evidence-boundary.md](evidence/EV-006-f007-retrieval-evidence-boundary.md)
- Result: project answers now layer L3 memory as context, Paper RAG as current evidence, and web supplement as an output boundary; persist paper evidence rows; and publish real retrieval/evidence state in `retrieval.completed` and `evidence.evaluated`.
- Known limitation: weak evidence can select `WEB_SUPPLEMENT`, but external web retrieval is not implemented in this slice; non-empty `sourceFilters` remain guarded until project sources map to indexed chunks.

### F006 Agent Run Event Flow

- Status: completed
- Feature page: [F006-agent-run-event-flow.md](features/F006-agent-run-event-flow.md)
- Evidence: [EV-005-f006-agent-run-event-flow.md](evidence/EV-005-f006-agent-run-event-flow.md)
- Result: project-scoped message runs now return `messageId`, `answerId`, `streamRunId`, and `sseUrl`; publish the required ordered run events; persist an `assistant_answer`; and replay through the existing F004 SSE projection.
- Known limitation: evidence is only summarized in the `evidence.evaluated` event from current response metadata; full retrieval/evidence boundary persistence remains F007.

### F005 Project-Scoped Source Status Machine

- Status: completed
- Feature page: [F005-project-source-status-machine.md](features/F005-project-source-status-machine.md)
- Evidence: [EV-004-f005-project-source-status-machine.md](evidence/EV-004-f005-project-source-status-machine.md)
- Result: project-scoped source import/list/get/retry endpoints, PDF/note and web status chains, concrete failure stage persistence, retry status events, and source-scoped `source.status.changed` publishing are implemented and verified.
- Known limitation: this slice records deterministic source pipeline stages only; Agent orchestration, retrieval evidence boundaries, candidates, feedback, and UI remain later F002 child Features.

### F004 Workbench Event Backbone and SSE Projection

- Status: completed
- Feature page: [F004-workbench-event-stream-sse.md](features/F004-workbench-event-stream-sse.md)
- Evidence: [EV-003-f004-workbench-event-stream-sse.md](evidence/EV-003-f004-workbench-event-stream-sse.md)
- Result: event envelope, wire-name event enum, in-memory default backend, Redis Stream adapter, and project run SSE replay projection are implemented and verified with `mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest test`.
- Known limitation: SSE projection currently replays available events and completes; continuous live tailing is a later run/UI integration concern.

### F003 Project Workbench Model API

- Status: completed
- Feature page: [F003-project-workbench-model-api.md](features/F003-project-workbench-model-api.md)
- Evidence: [EV-002-f003-project-workbench-model-api.md](evidence/EV-002-f003-project-workbench-model-api.md)
- Result: project-level schema skeleton, `ProjectRepository`, `ProjectController`, repository tests, controller tests, and cross-project database boundary coverage are in place.

### F001 Harness Engineering Loop

- Status: completed
- Feature page: [F001-harness-engineering-loop.md](features/F001-harness-engineering-loop.md)
- Evidence: [EV-001-harness-bootstrap.md](evidence/EV-001-harness-bootstrap.md)
- Result: BACKLOG, Feature, ADR, Evidence, templates, and `scripts/knowledge_check.py` are in place.

## Standing Judgments

- Keep old code only when it accelerates the rebuild or provides a verifiable behavior reference.
- Prefer replacing unstable low-value surfaces over preserving compatibility that distorts the project-level model.
- Markdown Harness documents are the source of truth; indexes or summaries are compiled artifacts.
- Development handoff before F002 implementation: [2026-05-09-f002-to-f003.md](handoffs/2026-05-09-f002-to-f003.md)
