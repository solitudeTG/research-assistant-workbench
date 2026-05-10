---
id: F009
doc_kind: feature
status: completed
owner: codex
created: 2026-05-09
updated: 2026-05-10
parent_feature: F002
---
# Feedback Score Loop

## Goal

Make answer thumbs-up/thumbs-down feedback observable and reusable: answer-level feedback is recorded, selected same-project evidence and linked chunks receive a lightweight score delta, and later retrieval can use that score through an explainable bounded formula.

## Current Status

completed.

## Scope

- In scope: `POST /api/projects/{projectId}/answers/{answerId}/feedback`.
- In scope: validate `rating` as exactly `up` or `down`.
- In scope: record answer-level feedback in `answer_feedback`.
- In scope: preserve `reason` or `note`; down feedback can carry a reason and up feedback can optionally carry a note.
- In scope: when `evidenceSourceIds` are supplied, update only evidence rows under the same `projectId` and `answerId`.
- In scope: update linked paper chunks only when the selected evidence belongs to the same project and maps through `source_document.indexed_document_id`.
- In scope: publish `feedback.applied`.
- In scope: apply chunk `feedback_score` to subsequent keyword/vector retrieval ordering with `relevance + clamp(feedbackScore * 0.05, -0.2, 0.2)`.
- In scope: keep the local in-memory vector index synchronized when feedback mutates chunk scores.
- Out of scope: UI, F010 frontend wiring, long-term personalization, user profile modeling, and complex reranking models.

## Acceptance Criteria

- `rating=up` increases selected same-project evidence and linked chunk scores.
- `rating=down` decreases selected same-project evidence and linked chunk scores and stores the supplied reason/note.
- Missing or empty `evidenceSourceIds` records answer-level feedback only and does not mutate evidence or chunks.
- Cross-project evidence IDs are ignored and do not mutate another project's evidence or chunks.
- `feedback.applied` event payload reports only same-project evidence IDs that were actually applied.
- `feedback.applied` is published after the feedback application.
- Retrieval scoring uses the bounded explainable formula instead of the previous unbounded keyword-only multiplier.
- Local vector search uses the same bounded feedback formula and receives feedback deltas from both project answer feedback and the legacy message feedback route.
- Legacy `POST /api/messages/{messageId}/feedback` behavior continues to pass.
- Acceptance command passes:
  `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectFeedbackServiceTest,FeedbackControllerTest,PaperRagServiceTest' test`

## Contracts

- API: `POST /api/projects/{projectId}/answers/{answerId}/feedback`.
- Request:
  - `rating`: `up` or `down`.
  - `reason`: optional string, mainly for down feedback.
  - `note`: optional string.
  - `evidenceSourceIds`: optional list of evidence IDs.
- Response:
  - `status`: `APPLIED`.
  - `rating`, `feedbackScore`, `updatedEvidenceSourceCount`, and `updatedChunkCount`.
- Events: `feedback.applied`.
- Data: `answer_feedback`, `evidence_source.feedback_score`, `document_chunk.feedback_score`.
- Retrieval formula: `finalScore = relevanceScore + clamp(feedbackScore * 0.05, -0.2, 0.2)`.

## Evidence

- Evidence record: [EV-008-f009-feedback-score-loop.md](../evidence/EV-008-f009-feedback-score-loop.md)

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## Next Step

Start F010 three-column workbench UI. Do not add broad source search, web retrieval, or personalization inside F010 unless its Feature page expands scope.
