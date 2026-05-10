---
id: EV-007
doc_kind: evidence
status: completed
scope: feature
feature_ids: [F008]
created: 2026-05-10
---
# F008 Candidate Confirmation and Knowledge Board Evidence

## Evidence

## Commands

- Red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- Acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- Review red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- Review green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- Quality-review red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- V5 compatibility red: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRepositoryTest' test`
- Quality-review green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- V5 compatibility green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRepositoryTest' test`
- Migration smoke green: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=SchemaSmokeTest' test`
- Parent final F008 acceptance: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest' test`
- Parent combined guardrail attempt: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRepositoryTest,SchemaSmokeTest' test`
- Parent final ProjectRepository guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRepositoryTest' test`
- Parent final SchemaSmoke guardrail: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=SchemaSmokeTest' test`
- Harness check: `python scripts\knowledge_check.py`
- Diff whitespace check: `git diff --check`

## Results

- Red result: test compilation failed because `KnowledgeCandidateRepository` and `KnowledgeCandidateRecord` did not exist. This proved the F008 tests targeted missing candidate confirmation behavior before implementation.
- Acceptance result: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Review red result: `Tests run: 13, Failures: 4, Errors: 0, Skipped: 0`. The failures proved terminal-state transitions still returned `200` instead of `409` for repeated accept, accept then ignore/mark-unverified, ignore then accept, and repeated edit-and-accept.
- Review green result: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Quality-review red result: `Tests run: 17, Failures: 2, Errors: 2, Skipped: 0`. The failures proved cross-project `evidenceSourceIds` were accepted and invalid enum-like API inputs were surfacing as server errors instead of controlled `400 Bad Request` responses.
- V5 compatibility red result: `Tests run: 4, Failures: 1, Errors: 0, Skipped: 0`. The failing guardrail proved V5-shaped inserts populated `statement` with an empty string instead of the legacy `content` value.
- Quality-review green result: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- V5 compatibility green result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Migration smoke green result: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent final F008 acceptance result: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent combined guardrail attempt result: `SchemaSmokeTest` completed, then `ProjectRepositoryTest` failed with JDBC connection refused against the prior Testcontainers port. This was treated as an environment/container lifecycle issue in the combined JVM run, not a schema assertion failure, because both guardrails passed when rerun separately.
- Parent final ProjectRepository guardrail result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Parent final SchemaSmoke guardrail result: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- Harness check result: `knowledge_check: ok`.
- Diff whitespace check result: no whitespace errors.

## Artifacts

- `KnowledgeCandidateControllerTest` covers:
  - `GET /api/projects/{projectId}/answers/{answerId}/candidates`.
  - `GET /api/projects/{projectId}/candidates`.
  - candidate creation as a repository-level draft seam.
  - candidate creation publishing `candidate.created`.
  - candidate creation not creating a `KnowledgeEntry`.
  - accept writing a `KnowledgeEntry`, marking status `accepted`, and publishing `knowledge.entry.created`.
  - edit-and-accept writing edited title/content/section/evidence status and marking `edited_accepted`.
  - mark-unverified and ignore changing only candidate status without writing entries.
  - edit-and-accept rejecting invalid section and invalid evidence status with `400 Bad Request`.
  - edit-and-accept rejecting cross-project `evidenceSourceIds` with `400 Bad Request`.
  - repeated accept returning `409 Conflict` without duplicate `KnowledgeEntry` rows or duplicate `knowledge.entry.created` events.
  - accepted candidates rejecting later ignore or mark-unverified transitions while keeping the board entry.
  - ignored candidates rejecting later accept without writing a board entry.
  - repeated edit-and-accept returning `409 Conflict` without duplicate `KnowledgeEntry` rows or duplicate `knowledge.entry.created` events.
- `KnowledgeBoardControllerTest` covers:
  - `GET /api/projects/{projectId}/knowledge-board` with the exact five sections.
  - manual `POST /entries` creating a knowledge entry without a source candidate.
  - `PATCH /entries/{entryId}` updating and moving an entry.
  - `DELETE /entries/{entryId}` archiving without hard delete.
  - pending candidates staying out of the board until accepted.
  - manual create and patch rejecting invalid section and invalid evidence status with `400 Bad Request`.
  - manual create rejecting cross-project `evidenceSourceIds` with `400 Bad Request`.
- `ProjectRepositoryTest` covers V5-shaped `knowledge_candidate` inserts receiving F008-safe defaults.
- `SchemaSmokeTest` covers V7 normalizing legacy free-form candidate statuses, suggested sections, and board sections before constraints are enforced.

## Implementation Notes

- Added V7 migration to complete V5 candidate and knowledge entry fields, JSON arrays, and enum-like check constraints.
- Added `com.researchassistant.candidates` records, repository, and controller for the F008 candidate API surface.
- Added `com.researchassistant.knowledge` records, section view model, repository, and controller for the board API surface.
- Candidate creation publishes `candidate.created` but does not write `knowledge_entry`.
- Knowledge entry creation publishes `knowledge.entry.created` for candidate accept/edit-and-accept and manual board creation.
- Knowledge board delete uses the existing `archived` flag and list queries hide archived rows.
- Candidate terminal actions now claim the candidate with `UPDATE ... WHERE status = 'pending' RETURNING ...`; only a successful pending-to-terminal transition can create a board entry.
- V7 includes a partial unique index on active `knowledge_entry(project_id, source_candidate_id)` as a database backstop against duplicate active entries from one source candidate.
- V7 preserves V5-shaped writes by defaulting/triggering F008 candidate fields from legacy `content` and normalizing invalid legacy enum-like values before constraints are added.
- Candidate and board write paths validate `evidenceSourceIds` against `evidence_source.project_id` before storing or copying them.
- F008 controllers map invalid enum-like request values to controlled `400 Bad Request` responses.

## Known Limitations

- F008 does not generate candidates from a model; it only provides the persistence and confirmation boundary.
- F008 does not implement F010 UI behavior.
- F008 does not implement feedback scoring, broad source search, or web retrieval.
- Project-scoped event replay still depends on the existing F004 event publisher abstraction; F008 adds event publication, not a new live UI consumer.
- `knowledge.entry.created` is no longer published before a successful pending candidate transition and entry write in accept/edit-and-accept flows. Full after-commit publication remains outside this scoped fix, so a future transaction wrapper that rolls back after repository publication would still need an after-commit event mechanism.

## Rollback

Revert the F008 candidate package, knowledge package, V7 migration, F008 tests, and Harness document changes to return the codebase to the F007 retrieval/evidence boundary state.

## Notes

No additional notes.
