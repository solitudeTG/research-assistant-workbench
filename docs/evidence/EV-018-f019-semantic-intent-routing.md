---
id: EV-018
doc_kind: evidence
status: completed
date: 2026-05-15
feature_ids: [F019]
---
# EV-018 F019 Semantic Intent Routing

## Evidence

F019 replaces the rule-primary workflow trigger with a semantic workflow advisor while keeping deterministic routing as the fallback and safety guardrail.

Verified capabilities:

- `MultiAgentWorkflowDecider` remains the single public entry point for `REACT` vs `PLAN_EXECUTE`.
- `ModelBackedMultiAgentWorkflowIntentAdvisor` asks the model for strict JSON containing mode, reason, subagent boundaries, and confidence.
- Semantic advice is accepted only when confidence is high enough and the requested mode is internally consistent: `PLAN_EXECUTE` must name at least one subagent boundary, while `REACT` must not require child agents.
- Invalid JSON, unavailable model output, unknown mode, low confidence, or internally inconsistent advice falls back to the deterministic decision.
- Semantic confidence is accepted only inside the `0.0` to `1.0` range required by the spec.
- `MultiAgentWorkflowDecision` now carries `decisionSource`, `fallbackReason`, and `semanticConfidence`.
- `mode-selection` trace events publish the routing source metadata for both `PLAN_EXECUTE` and `REACT` paths.
- The frontend model preserves mode-selection metadata, and the research process timeline can display a `Mode decision` item so manual validation can explain why multi-agent collaboration appeared.
- F018 serial subagent behavior remains unchanged: `Deep Research Agent`, `Evidence Audit Agent`, and `Document Composer Agent` are still runtime steps under the Supervisor-led plan-execute path.

## Verification Commands

Focused backend verification:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'; & 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=MultiAgentWorkflowDeciderTest,ModelBackedMultiAgentWorkflowIntentAdvisorTest,AgentTracePublisherTest,ProjectAgentRoutingTest' test
```

Result:

```text
BUILD SUCCESS
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
```

F018/F019 backend regression:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'; & 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=MultiAgentWorkflowDeciderTest,ModelBackedMultiAgentWorkflowIntentAdvisorTest,MultiAgentContractTest,MultiAgentSubagentTest,MultiAgentPlanExecuteLoopTest,AgentTracePublisherTest,ProjectRunEventFlowTest,ProjectAgentRoutingTest,SupervisorServiceLogicTest' test
```

Result:

```text
BUILD SUCCESS
Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
```

Frontend model/UI-source verification:

```powershell
node .\src\main\resources\static\tests\workbench-model.test.mjs
node .\src\main\resources\static\tests\f002-workbench-model.test.mjs
```

Result:

```text
workbench-model.test.mjs: 6 tests passed.
f002-workbench-model.test.mjs: 31 tests passed.
```

Harness and diff hygiene commands are recorded during closeout:

```powershell
python .\scripts\knowledge_check.py .
git diff --check
```

## Residual Risk

This slice does not guarantee provider-level determinism for semantic classification; it intentionally bounds that risk with strict JSON parsing, confidence thresholding, consistency checks, and deterministic fallback. Live provider quality should be validated manually after rebuild/start with the same F018 demo prompt and a simple ReAct prompt.
