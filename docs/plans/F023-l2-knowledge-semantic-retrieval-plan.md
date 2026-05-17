---
id: F023-PLAN
doc_kind: plan
status: completed
created: 2026-05-17
updated: 2026-05-17
feature_ids: [F023]
---
# F023 L2 Knowledge Semantic Retrieval Plan

## Start Gate

Start Gate: needs feature

Task class:
- non-trivial

Risk triggers:
- Retrieval policy changes answer context.
- Memory/evidence boundary must remain strict.
- Future reviewers may confuse Memory Retrieval with Paper RAG.

Delegation decision:
- authorized. The user explicitly authorized subagents for complex tasks and independent vision review.

Required pre-work:
- Feature, spec, and plan anchors before implementation.

Allowed next action:
- Explore existing embedding/vector code and implement a small bounded L2 knowledge ranker.

## Vision Gate Entry

Original goal:
- Replace "latest 5" confirmed knowledge with relevant confirmed knowledge while keeping it context-only.

Smallest coherent path:
- On-the-fly ranking over confirmed `knowledge_entry` rows using existing `EmbeddingModel`; no vector table or schema migration.

Non-goals:
- No Paper RAG change.
- No persistent L2 vector store.
- No L3 promotion.
- No automatic confirmed writes.

Exit Gate source:
- Feature F023 plus this plan.

## Work Split

- Main agent owns Harness docs, branch/commit/push, integration, and final verification.
- Explorer A checks existing embedding/vector APIs and recommends the smallest backend design.
- Explorer B checks tests and trace/UI contracts for minimal changes.
- Independent vision guardian reviews final behavior before closeout.

## Implementation Steps

1. Add a L2 project knowledge recall service with bounded candidate read and scoring.
2. Extend `KnowledgeBoardRepository` only as needed to read confirmed candidates.
3. Wire `SupervisorService` to use the recall service instead of latest-only lookup.
4. Preserve F022 global cognition trace and context-only memory payloads.
5. Add backend tests for older relevant knowledge outranking newer unrelated knowledge.
6. Run F022 frontend tests to prove UI projection remains stable.
7. Capture Evidence, run independent vision review, commit, and push.

## Rollback

If semantic ranking destabilizes prompt context or evidence separation, revert the recall service wiring and restore `listConfirmedProjectKnowledge(projectId, MAX_TRACE_HITS)` while keeping F022 observability intact.

## Closeout

- Backend: `ProjectKnowledgeRecallService` ranks confirmed project knowledge candidates by embedding similarity, confirmed evidence confidence, and bounded recency.
- Orchestration: `SupervisorService` passes the ranked entries into `ProjectAgentRequest` and emits L2 `project_knowledge` memory hits with score components.
- Frontend: the trace model preserves F023 recall metadata in the existing F022 L2 memory group.
- Verification: recorded in [EV-022-f023-l2-knowledge-semantic-retrieval.md](../evidence/EV-022-f023-l2-knowledge-semantic-retrieval.md).
