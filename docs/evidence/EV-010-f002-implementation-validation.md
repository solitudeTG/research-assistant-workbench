---
id: EV-010
doc_kind: evidence
status: completed
scope: feature
feature_ids: [F002, F011]
created: 2026-05-10
---
# F002 Implementation Validation Evidence

## Evidence

F011 closes F002 with validation evidence only. It did not add product capability; the test changes in this slice harden the integrated acceptance path and full-suite database lifecycle.

EV-010 is used instead of the original F011 acceptance filename `EV-002-f002-implementation-validation.md` because `EV-002` already belongs to F003.

## Commands

- Initial parent full backend verification: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' test`
- Targeted backend verification after harness hardening: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ChatControllerTest,Phase1HappyPathTest' test`
- Full backend verification after harness hardening: `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' test`
- Frontend verification: bundled Node `--test src/main/resources/static/tests/f002-workbench-model.test.mjs`
- Harness verification: `python scripts\knowledge_check.py`

## Results

- Initial parent full backend verification failed once: `Tests run: 133, Failures: 1`. The failure was `Phase1HappyPathTest.uploadThenAskGroundedQuestion`; the response had `answerMode=REFUSAL`, `citations=[]`, and no `$.citations[0].documentId`.
- The validation-hardening update makes `Phase1HappyPathTest` seed `vectorSearchPort.search(...)` from indexed chunks and assert chunks exist after upload indexing. This turns the legacy happy path into an explicit grounded-answer test instead of relying on implicit vector mock behavior.
- `PostgresIntegrationTest` now starts the shared PostgreSQL Testcontainer before dynamic datasource properties are resolved. This keeps the full Spring suite from binding Hikari pools to a stopped container port during context churn.
- `ChatControllerTest` now uses the inserted `research_document` id instead of assuming sequence value `1`, reducing order sensitivity under the shared full-suite database container.
- Targeted backend verification after hardening passed: `Tests run: 2, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- Full backend verification after hardening passed: `Tests run: 133, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- Frontend verification passed: `tests 9`, `pass 9`, `fail 0`.
- Harness verification passed: `knowledge_check: ok`.

## Artifacts

- F003 through F010 remain the product implementation slices for F002.
- F011 records validation and closeout only.
- F010 browser evidence remains in [EV-009-f010-three-column-workbench-ui.md](EV-009-f010-three-column-workbench-ui.md). That evidence covered desktop layout, narrow viewport overflow, typed SSE updates, evidence refresh, and candidate edit-and-accept through mocked F002 API/SSE endpoints.

## API and SSE Samples

Representative project answer request:

```http
POST /api/projects/{projectId}/sessions/{sessionId}/messages
Content-Type: application/json
```

```json
{
  "question": "What does this project evidence support?",
  "sourceFilters": [],
  "allowWebSupplement": true,
  "extractKnowledgeCandidates": true,
  "answerMode": "local_first"
}
```

Representative project answer response:

```json
{
  "messageId": "msg_...",
  "answerId": "ans_...",
  "streamRunId": "run_...",
  "sseUrl": "/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events"
}
```

Representative SSE projection:

```text
event: answer.delta
id: evt_...
data: {"eventType":"answer.delta","projectId":"...","sessionId":"...","runId":"...","answerId":"...","payload":{"text":"..."}}
```

Representative event sequence verified by F006/F010 guardrails:

```text
run.started
agent.plan.created
agent.step.started
retrieval.started
retrieval.completed
evidence.evaluated
answer.delta
answer.completed
run.completed
candidate.created
knowledge.entry.created
feedback.applied
```

## Implementation Notes

- F011 acceptance confirms the backend integrated happy path, frontend state model, browser evidence chain, and Harness documents pass after validation-hardening.
- The full backend pass covers all 133 tests available in the parent session.
- The frontend pass covers the F002 workbench model behavior exercised by F010.
- Browser verification was not repeated for F011 because F011 did not add or change product UI capability; EV-009 is the relevant browser artifact.

## Known Limitations

- F002 is complete as the current implemented workbench baseline, not as a full commercial research product.
- F004 SSE projection is replay-oriented; continuous live tailing beyond available run events remains a future enhancement.
- External web retrieval is still a boundary/fallback, not an implemented web search connector.
- Source-scoped live SSE, broad source search, account preferences, multi-tenant behavior, undo/deduplication for feedback, and long-term personalization remain outside F002.
- F010 browser verification used mocked F002 APIs for UI behavior; F011 backend tests cover the integrated backend path separately.

## Rollback

If the F011 documentation closeout needs to be reverted, remove this evidence file and restore the F002, F011, and BACKLOG status links to their pre-closeout state. Product code rollback is not part of F011.

## Notes

No additional notes.
