---
id: F024-PLAN
doc_kind: plan
status: completed
created: 2026-05-17
updated: 2026-05-18
feature_ids: [F024]
---
# F024 L3 To L2 Candidate Promotion And Decay Plan

## Start Gate

Start Gate: needs feature/spec/plan

Task class:
- high-risk

Risk triggers:
- Changes the boundary between L3 memory, L2 candidates, and confirmed knowledge.
- Requires durable explanation so future work does not auto-confirm L3 memory.
- Spans database schema, repository behavior, orchestration, events, frontend model, and tests.

Delegation decision:
- authorized. The user explicitly requested subagents for complex tasks and an independent vision guardian.

Required pre-work:
- F024 Feature, spec, and plan before code changes.
- Read F021/F022/F023 context and existing memory/candidate code.

Allowed next action:
- Run read-only subagent exploration, then implement the smallest promotion/decay service.

## Vision Gate Entry

Original goal:
- Repeatedly helpful L3 memory should become visible as a reviewable L2 candidate, but never become confirmed knowledge without user confirmation.

Smallest coherent path:
- Answer-triggered L3 promotion service that records project-scoped L3 memory hits, creates or updates pending L3-sourced candidates after the repeated-hit threshold, and marks stale or duplicate pending L3 candidates decayed.

Non-goals:
- No automatic confirmed knowledge write.
- No confirmed L2 automatic deletion.
- No background scheduler.
- No L3 storage migration to project-scoped memory.
- No Paper RAG or citation evidence changes.

Exit Gate source:
- Feature F024 plus this plan and the spec.

## Work Split

- Main agent owns branch, Harness docs, integration, implementation, verification, and commit/push.
- Explorer A checks memory/candidate repository and schema boundary.
- Explorer B checks Supervisor/event/frontend trace boundary.
- Independent vision guardian reviews the final diff before closeout.

## Implementation Steps

1. Add a migration for L3 promotion metadata on `knowledge_candidate`.
2. Add a promotion state table plus a promotion hit table so repeated answer/run replays do not increment the hit count.
3. Extend `KnowledgeCandidateRecord` and repository mapping/payloads to expose source kind, source memory id, promotion counts/scores/reasons, and decay reason.
4. Add repository operations for finding/updating pending L3 candidates and decaying stale or duplicate candidates.
5. Add an `L3KnowledgePromotionService` that:
   - records project-scoped historical L3 memory hits with answer/run identity dedupe,
   - requires repeated hits before candidate creation,
   - skips duplicates against confirmed project knowledge,
   - updates existing pending L3 candidates on repeated hits,
   - decays stale or duplicate pending L3 candidates.
6. Wire the service in `SupervisorService` after `MemoryRecallResult` is known and before the run finishes.
7. Add backend tests for first-hit no-op, second-hit promotion, replay idempotency, dedupe/update, duplicate-confirmed decay, stale decay, and no evidence rows.
8. Extend frontend model tests so F024 metadata and decay events are projected without turning candidates into confirmed knowledge.
9. Run focused Maven, JS checks, Harness knowledge check, independent vision review, then commit and push.

## Rollback

If F024 pollutes confirmed knowledge or citation evidence, revert the `L3KnowledgePromotionService` wiring and migration-dependent repository methods. F021/F022/F023 memory observability and L2 semantic retrieval should remain intact.
