---
id: EV-004
doc_kind: evidence
status: completed
date: 2026-05-10
created: 2026-05-10
scope: feature
feature_ids: [F005]
---
# F005 Project Source Status Machine Evidence

## Commands

See the detailed command/result trail below. The latest review-fix verification used:

- `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`

## Results

- Latest focused review-fix result: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Latest required acceptance result: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent-session F005 acceptance result: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent-session F004 event guardrail result: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent-session Harness check: `knowledge_check: ok`.

## Artifacts

- Feature: [F005-project-source-status-machine.md](../features/F005-project-source-status-machine.md)
- Tests: `ProjectSourceStatusMachineTest`, `DocumentIngestServiceTest`, `DocumentControllerTest`, `DocumentControllerStatusTest`, `DocumentProcessingJobTest`, `DocumentProcessingJobFailureTest`
- Production paths: project source API, source status transitions, event publication, and note retry handling.

## Evidence

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest' test`
- Result: red during TDD before production changes. The new tests failed with `Status expected:<202> but was:<404>` and `Status expected:<503> but was:<404>` because `/api/projects/{projectId}/sources` did not exist yet.
- Coverage: project-scoped file import, web import, project-only list/get boundaries, parsing failure stage persistence, retry of failed source, and `source.status.changed` event publication.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Result: green after implementation. Maven reported `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: F005 acceptance plus legacy document upload/status/processing compatibility tests.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: red during spec review fixes. Maven reported failures showing direct-list contract mismatch (`No value at JSON path "$[0].id"`), unsupported JSON source type returning `503` instead of `400`, and stage failures still recorded as `parsing` instead of `indexing`, `extracting`, or `depositing`.
- Coverage: review findings for direct `SourceDocument[]` list response, unsupported source-type validation before insert, parsing/fetching failure-stage preservation, and indexing/extracting/depositing failure-stage tracking.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: green after spec review fixes. Maven reported `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: focused review-fix regression tests.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Result: green after spec review fixes. Maven reported `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: required F005 acceptance command plus legacy document compatibility tests.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: red during spec re-review fixes. Maven reported failures showing list objects still exposed extra `sourceId` and missing JSON `type` still defaulted to `note`, returning `503` instead of `400`.
- Coverage: explicit JSON source type requirement and direct `SourceDocument[]` list response shape.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: green after spec re-review fixes. Maven reported `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: focused regression tests for explicit source type and list contract shape.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Result: green after spec re-review fixes. Maven reported `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: required F005 acceptance command plus legacy document compatibility tests.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest' test`
- Result: green F004 guardrail after source-scoped event envelope/publisher adjustments. Maven reported `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: existing event envelope, in-memory/Redis publisher behavior, and SSE projection behavior remain intact.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: red during note-path review fixes. Maven reported one failure where note retry incorrectly invoked `fileStorage.resolve` with `note:Recovered note body`, and one error where JSON note import without a title inserted `null` into non-null `source_document.title`.
- Coverage: JSON note happy path with default title and failed note retry through the text pipeline without file storage.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: green after note-path review fixes. Maven reported `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: focused note import/retry regression tests plus existing F005 service/status-machine coverage.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Result: green after note-path review fixes. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: required F005 acceptance command plus legacy document compatibility tests.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: red during retry provenance review fix. Maven reported `Tests run: 14, Failures: 1, Errors: 0, Skipped: 0`; the new multipart-note retry test failed because `fileStorage.resolve(...)` was not invoked for a failed uploaded `.txt` source.
- Coverage: file-backed non-PDF note retry must use the file pipeline instead of treating the storage path as inline note content.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentIngestServiceTest' test`
- Result: green after retry provenance review fix. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: JSON notes with `note:` URI marker retry through the text pipeline; file-backed `note` sources without that marker retry through the file pipeline.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Result: green after retry provenance review fix. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: required F005 acceptance command plus legacy document compatibility tests.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Result: green in the parent session after spec and quality review approval. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: final F005 acceptance command before commit.

- Command: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest' test`
- Result: green in the parent session. Maven reported `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Coverage: F004 event envelope and SSE projection guardrail after F005 source-scoped event changes.

- Command: `python scripts\knowledge_check.py`
- Result: `knowledge_check: ok`.
- Coverage: Harness Feature/Evidence/BACKLOG updates validate before commit.

## Notes

- F005 keeps the actual extraction/index/deposit work minimal and stage-based; it does not implement F006 Agent orchestration, F007 evidence boundaries, F008 candidates, F009 feedback, or F010 UI.
- The event envelope now has a source-specific pending factory and preserves explicit null payload values for fields such as `failureStage` and `errorMessage`.
- JSON note import uses `"Untitled note"` when title is omitted, and stores retryable note content in the existing `source_document.uri` field with a `note:` marker. File-backed sources, including uploaded non-PDF notes, keep normal storage paths and retry through the file pipeline.
- Rollback: revert the F005 code/test/doc changes to remove project source endpoints and status transitions while leaving prior F003/F004 behavior intact.
