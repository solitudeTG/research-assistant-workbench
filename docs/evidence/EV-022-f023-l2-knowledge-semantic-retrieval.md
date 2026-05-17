---
id: EV-022
doc_kind: evidence
status: completed
created: 2026-05-17
updated: 2026-05-17
feature_ids: [F023]
---
# EV-022 F023 L2 Knowledge Semantic Retrieval

## Evidence

F023 replaces the recency-only L2 confirmed project knowledge preload with bounded relevance ranking over existing `knowledge_entry` rows.

## Implementation Evidence

- Added `ProjectKnowledgeRecallService` and `ProjectKnowledgeRecallHit`.
- `KnowledgeBoardRepository.listConfirmedProjectKnowledgeCandidates(...)` reads only active confirmed entries within the project, capped at 50 candidates.
- `SupervisorService` now calls the recall service with the current question, passes the ranked entries to `ProjectAgentRequest`, and publishes L2 `project_knowledge` memory hits with `score`, `semanticScore`, `evidenceScore`, `recencyScore`, `rank`, `injectionMode=preloaded_prompt`, `reason=semantic_confirmed_project_knowledge`, and `contextOnly=true`.
- The frontend trace model preserves the F023 metadata inside the existing F022 L2 memory group.

## Boundary Evidence

- F023 does not call `PaperRagService`, `VectorSearchPort`, `LocalVectorSearchPort`, `PgVectorSearchPort`, or `document_chunk`.
- F023 does not create `evidence_source` rows for L2 knowledge memory.
- F023 does not add a schema migration, vector table, L3 promotion, or automatic confirmed-knowledge writes.
- Global L2 cognition remains separate as `sourceType=global_knowledge` from F022 and is not ranked by the F023 project-knowledge ranker.

## Verification Commands

```powershell
node --check src\main\resources\static\js\workbench-model.js
node --check src\main\resources\static\js\workbench-app.js
node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "F023|F022"
```

Result: 52 tests passed.

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectKnowledgeRecallServiceTest,ProjectAgentRoutingTest#confirmedKnowledgeEntryPublishesL2ProjectKnowledgeMemoryTraceWithoutCitationEvidence+semanticProjectKnowledgeRecallSelectsOlderRelevantEntryForAgentPrompt,ProjectAgentToolsTest#memoryRecallToolCallsMemoryRecallPortAndDoesNotCreateEvidence' test
```

Result: 4 tests passed, BUILD SUCCESS.

## Acceptance Checks

- Older relevant confirmed knowledge can rank first over newer unrelated confirmed entries.
- The ranked L2 entry is passed into the Agent prompt through `ProjectAgentRequest.projectKnowledge()`.
- The rank 1 L2 `project_knowledge` trace is the same relevant entry.
- L2 project knowledge remains context-only and does not increase citation evidence counts.
- F022 memory observability still groups L1/L2/L3 memory and keeps memory separate from citation evidence.

## Residual Risk

The ranker computes embeddings on demand over a capped candidate pool. This is intentionally simple for the current single-project scale. If confirmed knowledge grows large, a future feature can add a dedicated memory vector index, but that should stay separate from Paper RAG to preserve the Memory Retrieval boundary.
