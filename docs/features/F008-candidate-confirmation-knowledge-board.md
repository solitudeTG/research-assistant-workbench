---
id: F008
doc_kind: feature
status: completed
owner: codex
created: 2026-05-09
updated: 2026-05-10
parent_feature: F002
---
# Candidate Confirmation and Knowledge Board

## Goal

Build the durable knowledge write boundary: AI or ingestion paths may create `KnowledgeCandidate` drafts, but only user confirmation, edit-and-accept, or manual board creation may write `KnowledgeEntry` records.

## Current Status

completed.

## Scope

- In scope: candidate list by answer and by project.
- In scope: candidate accept, edit-and-accept, mark-unverified, and ignore actions.
- In scope: knowledge board section listing, manual entry creation, patch/update/move, and archive via `DELETE`.
- In scope: `candidate.created` and `knowledge.entry.created` event publishing.
- In scope: minimal V7 schema migration to complete the V5 candidate and knowledge-board contract.
- Out of scope: feedback scoring, F010 UI, candidate generation model quality, broad source search, and web retrieval.

## Acceptance Criteria

- Creating a candidate does not create a `KnowledgeEntry`.
- Only candidate accept, candidate edit-and-accept, or manual board creation writes a `KnowledgeEntry`.
- Candidate terminal actions are pending-only: accepted, edited-accepted, marked-unverified, and ignored candidates cannot be relabeled or accepted again.
- Candidate statuses are exactly `pending`, `accepted`, `edited_accepted`, `marked_unverified`, and `ignored`.
- Knowledge board sections are exactly `current_candidates`, `core_concept`, `method_route`, `confirmed_finding`, and `open_question`.
- Deleting a knowledge board entry archives it without hard delete.
- `candidate.created` is published when a candidate is created.
- `knowledge.entry.created` is published when a knowledge entry is created.
- Acceptance command passes:
  `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`

## Contracts

- API:
  - `GET /api/projects/{projectId}/answers/{answerId}/candidates`
  - `GET /api/projects/{projectId}/candidates`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/accept`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/edit-and-accept`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/mark-unverified`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/ignore`
  - `GET /api/projects/{projectId}/knowledge-board`
  - `POST /api/projects/{projectId}/knowledge-board/entries`
  - `PATCH /api/projects/{projectId}/knowledge-board/entries/{entryId}`
  - `DELETE /api/projects/{projectId}/knowledge-board/entries/{entryId}`
- Events: `candidate.created`, `knowledge.entry.created`.
- Data: `knowledge_candidate`, `knowledge_entry`.
- Boundary: candidate creation is draft-only; confirmed knowledge writes require explicit user action or manual entry creation.

## Evidence

- Evidence record: [EV-007-f008-candidate-confirmation-knowledge-board.md](../evidence/EV-007-f008-candidate-confirmation-knowledge-board.md)

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## Next Step

Start F009 feedback score loop with TDD. Do not fold F010 UI, broad source search, or web retrieval into F009.
