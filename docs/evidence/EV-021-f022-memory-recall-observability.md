---
id: EV-021
doc_kind: evidence
status: accepted
created: 2026-05-17
updated: 2026-05-17
feature_ids: [F022]
---
# EV-021 F022 Memory Recall Observability

## Evidence

F022 makes memory participation in project answers more explainable without changing retrieval ranking or evidence semantics.

## Implemented Capability

- L1/L2/L3 `memory.hit` events now carry `title`, `summary`, `rank`, `injectionMode`, and `reason`.
- L2 confirmed project knowledge remains `sourceType=project_knowledge` and `contextOnly=true`.
- L2 global cognition from `USER.md` / `SOUL.md` / `Research_state.md` now emits a `sourceType=global_knowledge` trace row when non-empty.
- L3 memory recall remains `sourceType=long_term_memory` and `contextOnly=true`.
- `memory.completed` now exposes both old and spec-aligned aggregate counts:
  - `workingMemoryHitCount` / `l1HitCount`
  - `l2HitCount` / `projectKnowledgeHitCount`
  - `l3HitCount` / `longTermMemoryHitCount`
  - `toolCalled`
  - `contextOnly`
- The frontend trace model now groups memory hits by `L1`, `L2`, and `L3`.
- The research process UI renders L1/L2/L3 memory in the memory/self-learning panel and no longer mixes memory hits into the citation evidence hit list.
- `memory_recall` tool JSON now includes rank and context-only recall metadata for L3 hits.

Not implemented:

- L2 semantic retrieval.
- L3-to-L2 promotion.
- L3 project boundary migration.
- Any Paper RAG scoring or citation behavior change.

## Verification

Frontend:

```text
node --check src\main\resources\static\js\workbench-model.js
node --check src\main\resources\static\js\workbench-app.js
```

Result: pass.

```text
node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "F022|F021 memory|memory.completed hit count|research process"
```

Result: 51 tests passed.

```text
node --test src\main\resources\static\tests\f002-workbench-model.test.mjs
```

Result: 51 tests passed.

Backend:

```text
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectAgentRoutingTest#workingMemorySummaryPublishesMemoryHitEvenWhenL3RecallIsEmpty+globalKnowledgeSnapshotPublishesL2ContextOnlyMemoryTrace+confirmedKnowledgeEntryPublishesL2ProjectKnowledgeMemoryTraceWithoutCitationEvidence+l3MemoryRecallPublishesRankedToolRecallContextOnlyTrace,ProjectAgentToolsTest#memoryRecallToolCallsMemoryRecallPortAndDoesNotCreateEvidence' test
```

Result: 5 tests passed, BUILD SUCCESS.

Harness:

```text
python .\scripts\knowledge_check.py
```

Result: `knowledge_check: ok`.

```text
git diff --check
```

Result: no whitespace errors; Git reported only CRLF normalization warnings.

## Boundary Evidence

- Memory context remains `contextOnly=true`.
- L2/L3 memory hits are rendered separately from paper/web evidence in the research process UI.
- Focused backend tests keep `evidenceSourceCount(answerId) == 0` for L2/L3 memory-only traces.
- F022 does not introduce a new table, vector index, Paper RAG change, or auto-confirmed knowledge write.

## Independent Review

An independent Vision Guardian reviewed F022 against the Feature/Spec/Plan after implementation and found one required revision: L2 global cognition was injected into the prompt but not traced. The branch was revised to emit `sourceType=global_knowledge` L2 memory hits for non-empty global cognition snapshots and the focused backend suite was rerun successfully.

The final reviewer noted one minor timeline wording polish: L2 timeline labels should distinguish global cognition from confirmed project knowledge. The UI now labels `global_knowledge` as `L2 稳定认知` and `project_knowledge` as `L2 已确认知识`.

The reviewer also noted that frontend UI tests are still source-slice based rather than full DOM rendering. This is accepted as residual test shape for F022 because the model projection is covered directly, the source-slice tests guard the rendered code path, and the change does not introduce a new browser-rendering framework. A future visual smoke test can harden the UI layer if research-process rendering grows further.
