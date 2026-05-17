---
id: EV-023
doc_kind: evidence
status: completed
created: 2026-05-18
updated: 2026-05-18
feature_ids: [F024]
---
# EV-023 F024 L3 To L2 Candidate Promotion And Decay

## Evidence

F024 turns repeatedly useful L3 memory recall into reviewable pending L2 candidates without crossing the confirmed-knowledge or citation-evidence boundary.

## Implementation Evidence

- Added `l3_memory_promotion_state` and `l3_memory_promotion_hit` storage so promotion is project-scoped and answer/run replay is idempotent.
- Extended `knowledge_candidate` with `source_kind`, `source_memory_entry_id`, `promotion_hit_count`, `promotion_last_score`, `promotion_reason`, and `decay_reason`.
- Added `L3KnowledgePromotionService`, wired from `SupervisorService` after memory trace publication and before retrieval completion telemetry.
- Added repository paths to create/update pending L3-sourced candidates and mark pending L3-sourced candidates `decayed`.
- Added `candidate.decayed` event handling in backend event types and frontend projection state.

## Boundary Evidence

- L3 promotion writes only pending `knowledge_candidate` rows; it does not call `KnowledgeBoardRepository.createEntry`.
- L3 promotion candidates use `sourceTypes=["l3_memory"]` and `evidenceSourceIds=[]`.
- The `answerProject` integration test verifies repeated L3 recall creates a pending candidate while `evidence_source` and `knowledge_entry` counts remain unchanged.
- Decay uses `status=decayed` plus `decayReason`; it does not reuse the user-driven `ignored` status.
- Duplicate blocking is an exact normalized title/content guard against confirmed project knowledge. Semantic equivalence detection remains a future hardening option.

## Verification Commands

```powershell
node --check src\main\resources\static\js\workbench-model.js
node --check src\main\resources\static\js\workbench-app.js
node --test src\main\resources\static\tests\f002-workbench-model.test.mjs
```

Result: both syntax checks passed; 54 frontend model tests passed.

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=L3KnowledgePromotionServiceTest,ProjectAgentRoutingTest#repeatedProjectL3RecallCreatesPendingCandidateWithoutEvidenceOrConfirmedKnowledge+l3MemoryRecallPublishesRankedToolRecallContextOnlyTrace,KnowledgeCandidateControllerTest#listsCandidatesByAnswerAndProjectWithoutCreatingKnowledgeEntries+acceptsCandidateIntoKnowledgeEntryAndPublishesKnowledgeEntryCreated' test
```

Result: 9 tests passed, BUILD SUCCESS.

```powershell
python scripts\knowledge_check.py
git diff --check
```

Result: `knowledge_check: ok`; `git diff --check` reported no whitespace errors.

## Acceptance Checks

- A first L3 hit does not create a candidate.
- A second distinct answer/run hit can create one pending L3-sourced candidate.
- Replaying the same answer/run does not increment promotion hit count.
- Future distinct hits update the same pending candidate instead of creating duplicates.
- Duplicate confirmed project knowledge decays a pending L3 candidate and blocks new candidate creation.
- Stale pending L3 candidates decay without becoming user ignored.
- L3 candidate promotion is observable through candidate events and retrieval telemetry while citation counts remain zero.
- Frontend state preserves L3 promotion metadata and removes decayed candidates from pending review.

## Residual Risk

The duplicate-confirmed guard is intentionally exact normalized matching for F024. If users frequently edit accepted candidate text, a later feature should add semantic duplicate detection or an explicit candidate supersession workflow. F024 also keeps L3 project scoping in the promotion state rather than migrating `memory_entry` to `project_id`; that keeps the slice smaller but leaves full L3 project scoping as separate future work.
