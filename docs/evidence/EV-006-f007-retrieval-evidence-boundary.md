---
id: EV-006
doc_kind: evidence
status: completed
scope: feature
feature_ids: [F007]
created: 2026-05-10
---
# F007 Retrieval Evidence Boundary Evidence

## Evidence

## Commands
- Red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest' test`
- Focused green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest' test`
- Acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest' test`
- Final acceptance rerun after source-filter message cleanup: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest' test`
- F006 guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest' test`
- Spec-review red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest' test`
- Spec-review focused green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest' test`
- Spec-review acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest' test`
- Second-review red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest#memoryRecallWithoutScopedPaperMappingEmitsMemoryRecallOnlyButNoEvidence' test`
- Second-review focused green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest#memoryRecallWithoutScopedPaperMappingEmitsMemoryRecallOnlyButNoEvidence' test`
- Second-review acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest' test`
- Quality-review red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=SchemaSmokeTest,DocumentIngestServiceTest,ProjectSourceStatusMachineTest,ProjectEvidenceBoundaryTest' test`
- Quality-review targeted green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=SchemaSmokeTest,DocumentIngestServiceTest,ProjectSourceStatusMachineTest,ProjectEvidenceBoundaryTest' test`
- Quality-review F007 acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest' test`
- Quality-review F005 acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Quality-review F006 guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest' test`
- Parent atomicity follow-up: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest#answerAndEvidencePersistenceRollsBackWhenEvidenceInsertFails' test`
- Parent final F007 acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest' test`
- Parent final F005 guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest' test`
- Parent final F006 guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest' test`
- Diff check: `git diff --check`
- Harness check: `python scripts\knowledge_check.py`

## Results
- Red result: `Tests run: 4, Failures: 3, Errors: 1, Skipped: 0`.
- Red failures proved the intended gaps:
  - `evidence_source` did not yet expose F007 fields such as `source_type`, `snippet`, `strength`, `relevance_score`, `feedback_score`, and `citation_meta_json`.
  - Weak local evidence with web enabled still returned `LOCAL_WEAK_EVIDENCE` instead of `WEB_SUPPLEMENT`.
  - No paper evidence with web disabled still returned a weak local answer state instead of `NONE` and `REFUSAL`.
  - L3 memory was not included as retrieval context for the project paper retrieval query.
