---
id: F024-SPEC
doc_kind: spec
status: completed
created: 2026-05-17
updated: 2026-05-18
feature_ids: [F024]
---
# F024 L3 To L2 Candidate Promotion And Decay Spec

## Problem

L3 memory recall is currently useful only inside the answer where it is recalled. If the same L3 memory repeatedly helps the same project, the system should surface it as a reviewable knowledge candidate. However, L3 memory is a summarized historical signal, not user-confirmed knowledge, so automatic confirmation would pollute L2.

## Promotion Contract

Input:

- `projectId`
- `sessionId`
- `runId`
- `answerId`
- current `MemoryRecallResult`

Eligibility:

- hit is L3 `memory_entry`
- project-scoped historical hit count for the same `sourceMemoryEntryId` plus current hit is at least `2`
- hit count only increments for a new answer/run identity; replaying the same `answerId` or `runId` is idempotent
- latest hit score is above the minimum promotion score
- no active confirmed project knowledge already duplicates the candidate text by exact normalized title/content comparison
- no pending L3 candidate for the same memory already exists

Output:

- create or update a pending `knowledge_candidate`
- `sourceKind=l3_memory`
- `sourceMemoryEntryId=<memory_entry.id>`
- `promotionHitCount`
- `promotionLastScore`
- `promotionReason=l3_memory_repeated_hit`
- `sourceTypes=["l3_memory"]`
- `evidenceSourceIds=[]`

## Decay Contract

Automatic decay affects only pending candidates with `sourceKind=l3_memory`.

Decay reasons:

- `stale_l3_candidate`: pending candidate has not been accepted within the configured age window.
- `duplicate_confirmed_knowledge`: equivalent confirmed project knowledge now exists.

Decay action:

- set `status=decayed`
- set `decayReason`
- publish a `candidate.decayed` event

## Event Contract

Candidate creation continues to use `candidate.created`.

F024 adds or emits decay events with enough data for the frontend trace:

- `eventType=candidate.decayed`
- `candidateId`
- `sourceKind=l3_memory`
- `sourceMemoryEntryId`
- `decayReason`
- `status=decayed`

## Non-Goals

- no automatic confirmed knowledge writes
- no confirmed L2 auto-delete or auto-archive
- no Paper RAG or citation evidence changes
- no background scheduler
- no L3 storage remodel or project-id migration in `memory_entry`

## Verification

- focused service test: first L3 hit does not create a candidate, second hit creates one.
- focused service test: replaying the same run/answer does not increment promotion hit count.
- focused service test: repeated hits update the pending candidate instead of duplicating it.
- focused service test: duplicate confirmed knowledge blocks or decays an L3 candidate.
- focused service test: stale pending L3 candidate decays without becoming user ignored.
- focused routing test: project answer with repeated L3 recall publishes observable candidate event and does not create evidence rows or confirmed knowledge.
- frontend model test: `candidate.created` and `candidate.decayed` preserve F024 metadata and keep pending review separate from confirmed knowledge.
