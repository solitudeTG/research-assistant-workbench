---
id: BACKLOG
doc_kind: backlog
status: active
updated: 2026-05-12
---
# Work Backlog

This file records active engineering state that future sessions must be able to recover. It is not an unlimited wishlist.

## Active Work

### F016 Retrieval Observability

- Status: active
- Feature page: [F016-retrieval-observability.md](features/F016-retrieval-observability.md)
- Spec: [F016-retrieval-observability-spec.md](specs/F016-retrieval-observability-spec.md)
- Plan: [F016-retrieval-observability-plan.md](plans/F016-retrieval-observability-plan.md)
- Current intent: turn Paper RAG retrieval into a long-lived observable capability that can explain query rewrite, hybrid retrieval hit counts, scope filtering, rerank output, zero-hit reasons, and final citation linkage. This is intentionally a backend observability feature first, not another UI patch.
- Product reason: recent research-process UI exposed many `paper_rag returned 0 scoped chunk(s)` events. The durable fix is to make the backend explain whether the cause is Agent over-calling, weak query rewrite, no backend hits, scope-filtered vector candidates, rerank empty output, or missing scoped evidence.
- Current slice: minimum observation model, query rewrite strategy, `NO_BACKEND_HITS` classification, `retrieval.query.rewritten`, `retrieval.completed`, and `tool.completed.data.retrievalObservationSummary` are implemented and covered by focused tests. Live observation also exposed that local restart recovery was broken for `LocalVectorSearchPort`; startup warmup now rehydrates indexed chunks into the in-memory vector index.
- Evidence: [EV-015-f016-retrieval-observability-slice.md](evidence/EV-015-f016-retrieval-observability-slice.md)
- Next step: rebuild/restart the backend and rerun the same manual query. If `vector_hits` becomes non-zero, continue with Agent query-budget and zero-hit suppression work; if not, inspect local embedding/index scoring before changing UI.

### F014 Primary Workspace Navigation UI

- Status: active
- Feature page: [F014-primary-workspace-navigation-ui.md](features/F014-primary-workspace-navigation-ui.md)
- Spec: [F014-primary-workspace-navigation-ui-spec.md](specs/F014-primary-workspace-navigation-ui-spec.md)
- Current intent: refactor the static UI information architecture so the left rail becomes a first-level workspace switcher. Session keeps the chat/evidence/candidate working surface; sources and knowledge become complete workspaces that replace the main area instead of living as side panels inside the chat shell.
- Design anchors: PRODUCT.md, DESIGN.md, Stitch project `6874803135901272290`, sources screen `5379df1c6dcb4012aadb1420d7117fc7`, knowledge screen `e220ee0bd7474bb48ef9a3bd507b2138`.
- Next step: write the implementation plan, then change frontend state/layout before CSS polish.

### No active F002 implementation

- Status: F002/F011 closeout complete.
- Parent Feature: [F002-next-generation-research-workbench.md](features/F002-next-generation-research-workbench.md)
- Validation Feature: [F011-f002-end-to-end-validation.md](features/F011-f002-end-to-end-validation.md)
- Evidence: [EV-010-f002-implementation-validation.md](evidence/EV-010-f002-implementation-validation.md)
- Readiness: [RD-001-f002-closeout-readiness.md](reviews/RD-001-f002-closeout-readiness.md)
- Lesson: [LL-001-explicit-validation-fixtures.md](lessons/LL-001-explicit-validation-fixtures.md)
- Current intent: no active F002 implementation remains. Future work should be opened as separate Features from known limitations, not by expanding F011.
- Known limitations only: replay-oriented SSE projection, no external web search connector, no source-scoped live SSE, no broad source search, no account preferences or multi-tenant behavior, and no feedback undo/deduplication or long-term personalization.

## Recently Completed

### F015 Agent Trace Event Contract and Live SSE

- Status: completed
- Feature page: [F015-agent-trace-live-sse.md](features/F015-agent-trace-live-sse.md)
- Evidence: [EV-013-f015-agent-trace-live-sse.md](evidence/EV-013-f015-agent-trace-live-sse.md)
- Result: project run SSE now replays and live-tails until terminal run events; event publishers expose a bounded wait seam for in-memory and Redis backends; the main Agent tool loop emits real `tool.*`, `retrieval.hit`, `memory.*`, conservative `evidence.gap.detected`, and paragraph `answer.delta` events; the frontend model folds these into `agentTraces[runId]`; and the session UI renders that projection as a research process module under assistant answers.
- Known limitation: F015 Phase 1 still uses logical step labels over the current main-Agent runtime, not a true parallel Supervisor-Worker system. The UI must not claim parallel subagents until that runtime exists.

### F013 Main Agent Tool-Calling Loop

- Status: completed
- Feature page: [F013-main-agent-tool-calling-loop.md](features/F013-main-agent-tool-calling-loop.md)
- Evidence: [EV-012-f013-main-agent-tool-calling-loop.md](evidence/EV-012-f013-main-agent-tool-calling-loop.md)
- Result: project chat now uses `ProjectAgentToolLoop` instead of rule-first project routing. The main Agent sees `web_search`, `paper_rag`, and `memory_recall` tools through Spring AI native tool calling; `retrieval.completed.toolsUsed` reflects actual tool use; web and paper evidence remain separated; memory recall remains context only.
- Known limitation: F013 validates native tool exposure and focused backend behavior, but does not run a live provider/Tavily weather smoke test because that depends on local API keys and provider behavior. If a configured provider cannot emit native tool calls reliably, open a follow-up Feature for bounded JSON action-loop fallback behind the same interface.

### F012 Agentic Routing and Tavily Web Search

- Status: completed
- Feature page: [F012-agentic-routing-and-tavily-web-search.md](features/F012-agentic-routing-and-tavily-web-search.md)
- Evidence: [EV-011-f012-agentic-routing-web-search.md](evidence/EV-011-f012-agentic-routing-web-search.md)
- Result: project chat now uses a main-Agent routing step over default L1/L2 context. Simple chat bypasses Paper RAG and Tavily; local research calls Paper RAG; explicit or inferred web needs can call Tavily; mixed freshness questions can call both; web evidence is persisted as `source_type=web`; and run events report actual tools used plus source types.
- Known limitation: routing is deterministic/rule-first rather than LLM-based; Tavily requires `TAVILY_API_KEY`; web results are answer evidence only, not durable ingested sources; richer tool timelines and multi-agent delegation should be separate Features.

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
