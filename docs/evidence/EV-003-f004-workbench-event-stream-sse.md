---
id: EV-003
status: captured
date: 2026-05-10
feature_ids: [F004]
---
# F004 Workbench Event Stream and SSE Evidence

## Evidence

- Command: `mvn -Dtest=StreamEventEnvelopeTest test`
- Result: red during TDD; the first focused run failed at test compilation because the F004 event classes did not exist yet.
- Coverage: proved the event envelope and event type contract tests were introduced before production implementation.

- Command: `mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest test`
- Result: red after the first implementation pass; compilation failed because `WorkbenchEventRepository` and `RedisStreamWorkbenchEventPublisher` were still missing.
- Coverage: proved the tests required the planned publisher/repository boundary and Redis adapter instead of only the in-memory implementation.

- Command: `mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest test`
- Result: green after the Redis/SSE contract fix; `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Coverage: verified event type wire names, monotonic per-run sequence, `Last-Event-ID` replay filtering, Redis Stream scoped writes, Redis payload JSON hydration, and SSE `id`/event name mapping.

- Command: `mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest test`
- Result: green after the bean graph fix; `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Coverage: added Spring context verification that the default/memory backend creates exactly one `WorkbenchEventPublisher` of type `InMemoryWorkbenchEventPublisher`, and `app.events.backend=redis` creates exactly one `WorkbenchEventPublisher` of type `RedisStreamWorkbenchEventPublisher`.

- Command: `mvn -Dtest=ProjectRepositoryTest,ProjectControllerTest,StreamEventEnvelopeTest,SseProjectionControllerTest test`
- Result: green after Harness document updates; `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Coverage: confirmed the new Redis dependency and event backend selection did not break the already completed F003 project model API tests.

- Review: F004 spec re-review by subagent.
- Result: approved with no findings after Redis/SSE fixes.
- Coverage: confirmed Redis `INCR` sequence, wire-name `eventType`, JSON payload storage/hydration, `Last-Event-ID` replay filtering, contract-shaped SSE data, scoped stream writes, and no F005+ overreach.

- Review: F004 code-quality re-review by subagent.
- Result: approved. Remaining note is non-blocking: current SSE projection replays currently available run events and completes instead of live-tailing future events.
- Coverage: confirmed the prior high-priority bean ambiguity was fixed by mutually exclusive backend conditions and context tests.

## Notes

- Redis Stream adapter writes `project:{projectId}:events`, `run:{runId}:events`, and `source:{sourceId}:events` when `sourceId` exists.
- The frontend-facing contract remains the SSE endpoint; Redis details are not exposed to browser code.
- The current SSE projection is sufficient for F004's replay/projection acceptance. Continuous live tailing should be revisited before later UI/runtime slices rely on long-lived process streaming.
- Rollback: revert the F004 commit to remove the event package, Redis dependency, F004 tests, Feature update, Backlog update, and this Evidence record.
