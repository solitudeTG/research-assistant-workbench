---
id: EV-019
doc_kind: evidence
status: completed
date: 2026-05-16
feature_ids: [F020]
---
# EV-019 F020 Plan-Execute Evidence Carry-Through

## Evidence

F020 closes the Plan-Execute citation gap for the interview-demo path.

Verified capabilities:

- `DeepResearchAgent` now creates structured `EvidenceCitationSource` records alongside paper/web text evidence.
- `EvidenceCurator` and `EvidenceGateAgent` preserve the citation reference attached to each accepted/rejected candidate.
- `ResearchPacket` carries accepted citation sources after curation and semantic gating.
- `SupervisorService` persists accepted Plan-Execute citation sources into the existing `evidence_source` table.
- Plan-Execute `citationCount`, `retrieval.completed`, and `evidence.evaluated` telemetry now reflect accepted/persisted citation sources.
- Plan-Execute answers without structured citations still keep the previous conservative weak-evidence behavior.

## Verification Commands

RED test before implementation:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectAgentRoutingTest#planExecutePersistsAcceptedCitationSources' test
```

Result:

```text
BUILD FAILURE
Compilation failed because EvidenceCitationSource did not exist.
```

Focused GREEN regression:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectAgentRoutingTest#planExecutePersistsAcceptedCitationSources' test
```

Result:

```text
BUILD SUCCESS
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Broader F020/F018 backend regression:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=MultiAgentWorkflowDeciderTest,MultiAgentContractTest,MultiAgentSubagentTest,MultiAgentPlanExecuteLoopTest,AgentTracePublisherTest,ProjectRunEventFlowTest,SupervisorServiceLogicTest,ProjectAgentRoutingTest' test
```

Result:

```text
BUILD SUCCESS
Tests run: 95, Failures: 0, Errors: 0, Skipped: 0
```

Frontend model smoke check:

```powershell
node .\src\main\resources\static\tests\workbench-model.test.mjs
node .\src\main\resources\static\tests\f002-workbench-model.test.mjs
```

Result:

```text
workbench-model.test.mjs: 6 tests passed.
f002-workbench-model.test.mjs: 35 tests passed.
```

Diff hygiene:

```powershell
git diff --check
```

Result:

```text
exit code 0; Git reported LF-to-CRLF working-copy warnings only.
```

Harness validation:

```powershell
python .\scripts\knowledge_check.py
```

Result:

```text
knowledge_check: ok
```

## Residual Limits

- This does not add parallel Worker runtime.
- This does not add a new citation table; it intentionally reuses `evidence_source`.
- This does not turn the deterministic `Document Composer Agent` into an LLM writer.
- Live manual demo validation after Docker rebuild/start remains useful before an interview.