- Focused green result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Acceptance result: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Final acceptance rerun result: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- F006 guardrail result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Spec-review red result: `Tests run: 7, Failures: 1, Errors: 6, Skipped: 0`. The failure proved `answerProject` still called `paperRagService.retrieve(..., [], 5)`, and the errors proved the minimal project-source-to-index mapping column did not exist yet.
- Spec-review focused green result: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Spec-review acceptance result: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Second-review red result: `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`. The failure proved memory-only project retrieval emitted `NO_RETRIEVAL` instead of `MEMORY_RECALL_ONLY`.
- Second-review focused green result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Second-review acceptance result: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Quality-review red result: test compilation failed because `DocumentRepository.linkSourceIndexedDocument(projectId, sourceId, indexedDocumentId)` did not exist yet. This proved the F005 file-source path had no production mapping seam for F007 scoped project evidence.
- Quality-review targeted green result: `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Quality-review F007 acceptance result: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Quality-review F005 acceptance result: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Quality-review F006 guardrail result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent atomicity follow-up result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent final F007 acceptance result: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent final F005 guardrail result: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent final F006 guardrail result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Diff check result: exit code 0. Git reported only existing LF-to-CRLF working-copy warnings.
- Harness check result: `knowledge_check: ok`.

## Artifacts
- `ProjectEvidenceBoundaryTest` covers:
  - strong paper evidence -> `SUFFICIENT` + `LOCAL_EVIDENCE`
  - weak paper evidence with web allowed -> `WEAK` + `WEB_SUPPLEMENT`
  - no paper evidence with web disabled -> `NONE` + `REFUSAL`
  - weak paper evidence with web disabled -> `WEAK` + `LOCAL_WEAK_EVIDENCE`
  - no project source-to-index mapping -> no Paper RAG call, `NONE` + `REFUSAL`, and no global legacy chunks counted as project evidence
  - no project source-to-index mapping with non-empty L3 memory recall -> `MEMORY_RECALL_ONLY`, no Paper RAG call, `NONE` + `REFUSAL`, and no evidence rows
  - L3 memory enriches the paper retrieval query but does not create evidence rows or raise evidence level
  - project file-source import creates a legacy indexed `research_document`, writes `source_document.indexed_document_id`, and F007 retrieval scopes Paper RAG to the mapped project document only
  - answer, evidence, and project chat memory persistence roll back together if evidence insertion fails, and completed/evaluated events are not emitted for that failed persistence path
  - null memory recall and null Paper RAG chunks are treated as no evidence instead of crashing the project answer path
  - evidence endpoint returns rows through MockMvc and does not leak the same `answerId` across projects
  - persisted `assistant_answer` evidence state and output mode
  - persisted paper rows in `evidence_source`, including the mapped project `sourceId`
  - `retrieval.completed` payload real state, including `PAPER_RAG_ONLY`, `MEMORY_THEN_PAPER`, `WEB_SUPPLEMENT`, and `NO_RETRIEVAL`
  - `evidence.evaluated` payload real state
- `PaperRagServiceTest`, `MemoryRecallServiceTest`, and `QueryRewriteServiceTest` guard the existing retrieval, recall, and query rewrite behavior while F007 changes the project evidence boundary.

## Implementation Notes

- Added `WEB_SUPPLEMENT` to `AnswerMode` and introduced `EvidenceAssessment`.
- Project message answering now performs memory recall as context, then only runs Paper RAG when `source_document.indexed_document_id` proves a project source maps to an indexed legacy paper document.
- F005 project file-source import now reuses the legacy `research_document` and `DocumentProcessingJob` indexing pipeline, then links the project source to the indexed document after successful indexing.
- With no scoped mapping, project answering skips Paper RAG and refuses instead of allowing an empty document filter to search all legacy chunks.
- The project path persists paper evidence sources after `assistant_answer` creation, and sets `evidence_source.source_id` only from the proved project source mapping.
- Answer, evidence, and project chat memory writes are persisted in one transaction so evidence insertion failures do not leave partial `assistant_answer`, `evidence_source`, or project chat-message state.
- V6 migration backfills historical evidence rows with `coalesce(snippet, quote, '')` before setting `snippet not null`.
- `GET /api/projects/{projectId}/answers/{answerId}/evidence` returns persisted evidence rows for a project answer.
- `retrieval.completed` now includes `retrievalMode`, `sourceFilterCount`, `paperEvidenceCount`, `memoryRecallCount`, `webSupplementAllowed`, `topPaperScore`, and `citationCount`.
- When memory recall contributes context but no scoped project paper mapping exists, `retrieval.completed.retrievalMode` is `MEMORY_RECALL_ONLY`; evidence remains `NONE` and answer mode remains `REFUSAL` because L3 memory is not current paper evidence.
- `evidence.evaluated` now includes `evidenceState`, `outputMode`, and `citationCount` from the same assessment used for persistence.

## Known Limitations

- F007 marks weak local evidence as requiring `WEB_SUPPLEMENT`, but does not implement an external web retrieval provider. That remains outside this slice.
- Non-empty project `sourceFilters` remain blocked by the controller guard until user-facing source selection is implemented. F007 added a backend-only `source_document.indexed_document_id` boundary for scoped project evidence; it did not add broad source search UI.
- `feedbackScore` update behavior remains F009.
- Candidate and knowledge-board behavior remains F008.

## Rollback

Revert the F007 code, test, migration, and Harness document changes to return project answers to the F006 coarse evidence summary while preserving F003-F006 project, source, event, and run flow behavior.

## Notes

No additional notes.
