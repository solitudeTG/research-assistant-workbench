---
id: F023-SPEC
doc_kind: spec
status: completed
created: 2026-05-17
updated: 2026-05-17
feature_ids: [F023]
---
# F023 L2 Knowledge Semantic Retrieval Spec

## Problem

F021.1 made confirmed `knowledge_entry` rows enter the next answer, but the selection policy is recency-only. That is safe and bounded, but it can load recently edited unrelated knowledge and miss older high-value findings.

## Retrieval Contract

Input:

- `projectId`
- current user question
- bounded candidate limit
- bounded selected limit

Output:

- ordered confirmed project knowledge entries
- per-entry score
- rank
- reason metadata

Candidate source:

- `knowledge_entry`
- `project_id = ?`
- `archived = false`
- `evidence_status = 'confirmed'`

Ranking:

- semantic relevance: primary signal, computed from question and `title + content`
- evidence confidence: confirmed entries receive a stable confidence boost
- recency: bounded tie-breaker, not the primary reason

Non-goals:

- no Paper RAG reuse
- no `document_chunk`
- no `evidence_source` creation
- no persistent vector index or schema migration in this slice
- no automatic candidate confirmation

## Trace Contract

For selected project knowledge memory hits:

- `memoryLayer=L2`
- `sourceType=project_knowledge`
- `contextOnly=true`
- `rank`
- `score`
- `semanticScore`
- `recencyScore`
- `evidenceScore`
- `injectionMode=preloaded_prompt`
- `reason=semantic_confirmed_project_knowledge`

## Verification

- A focused backend test inserts at least six confirmed entries, where an older relevant entry and a newer unrelated entry compete; the older relevant entry must be selected.
- Evidence boundary tests continue to assert no memory-only evidence rows.
- F022 frontend projection tests continue to pass.

## Outcome

F023 implements an on-the-fly ranker over confirmed `knowledge_entry` rows using the existing `EmbeddingModel`. The selected records are injected as L2 project knowledge context and traced with explainable score components. No Paper RAG, citation evidence, vector table, or schema migration was introduced.
