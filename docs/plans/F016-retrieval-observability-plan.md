---
id: PLAN-F016
doc_kind: plan
status: active
updated: 2026-05-12
feature_ids: [F016]
---
# F016 Retrieval Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for behavior changes and `superpowers:subagent-driven-development` only if splitting independent implementation slices. Do not start by changing UI.

## Goal

Add durable, structured observability to the Paper RAG path so the system can explain query rewrite, hybrid retrieval, scope filtering, reranking, zero-hit reasons, and final citation linkage.

## Architecture

Keep the current answer path intact:

```text
ProjectAgentTools.paperRag
-> PaperRagService.retrieve
-> QueryRewriteService
-> KeywordSearchRepository / VectorSearchPort / MetadataSearchRepository
-> merge + rerank
-> RagResult
-> evidence/citation persistence
```

Add a retrieval observation model alongside this flow. Observation write failures must not fail the answer.

## File Structure

Likely files:

- Modify: `src/main/java/com/researchassistant/orchestrator/ProjectAgentTools.java`
- Modify: `src/main/java/com/researchassistant/rag/PaperRagService.java`
- Modify: `src/main/java/com/researchassistant/rag/QueryRewritePlan.java`
- Modify: `src/main/java/com/researchassistant/rag/QueryRewriteService.java`
- Modify: `src/main/java/com/researchassistant/rag/VectorSearchPort.java`
- Modify: `src/main/java/com/researchassistant/rag/PgVectorSearchPort.java`
- Modify: `src/main/java/com/researchassistant/rag/LocalVectorSearchPort.java`
- Modify or extend: `src/main/java/com/researchassistant/rag/RagResult.java`
- Modify or extend: `src/main/java/com/researchassistant/rag/RetrievalTraceRepository.java`
- Possibly create: `src/main/java/com/researchassistant/rag/RetrievalObservation.java`
- Possibly create: `src/main/java/com/researchassistant/rag/RetrievalBackendStats.java`
- Possibly create: `src/main/java/com/researchassistant/rag/ZeroHitReason.java`
- Possibly create: `src/main/java/com/researchassistant/rag/RetrievalObservationRepository.java`
- Test: `src/test/java/com/researchassistant/rag/PaperRagServiceTest.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectAgentToolsTest.java`
- Test: `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`

## Task 1: Characterize Current Zero-Hit Behavior

- [x] Add focused tests around current `PaperRagService.retrieve(...)` behavior for empty backend hits.
- [ ] Add a test showing vector pre-scope hits can become zero after allowed document filtering.
- [x] Add a test showing `ProjectAgentTools.paperRag(...)` currently only publishes a free-text `resultSummary`.
- [x] Run:

```powershell
mvn -Dtest=PaperRagServiceTest,ProjectAgentToolsTest test
```

Result: current slice now passes after adding the minimum structured observation and trace event payloads. Scope-filtered vector behavior remains pending.

## Task 2: Define Observation Value Objects

- [x] Create `ZeroHitReason` enum.
- [x] Create `RetrievalBackendStats` for backend name, query count, pre-scope hits, post-scope hits, duration.
- [x] Create `RetrievalObservation` with query/rewrite data, backend stats, counts, and zero-hit reason.
- [ ] Keep snippets bounded.
- [x] Add unit tests for `NO_BACKEND_HITS` zero-hit classification.

Verification:

```powershell
mvn -Dtest=PaperRagServiceTest test
```

## Task 3: Expose Query Rewrite Strategy

- [x] Extend `QueryRewritePlan` with `strategy` and optional `fallbackReason`.
- [x] Keep existing `originalOnly(...)` behavior compatible.
- [x] Make `QueryRewriteService` return observable `original_only` when rewrite fails.
- [ ] Test non-CJK original-only and blank query.
- [x] Test CJK rewrite and rewrite failure fallback.

Verification:

```powershell
mvn -Dtest=QueryRewriteServiceTest test
```

## Task 4: Measure Backend Retrieval Stats

- [x] In `PaperRagService`, collect stats per backend across rewritten queries.
- [x] Add local vector index warmup for restart recovery when using `LocalVectorSearchPort`.
- [ ] Track true keyword, vector, and metadata pre/post scope counts.
- [ ] For vector search, introduce a result shape that can distinguish pre-scope candidates from scoped returned chunks.
- [ ] Avoid changing ranking semantics unless tests prove the existing behavior must change.
- [x] Store merged/reranked/returned counts in observation.

