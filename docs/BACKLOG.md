# Work Backlog

This file records active engineering state that future sessions must be able to recover. It is not an unlimited wishlist.

## Active Work

### No active F002 implementation

- Status: F002/F011 closeout complete.
- Parent Feature: [F002-next-generation-research-workbench.md](features/F002-next-generation-research-workbench.md)
- Validation Feature: [F011-f002-end-to-end-validation.md](features/F011-f002-end-to-end-validation.md)
- Evidence: [EV-010-f002-implementation-validation.md](evidence/EV-010-f002-implementation-validation.md)
- Current intent: no active F002 implementation remains. Future work should be opened as separate Features from known limitations, not by expanding F011.
- Known limitations only: replay-oriented SSE projection, no external web search connector, no source-scoped live SSE, no broad source search, no account preferences or multi-tenant behavior, and no feedback undo/deduplication or long-term personalization.

## Recently Completed

### F011 F002 End-to-End Validation

- Status: completed
- Feature page: [F011-f002-end-to-end-validation.md](features/F011-f002-end-to-end-validation.md)
- Evidence: [EV-010-f002-implementation-validation.md](evidence/EV-010-f002-implementation-validation.md)
- Result: F011 closed F002 with validation evidence only. Parent-session backend full verification first exposed a validation harness gap in `Phase1HappyPathTest`; after hardening the test harness, targeted backend validation, full backend validation, frontend model tests, and Harness validation all passed.
- Known limitation: F011 did not add product capability; F010 browser evidence remains in EV-009 and is summarized by EV-010.

### F002 Next-Generation Research Workbench

- Status: completed
- Feature page: [F002-next-generation-research-workbench.md](features/F002-next-generation-research-workbench.md)
- Evidence: [EV-010-f002-implementation-validation.md](evidence/EV-010-f002-implementation-validation.md)
- Result: F003-F010 delivered the implemented workbench baseline, and F011 completed validation and Harness closeout.
- Known limitation: remaining gaps are future-feature candidates only: replay-oriented SSE projection, no external web search connector, no source-scoped live SSE, no broad source search, no account preferences or multi-tenant behavior, and no feedback undo/deduplication or long-term personalization.

### F010 Three-Column Research Workbench UI

- Status: completed
- Feature page: [F010-three-column-workbench-ui.md](features/F010-three-column-workbench-ui.md)
- Evidence: [EV-009-f010-three-column-workbench-ui.md](evidence/EV-009-f010-three-column-workbench-ui.md)
- Result: static workbench now exposes stable left project/source, center dialogue, and right research-sidebar regions; the frontend state model handles session selection, source status changes, answer deltas, evidence hydration, pending candidates, confirmed knowledge entries, deterministic fallback IDs, and duplicate SSE event IDs; `workbench-app.js` uses F002 project endpoints and run SSE as the main path with named compatibility functions for legacy demo endpoints; parent-session Chrome verification covered desktop layout, narrow viewport overflow, typed SSE updates, evidence refresh, and candidate edit-and-accept.
- Known limitation: F010 does not add source-scoped live SSE, broad search, web retrieval, account preferences, or multi-tenant UI.

### F009 Feedback Score Loop

- Status: completed
- Feature page: [F009-feedback-score-loop.md](features/F009-feedback-score-loop.md)
- Evidence: [EV-008-f009-feedback-score-loop.md](evidence/EV-008-f009-feedback-score-loop.md)
- Result: project answer feedback can now be posted with `rating=up` or `rating=down`; answer-level feedback is recorded; selected same-project evidence and linked paper chunks receive feedback score deltas; empty evidence selections record answer feedback only; cross-project evidence IDs do not mutate other projects or leak into applied event payloads; `feedback.applied` is published; and keyword, pgvector, and local vector retrieval scoring use the bounded formula `relevance + clamp(feedbackScore * 0.05, -0.2, 0.2)`.
- Known limitation: F009 provides backend feedback and retrieval scoring only; UI controls, undo/deduplication, long-term personalization, and complex reranking remain outside this slice.

### F008 Candidate Confirmation and Knowledge Board

- Status: completed
- Feature page: [F008-candidate-confirmation-knowledge-board.md](features/F008-candidate-confirmation-knowledge-board.md)
- Evidence: [EV-007-f008-candidate-confirmation-knowledge-board.md](evidence/EV-007-f008-candidate-confirmation-knowledge-board.md)
- Result: project candidates can be listed by answer or project, accepted, edited-and-accepted, marked unverified, or ignored; knowledge board entries can be listed by fixed section, manually created, patched/moved, and archived without hard delete; candidate creation publishes `candidate.created` without creating `KnowledgeEntry`; entry creation publishes `knowledge.entry.created`.
- Known limitation: this slice provides the confirmation and board API boundary only; candidate generation quality, feedback scoring, broad source search, web retrieval, and F010 UI remain outside F008.

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
