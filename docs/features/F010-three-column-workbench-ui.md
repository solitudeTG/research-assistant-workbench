---
id: F010
status: completed
owner: codex
updated: 2026-05-10
parent_feature: F002
evidence:
  - ../evidence/EV-009-f010-three-column-workbench-ui.md
---
# Three-Column Research Workbench UI

## Goal

Upgrade the static frontend from a single-document Q&A demo into the F002 three-column research workbench: project/session/source management on the left, multi-turn research dialogue in the center, and knowledge/evidence/candidate confirmation in the right research sidebar.

## Scope

- In scope: static `HTML/CSS/JS` workbench structure.
- In scope: pure frontend state model and SSE deduplication.
- In scope: source status, answer streaming state, and right-sidebar view switching for evidence and candidates.
- Out of scope: backend API implementation, broad source search, web retrieval, account preferences, and multi-tenant UI.

## Acceptance Criteria

- Desktop layout exposes three usable columns.
- Narrow viewport stacks columns without overlapping text or controls.
- `source.status.changed` updates the matching source row in frontend state.
- `answer.delta` updates the center answer state.
- `candidate.created` adds a pending candidate without adding a knowledge-board entry.
- `knowledge.entry.created` updates the correct knowledge-board section.
- Duplicate SSE event IDs are ignored.
- `node --test src/main/resources/static/tests/f002-workbench-model.test.mjs` passes.

## Result

F010 is completed as a static frontend and Harness slice. The workbench now has stable three-column regions, project-scoped API loading where the F002 backend exists, run SSE consumption for the center answer and sidebar context, evidence hydration for evaluated answers, inline candidate edit-and-accept controls, compatibility fallback functions for old demo endpoints, focused Node ESM tests for the frontend state model, and parent-session Chrome verification for desktop and narrow viewport behavior.

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- UI spec: [research-workbench-ui-interaction-spec.md](../product/research-workbench-ui-interaction-spec.md)
- Evidence: [EV-009-f010-three-column-workbench-ui.md](../evidence/EV-009-f010-three-column-workbench-ui.md)

## Next Step

Start F011 end-to-end F002 validation. Do not expand F011 into new product capabilities; it should verify the full F002 system and close the parent Feature based on evidence.
