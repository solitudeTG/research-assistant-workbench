---
id: F011
doc_kind: feature
status: completed
owner: codex
updated: 2026-05-10
parent_feature: F002
evidence:
  - ../evidence/EV-010-f002-implementation-validation.md
---
# F002 End-to-End Validation and Evidence Closeout

## Goal

Close F002 with end-to-end validation evidence after F003-F010 shipped the implementation slices. F011 exists to prove the workbench baseline is recoverable and verifiable, not to add new product capability.

## Current Status

completed.

F011 uses [EV-010-f002-implementation-validation.md](../evidence/EV-010-f002-implementation-validation.md) instead of the originally planned `EV-002-f002-implementation-validation.md` filename because `EV-002` already belongs to F003. Unique Evidence IDs are required for Harness traceability.

## Scope

- In scope: full backend test verification.
- In scope: F002 frontend model verification.
- In scope: Harness `knowledge_check.py`.
- In scope: API/SSE sample recording, known limitations, and rollback notes.
- In scope: updating F002 and BACKLOG status.
- Out of scope: new product capabilities, new API behavior, broad source search, web retrieval, or UI expansion.

## Acceptance Criteria

- `mvn test` passes.
- `node --test src/main/resources/static/tests/f002-workbench-model.test.mjs` passes.
- `python scripts/knowledge_check.py` passes.
- Final F002 validation Evidence exists as `docs/evidence/EV-010-f002-implementation-validation.md`.
- F002 Feature links to final Evidence.
- BACKLOG records that no active F002 implementation remains.

## Contracts

- API: no new F011 API.
- Events: no new F011 event type; EV-010 records representative F002 event samples.
- Data: Evidence and Feature status only.
- UI: no new F011 UI; browser evidence remains owned by F010/EV-009 and is summarized by EV-010.

## Evidence

- Final Evidence: [EV-010-f002-implementation-validation.md](../evidence/EV-010-f002-implementation-validation.md)
- Closeout Readiness: [RD-001-f002-closeout-readiness.md](../reviews/RD-001-f002-closeout-readiness.md)
- Validation Lesson: [LL-001-explicit-validation-fixtures.md](../lessons/LL-001-explicit-validation-fixtures.md)

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- Browser Evidence: [EV-009-f010-three-column-workbench-ui.md](../evidence/EV-009-f010-three-column-workbench-ui.md)
- Readiness Dashboard: [RD-001-f002-closeout-readiness.md](../reviews/RD-001-f002-closeout-readiness.md)
- Lesson: [LL-001 Explicit Validation Fixtures](../lessons/LL-001-explicit-validation-fixtures.md)

## Next Step

F011 is closed. Future work should be opened as new Features from the known limitations rather than expanding F011 or reopening the F002 parent.
