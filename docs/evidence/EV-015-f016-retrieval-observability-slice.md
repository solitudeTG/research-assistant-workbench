---
id: EV-015
doc_kind: evidence
status: active
created: 2026-05-12
updated: 2026-05-12
feature_ids: [F016]
---
# EV-015 F016 Retrieval Observability Slice

## Scope Verified

This evidence records the first F016 backend slice. It does not close the full Feature.

Implemented capability:

- Added `QueryRewritePlan.strategy` and `fallbackReason` so query rewrite behavior is observable instead of implicit.
- Added `RetrievalObservation`, `RetrievalBackendStats`, and `ZeroHitReason` as the first structured observation model for `paper_rag`.
- Extended `RagResult` with an optional observation while preserving the existing 3-argument constructor.
- `PaperRagService.retrieve(...)` now records per-backend query count, hit count, duration, rewrite strategy, merged/reranked/returned counts, and `NO_BACKEND_HITS` classification for empty backend results.
- `RetrievalTraceRepository.save(...)` now receives observation details in the rerank JSON payload without adding a new table.
- `ProjectAgentTools.paperRag(...)` publishes `retrieval.query.rewritten` and `retrieval.completed` trace events when an observation exists.
- `tool.completed.data` now carries `retrievalObservationSummary` so consumers do not need to parse `resultSummary`.
- Added startup warmup for `LocalVectorSearchPort` so already-indexed document chunks are restored into the in-memory vector index after backend restart.

## Evidence

Live diagnosis after a user rerun showed the observation data was useful enough to distinguish the actual failure mode:

- Recent `retrieval_trace.rerank_result_json.observation` rows for session `8` showed `NO_BACKEND_HITS` on many calls.
- Aggregate backend stats showed keyword retrieval returned hits, but vector retrieval returned `0` hits across the inspected calls.
- The local runtime was configured with `AI_VECTORSTORE_TYPE=none` and `AI_EMBEDDING_PROVIDER=none`.
- The application therefore used `LocalVectorSearchPort`, an in-memory vector index, while the database still contained indexed documents and chunks.
- Root cause: after backend restart, existing `document_chunk` rows remained in the database, but the local in-memory vector index was empty until a document was reprocessed.
- Fix slice: `LocalVectorIndexWarmup` rehydrates indexed documents into `LocalVectorSearchPort` on startup.

Follow-up live diagnosis after warmup showed the retrieval layer had recovered:

- Latest inspected session `9` had `8` retrieval calls, `0` zero-hit calls, `90` vector hits, and `70` returned chunks.
- Previous inspected session `8` had `25` retrieval calls, `16` zero-hit calls, `0` vector hits, and `29` returned chunks.
- Remaining mismatch was no longer retrieval: the latest answer had persisted paper evidence but was stored as `REFUSAL/NONE` because `EvidenceBoundaryService` treated local chunks below score `0.35` as no evidence.
- Fix slice: evidence boundary now treats empty scoped paper results as `NONE`, but any non-empty scoped paper result below the sufficient threshold as `WEAK`.
- Startup warmup was also hardened to skip safely when no `LocalVectorSearchPort` bean exists, which keeps pgvector/test contexts from failing application startup.

## Verification Commands

Focused backend verification:

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=LocalVectorIndexWarmupTest,QueryRewriteServiceTest,PaperRagServiceTest,ProjectAgentToolsTest' test
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,LocalVectorIndexWarmupTest' test
```

Result:

```text
BUILD SUCCESS
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

Harness validation:

```powershell
python scripts/knowledge_check.py
```

Result:

```text
ok
```

## Residual Risk

- This slice does not yet distinguish vector pre-scope and post-scope hits. Current stats use the existing search results, so `SCOPE_FILTERED_EMPTY` still needs a stronger test and likely vector search contract work.
- This slice does not yet link final persisted citations back to a retrieval observation id or tool-call index.
- No dedicated `retrieval_observation` table was added. This intentionally avoids an ADR-triggering storage decision until aggregation needs are proven.
- UI was not changed in this slice.
- The current live container must be rebuilt/restarted before the warmup code can affect local manual testing.
