---
id: EV-008
doc_kind: evidence
status: completed
scope: feature
feature_ids: [F009]
created: 2026-05-10
---
# F009 Feedback Score Loop Evidence

## Evidence

## Commands

- Red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectFeedbackServiceTest,FeedbackControllerTest,PaperRagServiceTest' test`
- Review red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectFeedbackServiceTest,LocalVectorSearchPortTest' test`
- Acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectFeedbackServiceTest,FeedbackControllerTest,PaperRagServiceTest' test`
- Parent acceptance plus local-vector guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectFeedbackServiceTest,FeedbackControllerTest,PaperRagServiceTest,LocalVectorSearchPortTest' test`
- Parent regression guardrails: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest,ProjectRunEventFlowTest,ProjectEvidenceBoundaryTest,KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- Harness check: `python scripts\knowledge_check.py`

## Results

- Red result: test compilation failed because `ProjectAnswerFeedbackRequest`, `ProjectAnswerFeedbackResult`, and `RetrievalFeedbackScoring` did not exist. This proved the new F009 tests targeted missing project answer feedback and retrieval scoring behavior before implementation.
- Acceptance result: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent acceptance plus local-vector guardrail result: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent regression guardrails result: `Tests run: 46, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Review red result: newly added hardening tests failed before fixes:
  - null entries in `evidenceSourceIds` returned `400` before request normalization ignored nulls.
  - `feedback.applied` included a cross-project evidence ID before event payloads were narrowed to applied same-project IDs.
  - local vector search ignored feedback score before `LocalVectorSearchPort` used the bounded formula.
  - oversized numeric `citation_meta_json.chunkId` raised a PostgreSQL bigint cast error before guarded parsing.
  - legacy `/api/messages/{messageId}/feedback` updated DB chunk feedback but did not notify the local vector index before the legacy path called `VectorSearchPort.applyChunkFeedback`.
- Harness check result: `knowledge_check: ok`.

## Artifacts

- `ProjectFeedbackServiceTest` covers:
  - up feedback increasing selected same-project evidence and linked chunk scores.
  - down feedback decreasing selected same-project evidence and linked chunk scores while storing the reason.
  - feedback without `evidenceSourceIds` recording only answer-level feedback.
  - cross-project evidence IDs not mutating another project's evidence or chunks.
  - `feedback.applied` publication with project, answer, rating, evidence count, and chunk count.
  - `feedback.applied` event payload containing only same-project evidence IDs that were actually applied.
  - project feedback endpoint rejecting unknown ratings with `400 Bad Request`.
  - project feedback endpoint applying valid ratings.
  - null `evidenceSourceIds` entries being ignored.
  - malformed or oversized citation chunk IDs not breaking answer feedback application.
  - legacy message feedback notifying vector search feedback synchronization.
- `FeedbackControllerTest` continues to cover the legacy `/api/messages/{messageId}/feedback` route.
- `PaperRagServiceTest` covers the bounded retrieval scoring helper used by keyword/vector retrieval.
- `LocalVectorSearchPortTest` covers local vector scoring with stored feedback and in-memory feedback delta application.

## Implementation Notes

- Added project answer feedback request/result records and controlled invalid-rating errors.
- Extended `FeedbackController` with `POST /api/projects/{projectId}/answers/{answerId}/feedback` while preserving the legacy message feedback route.
- Extended `FeedbackService` to insert `answer_feedback`, update selected evidence/chunk scores, and publish `feedback.applied`.
- Added same-project evidence/chunk feedback application to `EvidenceSourceRepository`, including guarded citation `chunkId` parsing before bigint casts.
- Added `RetrievalFeedbackScoring` with `relevance + clamp(feedbackScore * 0.05, -0.2, 0.2)`.
- Replaced the previous keyword-only `feedback_score * 0.15` scoring with the bounded formula, and applied the same helper to pgvector-backed and local vector results.
- Added `VectorSearchPort.applyChunkFeedback` as a no-op default and implemented local vector in-memory feedback delta application so project and legacy feedback routes keep local search ranking current.
- No schema migration was required; F009 uses existing `answer_feedback`, `evidence_source.feedback_score`, and `document_chunk.feedback_score` fields.

## Known Limitations

- F009 does not add UI controls; F010 owns frontend wiring.
- F009 records one answer feedback row per request and does not implement undo, deduplication, or per-user personalization.
- Event publication happens through the existing `WorkbenchEventPublisher` abstraction; this slice does not add after-commit event dispatch.
- Metadata title hits have no chunk-level feedback signal, so they use zero feedback adjustment.

## Rollback

Revert the feedback package additions, `EvidenceSourceRepository` feedback application method, retrieval scoring helper/repository scoring changes, F009 tests, and Harness document updates to return the codebase to the F008 candidate confirmation state.

## Notes

No additional notes.
