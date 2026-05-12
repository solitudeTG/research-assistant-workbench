---
id: F002
doc_kind: feature
status: completed
owner: codex
updated: 2026-05-10
evidence:
  - ../evidence/EV-010-f002-implementation-validation.md
---
# Next-Generation Research Workbench

## Goal

Rebuild the application from a single-document Q&A demo into a project-scoped long-running research workbench. The implemented baseline lets a project own its sources, sessions, run events, evidence, candidates, knowledge board entries, and feedback scoring through documented contracts.

## Current Status

completed.

F003-F010 delivered the implementation slices. F011 completed validation and Harness closeout without adding product capability. Final validation is recorded in [EV-010-f002-implementation-validation.md](../evidence/EV-010-f002-implementation-validation.md).

## Scope

- In scope: project-scoped workbench model, source library, research sessions, answer evidence, candidate confirmation, knowledge board, feedback scoring, run events, and static three-column UI.
- In scope: replacing low-value legacy surfaces where replacement was lower cost than preserving compatibility.
- Out of scope: account management, permission systems, multi-tenant SaaS behavior, generic task graphs, broad source search, and product flows not needed by the workbench baseline.

## Acceptance Criteria

- UI, backend entities, and streaming events share the F002 documented contract.
- Users can enter a research project, import sources, ask questions, inspect evidence, and write confirmed knowledge to the project knowledge board.
- Agent or staged processing is visible through SSE events rather than hidden behind a final answer only.
- Redis Stream is implemented as the event backbone, with an in-memory default for tests.
- Long-term memory and evidence boundaries avoid automatically polluting confirmed knowledge.
- Thumbs-up/thumbs-down feedback updates retrieval scoring through a bounded, explainable formula.
- Final validation evidence exists and passes backend, frontend, browser, and Harness checks appropriate to the current baseline.

## Contracts

- API: defined in [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md).
- Events: `WorkbenchEvent` envelope and F002 event wire names.
- Data: `Project`, `ResearchSession`, `SourceDocument`, `AssistantAnswer`, `EvidenceSource`, `KnowledgeCandidate`, `KnowledgeEntry`, stream events, and feedback records.
- UI: three-column research workbench from [research-workbench-ui-interaction-spec.md](../product/research-workbench-ui-interaction-spec.md).

## Evidence

- Final validation: [EV-010-f002-implementation-validation.md](../evidence/EV-010-f002-implementation-validation.md)
- UI/browser validation: [EV-009-f010-three-column-workbench-ui.md](../evidence/EV-009-f010-three-column-workbench-ui.md)
- Closeout readiness: [RD-001-f002-closeout-readiness.md](../reviews/RD-001-f002-closeout-readiness.md)
- Validation lesson: [LL-001-explicit-validation-fixtures.md](../lessons/LL-001-explicit-validation-fixtures.md)

## Known Limitations

- F004 SSE projection replays available run events and completes; continuous live tailing remains future work.
- External web retrieval is a boundary/fallback, not an implemented web search connector.
- Source-scoped live SSE, broad source search, account preferences, multi-tenant behavior, feedback undo/deduplication, and long-term personalization remain outside F002.

## Links

- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- Vision Gate: [VG-001-f002-before-development.md](../reviews/VG-001-f002-before-development.md)
- Readiness Dashboard: [RD-001-f002-closeout-readiness.md](../reviews/RD-001-f002-closeout-readiness.md)
- Lesson: [LL-001 Explicit Validation Fixtures](../lessons/LL-001-explicit-validation-fixtures.md)
- Child Feature: [F003 Project Workbench Model API](F003-project-workbench-model-api.md)
- Child Feature: [F004 Workbench Event Stream SSE](F004-workbench-event-stream-sse.md)
- Child Feature: [F005 Project Source Status Machine](F005-project-source-status-machine.md)
- Child Feature: [F006 Agent Run Event Flow](F006-agent-run-event-flow.md)
- Child Feature: [F007 Retrieval Evidence Boundary](F007-retrieval-evidence-boundary.md)
- Child Feature: [F008 Candidate Confirmation and Knowledge Board](F008-candidate-confirmation-knowledge-board.md)
- Child Feature: [F009 Feedback Score Loop](F009-feedback-score-loop.md)
- Child Feature: [F010 Three-Column Workbench UI](F010-three-column-workbench-ui.md)
- Child Feature: [F011 F002 End-to-End Validation](F011-f002-end-to-end-validation.md)
- ADR: [ADR-002-rebuild-around-project-workbench.md](../decisions/ADR-002-rebuild-around-project-workbench.md)
- Product UI spec: [research-workbench-ui-interaction-spec.md](../product/research-workbench-ui-interaction-spec.md)

## Next Step

No active F002 implementation remains. Future work should be opened as separate Features from the known limitations above instead of expanding F011 or reopening the parent Feature.
