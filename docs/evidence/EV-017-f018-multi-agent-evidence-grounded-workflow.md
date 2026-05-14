---
id: EV-017
doc_kind: evidence
status: completed
date: 2026-05-14
feature_ids: [F018]
---
# EV-017 F018 Multi-Agent Evidence-Grounded Workflow

## Evidence

F018 delivered the first Supervisor-led, serial, on-demand multi-agent workflow for project messages.

Verified capabilities:

- Simple/default project messages remain on the existing ReAct `ProjectAgentToolLoop` path and do not display fake child agents.
- Complex research and document-output requests enter `PLAN_EXECUTE`.
- `Deep Research Agent`, `Evidence Audit Agent`, and `Document Composer Agent` have explicit backend boundaries and structured outputs.
- `MultiAgentPlanExecuteLoop` emits real live lifecycle trace events at execution boundaries: `agent.step.started` before each subagent call, `agent.step.completed` after success, and `agent.step.failed` before rethrow.
- Existing wire names are reused: `agent.plan.created` and `agent.step.*`.
- Frontend model projection folds mode selection, serial plan, child agent lifecycle, audit counts, and composer metadata into `agentTraces[runId]`.
- Plan-Execute trace evidence counts remain process telemetry only. They do not create `evidence_source` rows and do not increment final answer citation counts until structured source references are carried through a later feature.
- Non-document Plan-Execute answers now synthesize user-facing weak-evidence/refusal text instead of returning internal research packet and audit telemetry as the final answer.

## Verification Commands

Backend focused verification:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=MultiAgentWorkflowDeciderTest,MultiAgentContractTest,MultiAgentSubagentTest,MultiAgentPlanExecuteLoopTest,ProjectAgentRoutingTest,AgentTracePublisherTest,ProjectRunEventFlowTest,ProjectEvidenceBoundaryTest' test
```

Recorded results from implementation and review loops before closeout:

```text
MultiAgentWorkflowDeciderTest: 11 tests passed.
MultiAgentContractTest: 8 tests passed.
MultiAgentSubagentTest + MultiAgentContractTest: 19 tests passed.
MultiAgentPlanExecuteLoopTest + MultiAgentSubagentTest + MultiAgentContractTest: 24 tests passed.
ProjectAgentRoutingTest + MultiAgentPlanExecuteLoopTest: 20 tests passed.
ProjectAgentRoutingTest + MultiAgentPlanExecuteLoopTest + ProjectEvidenceBoundaryTest: 35 tests passed.
AgentTracePublisherTest + ProjectRunEventFlowTest + ProjectAgentRoutingTest + MultiAgentPlanExecuteLoopTest: 32 tests passed.
```

Closeout backend re-run on 2026-05-14:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=MultiAgentWorkflowDeciderTest,MultiAgentContractTest,MultiAgentSubagentTest,MultiAgentPlanExecuteLoopTest,AgentTracePublisherTest' test
```

Result:

```text
BUILD SUCCESS
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
```

The full closeout backend command including Postgres integration tests was also attempted on 2026-05-14 and was blocked by local Docker/Testcontainers availability, not by assertion failures:

```text
Could not find a valid Docker environment.
Tests run: 77, Failures: 0, Errors: 38, Skipped: 0
```

The integration slices were previously verified during the implementation/review loops listed above.

Frontend model verification:

```powershell
node .\src\main\resources\static\tests\workbench-model.test.mjs
node .\src\main\resources\static\tests\f002-workbench-model.test.mjs
```

Result:

```text
workbench-model.test.mjs: 6 tests passed.
f002-workbench-model.test.mjs: 28 tests passed.
```

Diff hygiene:

```powershell
git diff --check
```

Result: exit code `0`; only Windows LF/CRLF normalization warnings.

Harness validation:

```powershell
python .\scripts\knowledge_check.py
```

Result:

```text
knowledge_check: ok
```

## Review Evidence

F018 was implemented with subagent-driven review gates after each task:

- Task 1 Mode Decision Contract: spec and code quality reviews passed after narrowing false-positive document triggers.
- Task 2 Structured Intermediate Contracts: reviews passed after hardening immutable contract constructors and fallback answer mode.
- Task 3 Subagent Boundaries: reviews passed after tightening audit semantics for unsupported claims and draft answers.
- Task 4 Serial Plan-Execute Loop: reviews passed after avoiding user-question-as-draft auditing.
- Task 5 Supervisor Integration: code quality review caught a citation/evidence-source mismatch; fix made Plan-Execute evidence semantics conservative and traceable.
- Task 6 Trace and SSE Contract: code quality review caught synthesized post-hoc lifecycle events; fix moved lifecycle publication into `MultiAgentPlanExecuteLoop` around actual subagent calls.
- Task 7 Frontend Trace Projection: spec review caught dropped backend audit count fields; fix preserved `unsupportedClaimCount`, `sourcePolicyIssueCount`, and `requiredRevisionCount`.
- Final independent review caught that non-document `PLAN_EXECUTE` could return internal telemetry as the user answer. The fix changed final synthesis to return document bodies, weak-evidence summaries, no-evidence messages, or refusals according to the audited packet/verdict.

## Residual Limits

- F018 does not implement parallel worker runtime. Serial execution is intentional for the first release and is recorded by `execution: serial`.
- Plan-Execute research packet evidence strings are process telemetry, not durable answer citations. The answer remains `LOCAL_WEAK_EVIDENCE` unless structured citation sources are persisted by a later feature.
- Non-document Plan-Execute summaries intentionally use cautious prose and evidence-gap/source-policy cautions until structured citation carry-through exists.
- `Document Composer Agent` produces document output only from the audited packet/verdict path. It does not independently retrieve or override evidence conclusions.
- This feature does not add a dynamic skill marketplace, human approval checkpoint, long-running job queue, or cross-process agent runtime.

## Closeout Judgment

F018 is complete for the scoped first release: Supervisor-led mode selection, serial Plan-Execute subagents, live trace/SSE contract, and frontend research-process projection. Follow-up work should focus on structured evidence carry-through for citable Plan-Execute answers or a separate parallel runtime feature, not by loosening the current trace-only evidence boundary.
