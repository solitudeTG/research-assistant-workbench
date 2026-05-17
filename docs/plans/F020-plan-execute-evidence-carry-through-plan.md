---
id: F020-PLAN
doc_kind: plan
status: completed
updated: 2026-05-16
feature_ids: [F020]
---
# F020 Plan-Execute Evidence Carry-Through Plan

## Steps

- [x] Add a failing backend test for Plan-Execute citation persistence.
- [x] Introduce a structured citation reference record without adding a new table.
- [x] Attach references in `DeepResearchAgent` and preserve them through curation/gating.
- [x] Persist accepted Plan-Execute references through `EvidenceSourceRepository`.
- [x] Update Plan-Execute assessment and run-event telemetry.
- [x] Run focused backend tests, frontend model tests, diff hygiene, and Harness validation.
- [x] Record Evidence and update Backlog/Feature state.

## Rollback

The change is code-only. If the citation carry-through causes regressions, revert the new structured reference path and keep the previous string-only Plan-Execute behavior.
