---
id: BACKLOG
doc_kind: backlog
status: active
updated: 2026-05-17
---
# Work Backlog

This file records active engineering state that future sessions must be able to recover. It is not an unlimited wishlist.

## Active Work

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

### F024 L3 To L2 Candidate Promotion And Decay

- Status: completed
- Feature page: [F024-l3-to-l2-candidate-promotion-decay.md](features/F024-l3-to-l2-candidate-promotion-decay.md)
- Spec: [F024-l3-to-l2-candidate-promotion-decay-spec.md](specs/F024-l3-to-l2-candidate-promotion-decay-spec.md)
- Plan: [F024-l3-to-l2-candidate-promotion-decay-plan.md](plans/F024-l3-to-l2-candidate-promotion-decay-plan.md)
- Evidence: [EV-023-f024-l3-to-l2-candidate-promotion-decay.md](evidence/EV-023-f024-l3-to-l2-candidate-promotion-decay.md)
- Result: repeated high-score L3 memory recall now records project-scoped promotion hits, dedupes answer/run replay, and creates or updates pending L2 candidates only after repeated distinct hits. Stale or duplicate pending L3 candidates are marked `decayed`, while confirmed L2 knowledge still requires user accept/edit-and-accept.
- Boundary: F024 never auto-writes `knowledge_entry`, never creates citation `evidence_source` rows from memory, and keeps Paper RAG/citation evidence unchanged. Duplicate blocking is exact normalized title/content matching for this MVP.
- Verification: service tests cover first/second hit, replay idempotency, update dedupe, duplicate-confirmed decay, and stale decay; routing tests cover `answerProject` promotion without evidence/confirmed pollution; frontend model tests cover F024 candidate metadata and decay projection.

### F023 L2 Knowledge Semantic Retrieval

- Status: completed
- Feature page: [F023-l2-knowledge-semantic-retrieval.md](features/F023-l2-knowledge-semantic-retrieval.md)
- Spec: [F023-l2-knowledge-semantic-retrieval-spec.md](specs/F023-l2-knowledge-semantic-retrieval-spec.md)
- Plan: [F023-l2-knowledge-semantic-retrieval-plan.md](plans/F023-l2-knowledge-semantic-retrieval-plan.md)
- Evidence: [EV-022-f023-l2-knowledge-semantic-retrieval.md](evidence/EV-022-f023-l2-knowledge-semantic-retrieval.md)
- Result: confirmed project knowledge recall now ranks a bounded candidate pool by semantic relevance to the current question, confirmed-status confidence, and weak recency tie-breaker. The selected entries are injected as L2 `project_knowledge` context and traced with score components, rank, injection mode, reason, and `contextOnly=true`.
- Boundary: F023 is Memory Retrieval over `knowledge_entry`; it does not change Paper RAG, create citation evidence, add a vector table, add L3-to-L2 promotion, or introduce schema migration.
- Verification: ranker unit test, Supervisor prompt/trace integration tests, frontend trace model test, JS syntax checks, and focused Maven tests are recorded in EV-022.

### F022 Memory Recall Observability

- Status: completed
- Feature page: [F022-memory-recall-observability.md](features/F022-memory-recall-observability.md)
- Spec: [F022-memory-recall-observability-spec.md](specs/F022-memory-recall-observability-spec.md)
- Plan: [F022-memory-recall-observability-plan.md](plans/F022-memory-recall-observability-plan.md)
- Evidence: [EV-021-f022-memory-recall-observability.md](evidence/EV-021-f022-memory-recall-observability.md)
- Result: L1/L2/L3 memory hits now carry normalized context-only metadata, including rank, injection mode, reason, title, and summary. L2 covers both global cognition and confirmed project knowledge. The frontend trace model groups memory by layer, and the research process UI shows memory separately from paper/web evidence so memory does not look like citation material.
- Boundary: F022 did not implement L2 semantic retrieval, L3-to-L2 promotion, L3 project scoping, or Paper RAG scoring changes.
- Verification: focused backend tests, full frontend model tests, JS syntax checks, `knowledge_check`, and `git diff --check` are recorded in EV-021.

### F016 Retrieval Observability

- Status: completed
- Feature page: [F016-retrieval-observability.md](features/F016-retrieval-observability.md)
- Spec: [F016-retrieval-observability-spec.md](specs/F016-retrieval-observability-spec.md)
- Plan: [F016-retrieval-observability-plan.md](plans/F016-retrieval-observability-plan.md)
- Evidence: [EV-015-f016-retrieval-observability-slice.md](evidence/EV-015-f016-retrieval-observability-slice.md)
- Result: Paper RAG retrieval now has durable, session-scoped observability for query rewrite, per-backend hit counts, vector pre/post scope filtering, zero-hit taxonomy, bounded tool-call behavior, Answer Run attribution, and final paper evidence metadata. The workbench exposes this through the Observability / answer-evidence diagnostics workspace and a project/session scoped diagnostics endpoint.
- Verification: the expanded focused backend slice, frontend model/syntax checks, Docker rebuild, HTTP smoke, and Harness knowledge check are recorded in EV-015. A later full `mvn test` attempt was blocked by shared Testcontainers PostgreSQL client exhaustion after the relevant F016/F021 boundary slices passed.
- Known limitation: F016 intentionally does not add a dedicated `retrieval_observation` table, cross-session trend analytics, or an exact citation-to-retrieval-observation id. Keyword/metadata repositories still apply scope internally, so only vector retrieval exposes true pre/post scope counts.

### F021 Memory Self-Learning Visualization / F021.1 Closed Loop Reset

