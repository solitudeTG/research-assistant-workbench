---
id: F020-SPEC
doc_kind: spec
status: completed
updated: 2026-05-16
feature_ids: [F020]
---
# F020 Plan-Execute Evidence Carry-Through Spec

## Problem

F018/F019 made the multi-agent workflow visible and useful, but final report citations remain weak because Plan-Execute evidence is represented as strings such as `paper: documentId=... content=...`. Those strings are enough for audit/composition, but they are not a durable citation contract.

## Required Behavior

1. `DeepResearchAgent` must create structured evidence references at the same time it creates text evidence.
2. Curation and semantic gating must keep the reference attached to each candidate while still filtering by text.
3. `ResearchPacket` must expose accepted citation references after curation/gating.
4. `SupervisorService` must persist Plan-Execute accepted references to `evidence_source`.
5. Plan-Execute assessment and emitted telemetry must count those persisted references.

## Data Contract

Structured citation references must support:

- `sourceType`: `paper` or `web`
- `text`: bounded human-readable evidence text
- `score`
- paper fields: `documentId`, `chunkId`, `chunkIndex`
- web fields: `title`, `url`, `provider`, `rank`

The system must not parse user-facing report text to reconstruct citations.

## Non-Goals

- No new database table.
- No parallel runtime.
- No change to Evidence Audit semantics.
- No UI redesign beyond consuming already emitted citation telemetry.

## Verification

- RED/GREEN test showing a Plan-Execute answer persists accepted paper/web citations.
- Regression test showing rejected candidates are not persisted.
- Existing F018/F019 focused tests continue to pass.