Verification:

```powershell
mvn -Dtest=LocalVectorIndexWarmupTest,PaperRagServiceTest test
```

## Task 5: Persist or Attach Observation

Choose the lightest implementation that satisfies diagnostics:

- [x] Option A: extend `retrieval_trace` JSON with observation details.
- [ ] Option B: create `retrieval_observation` table and repository.

Decision rule:

- Use Option A if tests only require per-run retrieval replay and no aggregate SQL queries.
- Use Option B if we need dashboard-style aggregation by zero-hit reason, backend, or project over time.

If Option B is selected, create an ADR before implementation.

Verification:

```powershell
mvn -Dtest=PaperRagServiceTest test
```

## Task 6: Publish Structured Trace Events

- [x] Publish `retrieval.query.rewritten` with rewrite plan.
- [x] Publish or enhance `retrieval.completed` with backend stats and zero-hit reason.
- [x] Enhance `tool.completed` with `retrievalObservationSummary`.
- [x] Keep `resultSummary` for readability, but do not make UI depend on parsing it.

Verification:

```powershell
mvn -Dtest=ProjectAgentToolsTest,ProjectRunEventFlowTest test
```

## Task 7: Link Observations to Final Citations

- [ ] Ensure final paper evidence/citation records carry `chunkId`, `documentId`, `sourceId`, `runId`, and `answerId`.
- [ ] Add `toolCallIndex` or `retrievalObservationId` when feasible.
- [ ] Test that final citations can be traced back to a retrieval observation.

Verification:

```powershell
mvn -Dtest=ProjectEvidenceBoundaryTest test
```

## Task 7A: Bound Agent Paper RAG Tool Calls

- [x] Deduplicate identical `paper_rag` queries within one Agent answer run.
- [x] Add a conservative per-answer `paper_rag` backend call budget of 3.
- [x] Return structured skip metadata instead of calling the backend after the budget is exhausted.
- [ ] Make the budget configurable only if live data shows 3 is too low.

Verification:

```powershell
mvn -Dtest=ProjectAgentToolsTest test
```

## Task 8: Add Safe Diagnostics Endpoint Only If Needed

- [x] If the frontend or manual diagnosis needs read access, add a project-scoped read-only endpoint.
- [x] Keep it behind existing project/session/run path boundaries.
- [x] Return summaries by default; full details only when explicitly requested.

Candidate endpoint:

```text
GET /api/projects/{projectId}/sessions/{sessionId}/retrieval-diagnostics
```

Verification:

```powershell
mvn -Dtest=RetrievalDiagnosticsControllerTest,TraceControllerTest test
```

## Task 9: Evidence and Closeout

- [x] Run focused backend tests.
- [x] Run full relevant backend test slice.
- [x] Run frontend model and syntax checks for the diagnostics workspace.
- [x] Run Harness validation.
- [x] Update existing evidence doc `docs/evidence/EV-015-f016-retrieval-observability-slice.md`.
- [x] Update F016 status and `docs/BACKLOG.md`.

Commands:

```powershell
mvn -Dtest=PaperRagServiceTest,QueryRewriteServiceTest,ProjectAgentToolsTest,ProjectRunEventFlowTest,ProjectEvidenceBoundaryTest test
python scripts/knowledge_check.py
git diff --check
```

## Open Decisions

- Whether to start with extended `retrieval_trace` JSON or create a dedicated `retrieval_observation` table.
- Whether scope-aware vector filtering should be part of F016 implementation or a follow-up optimization Feature after metrics prove the problem.
- Whether UI consumption belongs to F016 or a follow-up UI Feature. Default recommendation: F016 produces backend observation and events; UI polish follows separately.
- Whether local deployments should keep using warmed in-memory vector search, or move to a durable vector store before broader retrieval tuning.

## Rollback

- Observation writes must be best-effort and removable without changing answer correctness.
- If event payload expansion causes frontend issues, keep old `resultSummary` and gate new structured fields behind additive payload keys.
- If migration is introduced, provide a down migration or make the first implementation JSON-only.
