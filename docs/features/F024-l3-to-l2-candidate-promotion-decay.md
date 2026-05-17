---
id: F024
doc_kind: feature
status: completed
owner: solitudeTG
created: 2026-05-17
updated: 2026-05-18
parent_feature: F002
depends_on: [F022, F023]
---
# L3 To L2 Candidate Promotion And Decay

## Goal

Turn repeatedly useful L3 long-term memory recall into reviewable L2 knowledge candidates, while preserving the trust boundary that only user-confirmed knowledge becomes L2 confirmed project knowledge.

F024 should make the self-learning loop more credible: the system can notice that an L3 memory keeps helping, propose it as a candidate, show why it was proposed, and retire stale or duplicate pending candidates. It must not silently promote memory into confirmed knowledge.

## Scope

- In scope: create pending `knowledge_candidate` rows from repeated high-value L3 `memory_recall` hits.
- In scope: require multiple project-scoped hits before candidate creation.
- In scope: store promotion source metadata such as `source_memory_entry_id`, hit count, score, reason, and source kind.
- In scope: prevent duplicate pending candidates for the same L3 memory in the same project.
- In scope: decay pending L3-sourced candidates when they become stale or duplicate confirmed knowledge.
- In scope: publish observable candidate creation and decay events for the research process.
- Out of scope: automatic writes to confirmed `knowledge_entry`.
- Out of scope: deleting or auto-archiving confirmed L2 knowledge.
- Out of scope: changing Paper RAG, citation evidence, L3 recall ranking, or project-wide L3 storage.
- Out of scope: background schedulers; F024 decay is answer-triggered.

## Acceptance Criteria

- A single L3 hit does not create a candidate.
- The same L3 memory hit in the same project at least twice across distinct answer/run identities can create one pending L2 candidate.
- The generated candidate has `sourceKind=l3_memory`, `sourceMemoryEntryId`, promotion score/count/reason, and no citation evidence IDs.
- Repeated future hits update the same pending candidate instead of creating duplicates.
- If equivalent confirmed project knowledge already exists, the L3 hit is not promoted into a new pending candidate.
- Pending L3-sourced candidates can be marked `decayed` with a decay reason when stale or superseded by confirmed knowledge.
- User accept/edit-and-accept remains the only path from candidate to confirmed `knowledge_entry`.
- Research process state can display L3 promotion and decay without treating it as citation evidence.
- Focused backend and frontend model tests cover promotion, dedupe, decay, and evidence boundary.
- Harness validation passes.

## Vision Anchor

The user accepted the principle that L3 must never automatically become confirmed L2 knowledge. The point of F024 is not to make the system more autonomous at the cost of trust; it is to make repeated memory usefulness visible and reviewable. The smallest coherent implementation is L3-to-candidate promotion plus pending-candidate decay, not automatic knowledge confirmation.

## Contracts

- Promotion boundary: L3 memory may create or update a pending `knowledge_candidate`; it never creates a confirmed `knowledge_entry`.
- Project boundary: because `memory_entry` is session-oriented, project-scoped promotion is inferred from project run events and the current project answer context.
- Evidence boundary: L3-sourced candidates have no `evidence_source_ids`; memory context does not become paper/web citation evidence.
- Decay boundary: automatic decay only affects pending L3-sourced candidates by marking them `decayed` with a reason.
- Duplicate boundary: the F024 MVP blocks exact normalized title/content duplicates against confirmed project knowledge; semantic duplicate detection remains a later hardening option.

## Links

- Spec: [F024-l3-to-l2-candidate-promotion-decay-spec.md](../specs/F024-l3-to-l2-candidate-promotion-decay-spec.md)
- Plan: [F024-l3-to-l2-candidate-promotion-decay-plan.md](../plans/F024-l3-to-l2-candidate-promotion-decay-plan.md)
- Evidence: [EV-023-f024-l3-to-l2-candidate-promotion-decay.md](../evidence/EV-023-f024-l3-to-l2-candidate-promotion-decay.md)
- Related Feature: [F021-memory-self-learning-visualization.md](F021-memory-self-learning-visualization.md)
- Related Feature: [F022-memory-recall-observability.md](F022-memory-recall-observability.md)
- Related Feature: [F023-l2-knowledge-semantic-retrieval.md](F023-l2-knowledge-semantic-retrieval.md)