- Status: completed
- Feature page: [F021-memory-self-learning-visualization.md](features/F021-memory-self-learning-visualization.md)
- Reset plan: [F021.1-self-learning-closed-loop-reset-plan.md](plans/F021.1-self-learning-closed-loop-reset-plan.md)
- Evidence: [EV-020-f021-memory-self-learning-visualization.md](evidence/EV-020-f021-memory-self-learning-visualization.md)
- Result: the F021.1 reset closed the self-learning demo loop without expanding the storage model. Confirmed `knowledge_entry` records now enter the next project answer as L2/project knowledge context, are traced as `memoryLayer=L2`, `sourceType=project_knowledge`, and `contextOnly=true`, and remain separate from citation evidence counts. Answer feedback now resolves persisted answer context so `feedback.applied` carries `projectId`, `sessionId`, `runId`, and `answerId`; reloaded project session messages also return `answerId` and `runId` for assistant answers.
- Verification: focused backend checks for L2 project knowledge trace, feedback run identity, and session-history `answerId/runId` passed on 2026-05-17; `workbench-app.js` syntax check and F021 frontend model tests also passed.
- Known limitation: post-answer feedback is still applied immediately in the UI through the existing local projection path after the feedback API returns. If strict replay of post-terminal run events becomes required, handle it as an event-stream hardening follow-up rather than reopening the F021 memory-loop scope.

### F020 Plan-Execute Evidence Carry-Through

- Status: completed
- Feature page: [F020-plan-execute-evidence-carry-through.md](features/F020-plan-execute-evidence-carry-through.md)
- Spec: [F020-plan-execute-evidence-carry-through-spec.md](specs/F020-plan-execute-evidence-carry-through-spec.md)
- Plan: [F020-plan-execute-evidence-carry-through-plan.md](plans/F020-plan-execute-evidence-carry-through-plan.md)
- Evidence: [EV-019-f020-plan-execute-evidence-carry-through.md](evidence/EV-019-f020-plan-execute-evidence-carry-through.md)
- Result: Plan-Execute paper/web evidence now carries structured citation references through curation and semantic gating; accepted references are persisted into `evidence_source`, and Plan-Execute citation telemetry reports non-zero counts when accepted citations exist.
- Known limitation: this does not add parallel Agent runtime, a new citation table, or LLM-based document writing. Manual live demo validation after Docker rebuild/start remains a useful pre-interview check.

### F019 Semantic Intent Routing

- Status: completed
- Feature page: [F019-semantic-intent-routing.md](features/F019-semantic-intent-routing.md)
- Spec: [F019-semantic-intent-routing-spec.md](specs/F019-semantic-intent-routing-spec.md)
- Plan: [F019-semantic-intent-routing-plan.md](plans/F019-semantic-intent-routing-plan.md)
- Evidence: [EV-018-f019-semantic-intent-routing.md](evidence/EV-018-f019-semantic-intent-routing.md)
- Result: Supervisor workflow selection now uses a model-backed semantic advisor for `REACT` vs `PLAN_EXECUTE`, with deterministic routing kept as fallback and guardrail. The mode-selection trace records `decisionSource`, `fallbackReason`, and `semanticConfidence`, and the frontend research-process timeline can display the mode decision before the serial subagent plan.
- Known limitation: this does not add parallel runtime, durable citation carry-through, or dynamic skills. Provider output quality is bounded by strict JSON parsing, confidence gating, consistency checks, and deterministic fallback, but still deserves manual live validation after rebuild/start.

### F018 Multi-Agent Evidence-Grounded Workflow

- Status: completed
- Feature page: [F018-multi-agent-evidence-grounded-workflow.md](features/F018-multi-agent-evidence-grounded-workflow.md)
- Evidence: [EV-017-f018-multi-agent-evidence-grounded-workflow.md](evidence/EV-017-f018-multi-agent-evidence-grounded-workflow.md)
- ADR: [ADR-005-supervisor-led-serial-multi-agent-workflow.md](decisions/ADR-005-supervisor-led-serial-multi-agent-workflow.md)
- Result: project chat now supports Supervisor-led mode selection. Simple tasks remain on the existing ReAct `ProjectAgentToolLoop`; complex research and document-output requests enter a serial Plan-Execute path with `Deep Research Agent`, `Evidence Audit Agent`, and `Document Composer Agent`. Backend trace/SSE publishes real live subagent lifecycle events from the execution boundary, and the frontend research-process model/UI projects the serial multi-agent process without inventing parallelism or fake subagents.
- Known limitation: Plan-Execute research packet evidence strings are trace/process telemetry only. They are not persisted as `evidence_source` rows and do not increase final citation counts until a later feature carries structured source references through the Plan-Execute result. Parallel runtime, dynamic skill marketplace, and human approval checkpoints remain out of scope.

### F017 Session Delete and Review Sidebar

- Status: completed
- Feature page: [F017-session-delete-and-review-sidebar.md](features/F017-session-delete-and-review-sidebar.md)
- Evidence: [EV-016-f017-session-delete-and-review-sidebar.md](evidence/EV-016-f017-session-delete-and-review-sidebar.md)
- Result: project sessions now support confirmed hard delete through `DELETE /api/projects/{projectId}/sessions/{sessionId}`; deletion explicitly removes session-scoped messages, answers, evidence, candidates, stream events, retrieval traces, and memory entries. The session list exposes compact rename/delete actions, and the right sidebar now frames itself as current-answer review with final evidence, candidate drafts, and project knowledge summary kept conceptually separate.
- Known limitation: this does not improve candidate generation quality or auto-create knowledge entries; empty candidate/knowledge states remain valid and are now explained in UI copy.

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
