---
id: PLAN-F019
doc_kind: plan
status: completed
updated: 2026-05-15
feature_ids: [F019]
---
# F019 Semantic Intent Routing Plan

## Goal

Implement a semantic workflow advisor for `REACT` vs `PLAN_EXECUTE` without expanding keyword routing.

## Tasks

- [x] Add RED tests for semantic Plan-Execute, semantic ReAct, invalid-model fallback, and trace metadata.
- [x] Add `MultiAgentWorkflowIntentAdvisor` contract and model-backed implementation.
- [x] Update `MultiAgentWorkflowDecision` to carry decision source, fallback reason, and semantic confidence.
- [x] Update `MultiAgentWorkflowDecider` to combine deterministic fallback with semantic advisor output.
- [x] Update `AgentTracePublisher` and frontend model tests for routing metadata.
- [x] Update Feature/Evidence docs after verification.
- [x] Run Harness validation and closeout checks.

## Verification

Focused backend:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=MultiAgentWorkflowDeciderTest,ModelBackedMultiAgentWorkflowIntentAdvisorTest,AgentTracePublisherTest,ProjectAgentRoutingTest' test
```

F018/F019 backend regression:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=MultiAgentWorkflowDeciderTest,ModelBackedMultiAgentWorkflowIntentAdvisorTest,MultiAgentContractTest,MultiAgentSubagentTest,MultiAgentPlanExecuteLoopTest,AgentTracePublisherTest,ProjectRunEventFlowTest,ProjectAgentRoutingTest,SupervisorServiceLogicTest' test
```

Frontend model tests:

```powershell
node .\src\main\resources\static\tests\workbench-model.test.mjs
node .\src\main\resources\static\tests\f002-workbench-model.test.mjs
```

Harness:

```powershell
python .\scripts\knowledge_check.py .
git diff --check
```
