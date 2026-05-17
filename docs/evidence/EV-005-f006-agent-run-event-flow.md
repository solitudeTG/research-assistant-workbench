---
id: EV-005
doc_kind: evidence
status: completed
date: 2026-05-10
created: 2026-05-10
scope: feature
feature_ids: [F006]
---
# F006 Agent Run Event Flow Evidence

## Evidence

### Commands

- Red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest' test`
- Green focused: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest' test`
- Acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest,SupervisorServiceLogicTest,TaskRouterTest,ChatControllerTest,ChatStreamControllerTest' test`
- Review red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest' test`
- Review green focused: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest' test`
- Review acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest,SupervisorServiceLogicTest,TaskRouterTest,ChatControllerTest,ChatStreamControllerTest' test`
- Parent acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest,SupervisorServiceLogicTest,TaskRouterTest,ChatControllerTest,ChatStreamControllerTest' test`
- Parent F004 guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest' test`
- Parent Harness check: `python scripts\knowledge_check.py`

### Results

- Red result: `Tests run: 2, Failures: 2, Errors: 0, Skipped: 0`; both failures were `Status expected:<200> but was:<404>` for `POST /api/projects/{projectId}/sessions/{sessionId}/messages`, proving the project-scoped run endpoint did not exist.
- Focused green result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Acceptance result: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Review red result: `Tests run: 4, Failures: 2, Errors: 1, Skipped: 0`; failures showed `evidence.evaluated` missing `outputMode`, non-empty `sourceFilters` returning `200` instead of `400`, and cross-project session falling through to an `assistant_answer` foreign-key error instead of a controlled `404`.
- Review focused green result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Review acceptance result: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent acceptance result after cleanup: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent F004 guardrail result: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent Harness check result: `knowledge_check: ok`.

### Coverage

- `ProjectRunEventFlowTest` verifies the project-scoped message response fields, the returned `answerId` exists in `assistant_answer`, the exact required event wire-name order for the returned `streamRunId`, `evidence.evaluated` uses `evidenceState`, `outputMode`, and `citationCount`, and SSE replay through the existing F004 projection format.
- Review regression coverage verifies cross-project sessions return `404` before event publication or legacy working-memory append, and non-empty `sourceFilters` returns `400` before event publication or answer-path invocation until F007 owns project-source retrieval.
- `SupervisorServiceLogicTest` and `TaskRouterTest` guard the existing answer-routing logic.
- `ChatControllerTest` and `ChatStreamControllerTest` guard legacy `/api/chat` and `/api/chat/stream` compatibility.

## Notes

- F006 intentionally emits a bounded `evidence.evaluated` event from current response metadata only; full evidence boundary modeling remains F007.
- `sourceFilters` are rejected for F006 because F005 source IDs are UUID strings and project-source retrieval belongs to F007. The Supervisor path no longer parses source filters into legacy numeric document IDs.
- The project run path uses `sessionId` as the legacy Supervisor `sessionKey` bridge so the existing answer path remains the single answer implementation.
- `assistant_answer` is persisted with generated `answerId`, answer text, answer mode, and a coarse evidence-state summary. No candidate, feedback, knowledge-board, or UI behavior is added.
- A test-context cache issue was avoided by giving `ProjectRunEventFlowTest` a unique test property, preventing reuse of a stale Testcontainers datasource across integration test classes.
- Rollback: revert the F006 code/test/doc changes to remove project-scoped message runs while preserving F003-F005 project, source, event, and SSE behavior.
