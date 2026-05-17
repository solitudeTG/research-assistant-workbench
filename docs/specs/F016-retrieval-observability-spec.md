---
id: SPEC-F016
doc_kind: spec
status: accepted
updated: 2026-05-17
feature_ids: [F016]
---
# F016 Retrieval Observability Spec

## 2026-05-17 Closeout Note

This spec is accepted as the design basis for the completed F016 interview-demo scope. The landed implementation chose to extend existing `retrieval_trace` JSON plus run events and a session-scoped diagnostics endpoint, rather than adding a dedicated `retrieval_observation` table.

Deferred items remain explicit follow-ups, not hidden completion claims: exact citation-to-retrieval-observation ids, cross-session trend analytics, and true pre/post scope counts for keyword and metadata repositories.

## Purpose

F016 defines long-lived observability for the project-local Paper RAG path. The system must be able to explain retrieval quality problems with structured evidence instead of relying on UI screenshots, free-text `resultSummary`, or one-off debug logs.

Primary diagnostic questions:

- Why did a `paper_rag` call return `0 scoped chunk(s)`?
- Did the Agent call `paper_rag` too many times?
- Did query rewrite produce useful retrieval queries?
- Which backend path produced hits: keyword, vector, metadata, or none?
- Did scope filtering remove otherwise valid vector candidates?
- Which retrieved chunks became final citations?

## Non-goals

- Do not redesign the full RAG algorithm in this Feature.
- Do not add a generic logging/APM product.
- Do not expose raw trace spam in the default conversation UI.
- Do not store unbounded chunk content or full model prompts as observability records.
- Do not make the answer fail when observability persistence fails.

## Observation Model

Each `paper_rag` call produces one `RetrievalObservation`.

Recommended logical shape:

```json
{
  "observationId": "ro_01",
  "projectId": "project_01",
  "sessionId": "session_01",
  "runId": "run_01",
  "messageId": "msg_01",
  "answerId": "ans_01",
  "toolCallIndex": 3,
  "toolName": "paper_rag",
  "originalQuery": "请总结这篇论文的核心贡献",
  "maxResults": 5,
  "boundedMaxResults": 5,
  "allowedDocumentCount": 2,
  "hasScopedPaperEvidence": true,
  "rewrite": {
    "strategy": "cjk_llm_rewrite",
    "retrievalQueries": [
      "请总结这篇论文的核心贡献",
      "What are the core contributions of this paper?",
      "satellite beamforming interference mitigation"
    ],
    "keywords": ["satellite", "beamforming", "interference mitigation"]
  },
  "backendStats": {
    "keyword": { "queryCount": 3, "preScopeHits": 2, "postScopeHits": 2, "durationMs": 21 },
    "vector": { "queryCount": 3, "preScopeHits": 15, "postScopeHits": 0, "durationMs": 48 },
    "metadata": { "queryCount": 3, "preScopeHits": 0, "postScopeHits": 0, "durationMs": 7 }
  },
  "mergedCandidateCount": 2,
  "rerankedChunkCount": 2,
  "returnedScopedChunkCount": 2,
  "zeroHitReason": null,
  "topChunks": [
    {
      "chunkId": 296,
      "documentId": 12,
      "sourceId": "source_12",
      "chunkIndex": 8,
      "score": 0.82,
      "retrievalModes": ["keyword"],
      "snippet": "bounded excerpt..."
    }
  ],
  "createdAt": "2026-05-12T10:00:00Z"
}
```

## Zero-Hit Reason

`zeroHitReason` is required when `returnedScopedChunkCount == 0`.

Allowed values:

```text
NO_SCOPED_EVIDENCE
QUERY_EMPTY_OR_INVALID
NO_BACKEND_HITS
SCOPE_FILTERED_EMPTY
RERANK_EMPTY
TOOL_ERROR
UNKNOWN
```

Rules:

- `NO_SCOPED_EVIDENCE`: `ProjectEvidenceScope` has no indexed scoped paper evidence before retrieval starts.
- `QUERY_EMPTY_OR_INVALID`: normalized query is blank or cannot safely be searched.
- `NO_BACKEND_HITS`: keyword, vector, and metadata all produce zero pre-scope hits.
- `SCOPE_FILTERED_EMPTY`: at least one backend produces pre-scope hits, but all are removed by project scope filtering.
- `RERANK_EMPTY`: scoped backend hits exist, but merge/rerank returns no usable chunk.
- `TOOL_ERROR`: `paper_rag` throws or degrades due to an exception.
- `UNKNOWN`: temporary fallback only; tests should minimize this path.

## Backend Responsibilities

### ProjectAgentTools

`ProjectAgentTools.paperRag(...)` owns tool-level observation identity:

- assign `toolCallIndex` within the run,
- record original tool arguments,
- record scope availability before calling `PaperRagService`,
- attach `runId`, `messageId`, and `answerId`,
- publish structured `tool.completed.data.retrievalObservationSummary`.

