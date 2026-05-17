---
id: F023
doc_kind: feature
status: completed
owner: solitudeTG
created: 2026-05-17
updated: 2026-05-17
parent_feature: F002
depends_on: [F022]
---
# L2 Knowledge Semantic Retrieval

## Goal

Upgrade L2 confirmed project knowledge from "latest 5" preload to bounded relevance-based memory retrieval. The system should choose the confirmed knowledge entries that best match the current question, keep them context-only, and expose why they were selected.

This is Memory Retrieval, not Paper RAG. L2 confirmed knowledge helps the Agent respect stable project context, constraints, and user-confirmed findings; it does not prove facts, create citations, or increase evidence strength.

## Scope

- In scope: rank confirmed `knowledge_entry` records by relevance to the current user question.
- In scope: combine semantic similarity with bounded project-knowledge heuristics such as evidence confidence and recency.
- In scope: keep the selected top entries bounded to the existing prompt/trace budget.
- In scope: publish L2 `project_knowledge` memory hits with rank, score, `injectionMode`, `reason`, and `contextOnly=true`.
- In scope: keep global cognition visible as L2 `global_knowledge`, but do not rank it as project confirmed knowledge.
- In scope: focused tests proving older relevant knowledge can outrank newer unrelated knowledge.
- Out of scope: Paper RAG changes, citation evidence changes, new vector table, L3 memory retrieval changes, L3-to-L2 promotion, automatic confirmed writes.

## Acceptance Criteria

- Given more than five confirmed knowledge entries, an older semantically relevant entry can be selected over a newer unrelated entry.
- Selected L2 project knowledge remains `sourceType=project_knowledge` and `contextOnly=true`.
- L2 project knowledge does not create `evidence_source` rows or change paper/web citation counts.
- Trace payload exposes relevance metadata sufficient to explain the selection.
- Global L2 cognition remains visible separately as `sourceType=global_knowledge`.
- Backend focused tests cover ranking, trace payload, and evidence boundary.
- Frontend model/UI tests continue to render the F022 L2 memory group without mixing it into citation evidence.
- Harness validation passes.

## Vision Anchor

The user challenged the usefulness of "latest 5" confirmed knowledge. F023 should make confirmed knowledge recall answer the real question: which user-confirmed project facts or constraints are relevant now? The smallest useful implementation is a bounded relevance ranker over `knowledge_entry`; a persistent knowledge vector store can wait until scale demands it.

## Contracts

- Data: read from existing `knowledge_entry`; no schema migration in this slice.
- Ranking: compute a bounded score from semantic similarity, evidence status, and recency.
- Events: reuse F022 memory trace fields; add explainable score/reason details if needed.
- Boundary: selected L2 knowledge is context only and never citation evidence.

## Links

- Spec: [F023-l2-knowledge-semantic-retrieval-spec.md](../specs/F023-l2-knowledge-semantic-retrieval-spec.md)
- Plan: [F023-l2-knowledge-semantic-retrieval-plan.md](../plans/F023-l2-knowledge-semantic-retrieval-plan.md)
- Evidence: [EV-022-f023-l2-knowledge-semantic-retrieval.md](../evidence/EV-022-f023-l2-knowledge-semantic-retrieval.md)
- Related Feature: [F022-memory-recall-observability.md](F022-memory-recall-observability.md)
- Related Feature: [F021-memory-self-learning-visualization.md](F021-memory-self-learning-visualization.md)