It must not infer backend hit reasons from free-text summaries.

### QueryRewriteService

`QueryRewriteService` must expose a structured `QueryRewritePlan` already suitable for observation:

- original question,
- retrieval queries,
- keywords,
- strategy,
- fallback reason when rewrite fails.

If rewrite fails, the plan should still be observable as `original_only` with a bounded failure reason.

### PaperRagService

`PaperRagService.retrieve(...)` owns retrieval-level stats:

- per rewritten query backend calls,
- per backend hit counts,
- merge/rerank counts,
- top chunk summaries,
- trace save.

The service should return or persist enough structured detail for `ProjectAgentTools` and `SupervisorService` to publish meaningful trace events.

### Vector Search

Vector search must expose pre-scope and post-scope counts.

Current risk: if vector search retrieves global topK first and filters project scope afterward, a project may see `0 scoped chunk(s)` even when the vector store had globally similar chunks. Observation must make that visible.

Later optimization may push scope filtering into the vector store query. That optimization is outside the minimum F016 deliverable but should be informed by F016 metrics.

## Persistence

Minimum acceptable first implementation:

- Extend existing `retrieval_trace` JSON payloads with structured observation fields.
- Keep bounded snippets only.
- Store no unbounded raw prompt or full document text.

Preferred if aggregation is needed:

- Add `retrieval_observation` table with stable columns for project/run/tool/query counts and `details_json` for bounded nested data.
- Keep `retrieval_trace` as compatibility/history if already used by workspace memory views.

If the implementation chooses a new table, write an ADR before coding the migration.

## Event Contract

F016 should reuse F015 event envelope.

Recommended enhanced events:

### `retrieval.query.rewritten`

```json
{
  "toolCallIndex": 3,
  "originalQuery": "这篇论文研究了什么？",
  "strategy": "cjk_llm_rewrite",
  "retrievalQueries": ["...", "..."],
  "keywords": ["...", "..."]
}
```

### `retrieval.completed`

```json
{
  "toolCallIndex": 3,
  "retrievalMode": "HYBRID_RAG",
  "backendStats": {
    "keyword": { "preScopeHits": 2, "postScopeHits": 2 },
    "vector": { "preScopeHits": 15, "postScopeHits": 0 },
    "metadata": { "preScopeHits": 0, "postScopeHits": 0 }
  },
  "mergedCandidateCount": 2,
  "rerankedChunkCount": 2,
  "returnedScopedChunkCount": 2,
  "zeroHitReason": null
}
```

### `tool.completed`

`tool.completed.data.resultSummary` may remain human-readable, but consumers must rely on structured fields:

```json
{
  "toolName": "paper_rag",
  "resultSummary": "paper_rag returned 2 scoped chunk(s)",
  "retrievalObservationSummary": {
    "toolCallIndex": 3,
    "returnedScopedChunkCount": 2,
    "zeroHitReason": null
  }
}
```

## Citation Linkage

When final answer evidence is persisted, each paper citation should be linkable back to:

- `runId`,
- `answerId`,
- `retrievalObservationId` or `toolCallIndex`,
- `chunkId`,
- `documentId`,
- `sourceId`.

If the first implementation cannot add a direct foreign key, it must record enough IDs in JSON to reconstruct the relationship.

## Frontend Projection Principle

Default UI should show:

- total `paper_rag` calls,
- total retrieval queries,
- valid evidence hits,
- zero-hit count grouped by reason,
- top warnings such as `SCOPE_FILTERED_EMPTY`.

Default UI should not show every low-level backend attempt. Full observation detail belongs behind an explicit expand/debug affordance.

## Testing Strategy

Backend focused tests:

- `paper_rag` with no scoped evidence emits/stores `NO_SCOPED_EVIDENCE`.
- blank query emits/stores `QUERY_EMPTY_OR_INVALID`.
- all backends empty emits/stores `NO_BACKEND_HITS`.
- vector pre-scope hits removed by allowed document filtering emits/stores `SCOPE_FILTERED_EMPTY`.
- successful hybrid retrieval records per-backend counts and final returned chunks.
- query rewrite failure records `original_only` fallback instead of hiding rewrite failure.
- final evidence/citation persistence can be linked to observation identifiers.

Frontend/model tests, when UI consumes this Feature:

- observation summary folds into run trace without rendering 48 raw rows by default.
- zero-hit reasons are grouped.
- full detail remains available only when explicitly expanded.

Harness validation:

```powershell
python scripts/knowledge_check.py
```

## Acceptance Evidence

Implementation closeout should record:

- focused backend test commands and PASS output,
- any migration command or rollback note,
- sample observation for a successful retrieval,
- sample observation for a `SCOPE_FILTERED_EMPTY` zero-hit,
- confirmation that main answer path degrades safely if observation persistence fails.
