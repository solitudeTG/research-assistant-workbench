# F018 Multi-Agent Evidence-Grounded Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a truthful, serial, on-demand multi-agent workflow where simple tasks stay on the existing ReAct path and complex research/document tasks use Deep Research, Evidence Audit, and Document Composer agents with traceable UI events.

**Architecture:** Keep `SupervisorService` as the project-message lifecycle owner, but move mode selection and Plan-Execute orchestration into focused classes under `com.researchassistant.orchestrator`. Reuse existing tool ports and trace publisher; add structured intermediate records and frontend model tests so the UI renders only real backend events.

**Tech Stack:** Spring Boot, Spring AI `ChatClient`, Java records/services, existing `WorkbenchEvent` SSE backbone, static ES modules, Node ESM tests, Maven/JUnit/MockMvc where Maven is available.

---

## File Structure

- Create `src/main/java/com/researchassistant/orchestrator/MultiAgentExecutionMode.java`: enum for `REACT` and `PLAN_EXECUTE`.
- Create `src/main/java/com/researchassistant/orchestrator/MultiAgentWorkflowDecider.java`: deterministic first-pass mode decision over request shape.
- Create `src/main/java/com/researchassistant/orchestrator/MultiAgentPlan.java`: structured plan record for trace and execution.
- Create `src/main/java/com/researchassistant/orchestrator/ResearchPacket.java`: structured deep research output.
- Create `src/main/java/com/researchassistant/orchestrator/AuditVerdict.java`: structured audit output.
- Create `src/main/java/com/researchassistant/orchestrator/DocumentDraft.java`: structured composer output.
- Create `src/main/java/com/researchassistant/orchestrator/DeepResearchAgent.java`: complex research subagent boundary.
- Create `src/main/java/com/researchassistant/orchestrator/EvidenceAuditAgent.java`: audit subagent boundary.
- Create `src/main/java/com/researchassistant/orchestrator/DocumentComposerAgent.java`: document-format subagent boundary.
- Create `src/main/java/com/researchassistant/orchestrator/MultiAgentPlanExecuteLoop.java`: serial orchestration boundary.
- Modify `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`: choose ReAct vs Plan-Execute and preserve existing persistence/evidence behavior.
- Modify `src/main/java/com/researchassistant/events/WorkbenchEventType.java`: add mode/delegation/audit/composer event types only if payload-only reuse is insufficient.
- Modify `src/main/java/com/researchassistant/orchestrator/AgentTracePublisher.java`: expose helper methods for mode and subagent lifecycle if needed.
- Modify `src/main/resources/static/js/workbench-model.js`: fold new trace payloads into `agentTraces`.
- Modify `src/main/resources/static/tests/workbench-model.test.mjs`: add multi-agent trace projection tests.
- Add/modify focused backend tests under `src/test/java/com/researchassistant/orchestrator/`.
- Update Harness docs and Evidence after implementation.

## Task 1: Mode Decision Contract

**Files:**
- Create: `src/main/java/com/researchassistant/orchestrator/MultiAgentExecutionMode.java`
- Create: `src/main/java/com/researchassistant/orchestrator/MultiAgentWorkflowDecider.java`
- Test: `src/test/java/com/researchassistant/orchestrator/MultiAgentWorkflowDeciderTest.java`

- [ ] **Step 1: Write failing tests**

Add tests that assert:

```java
@Test
void simpleQuestionUsesReact() {
    MultiAgentWorkflowDecider decider = new MultiAgentWorkflowDecider();

    assertThat(decider.decide("你好", false).mode()).isEqualTo(MultiAgentExecutionMode.REACT);
    assertThat(decider.decide("这篇论文的标题是什么", false).mode()).isEqualTo(MultiAgentExecutionMode.REACT);
}

@Test
void complexResearchUsesPlanExecute() {
    MultiAgentWorkflowDecider decider = new MultiAgentWorkflowDecider();

    assertThat(decider.decide("对比这几篇论文关于多 Agent 协作架构的观点，并给出可引用结论", true).mode())
            .isEqualTo(MultiAgentExecutionMode.PLAN_EXECUTE);
}

@Test
void documentRequestsUsePlanExecute() {
    MultiAgentWorkflowDecider decider = new MultiAgentWorkflowDecider();

    assertThat(decider.decide("基于资料生成一份 Markdown 综述报告", true).requiresDocumentComposer()).isTrue();
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
mvn "-Dtest=MultiAgentWorkflowDeciderTest" test
```

Expected: compile fails because the new classes do not exist.

- [ ] **Step 3: Implement minimal decision classes**

Create:

```java
package com.researchassistant.orchestrator;

public enum MultiAgentExecutionMode {
    REACT,
    PLAN_EXECUTE
}
```

Create a decision record and decider:

```java
package com.researchassistant.orchestrator;

public record MultiAgentWorkflowDecision(
        MultiAgentExecutionMode mode,
        String reason,
        boolean requiresDeepResearch,
        boolean requiresEvidenceAudit,
        boolean requiresDocumentComposer
) {
}
```

```java
package com.researchassistant.orchestrator;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MultiAgentWorkflowDecider {

    public MultiAgentWorkflowDecision decide(String question, boolean allowWebSupplement) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean document = containsAny(normalized, "报告", "综述", "表格", "论文笔记", "markdown", "文档");
        boolean complex = containsAny(normalized, "对比", "比较", "研究路线", "系统分析", "可引用", "判断是否成立", "严谨");
        boolean multiSource = containsAny(normalized, "多篇", "几篇", "多个来源", "paper/web", "联网和论文");
        boolean planExecute = document || complex || multiSource;
        return new MultiAgentWorkflowDecision(
                planExecute ? MultiAgentExecutionMode.PLAN_EXECUTE : MultiAgentExecutionMode.REACT,
                planExecute ? "complex_or_document_research_request" : "simple_react_request",
                planExecute && !document,
                planExecute,
                document
        );
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify GREEN**

Run:

```powershell
mvn "-Dtest=MultiAgentWorkflowDeciderTest" test
```

Expected: PASS.

## Task 2: Structured Intermediate Contracts

**Files:**
- Create: `src/main/java/com/researchassistant/orchestrator/ResearchPacket.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AuditVerdict.java`
- Create: `src/main/java/com/researchassistant/orchestrator/DocumentDraft.java`
- Test: `src/test/java/com/researchassistant/orchestrator/MultiAgentContractTest.java`

- [ ] **Step 1: Write contract tests**

Assert that empty-safe factory methods preserve source separation:

```java
@Test
void researchPacketSeparatesSources() {
    ResearchPacket packet = ResearchPacket.empty("question")
            .withEvidenceGap("No local paper evidence supports the claim.");

    assertThat(packet.paperEvidence()).isEmpty();
    assertThat(packet.webEvidence()).isEmpty();
    assertThat(packet.memoryContext()).isEmpty();
    assertThat(packet.evidenceGaps()).contains("No local paper evidence supports the claim.");
}
```

- [ ] **Step 2: Implement immutable records**

Implement records with `List.copyOf(...)` normalization in canonical constructors and small helper methods such as `empty(...)`, `withEvidenceGap(...)`, and `isPassing()`.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
mvn "-Dtest=MultiAgentContractTest" test
```

Expected: PASS.

## Task 3: Subagent Boundaries

**Files:**
- Create: `src/main/java/com/researchassistant/orchestrator/DeepResearchAgent.java`
- Create: `src/main/java/com/researchassistant/orchestrator/EvidenceAuditAgent.java`
- Create: `src/main/java/com/researchassistant/orchestrator/DocumentComposerAgent.java`
- Test: `src/test/java/com/researchassistant/orchestrator/MultiAgentSubagentTest.java`

- [ ] **Step 1: Write tests for permissions and outputs**

Cover:

- `DeepResearchAgent` may call research tools and returns `ResearchPacket`.
- `EvidenceAuditAgent` flags unsupported claims and recommends downgrade.
- `DocumentComposerAgent` generates a document from approved packet + verdict and does not require tool ports.

- [ ] **Step 2: Implement first-pass deterministic/service-backed agents**

Start with bounded deterministic implementations that wrap existing ports and prompt contracts only where needed. Do not add a dynamic skill registry.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
mvn "-Dtest=MultiAgentSubagentTest" test
```

Expected: PASS.

## Task 4: Serial Plan-Execute Loop

**Files:**
- Create: `src/main/java/com/researchassistant/orchestrator/MultiAgentPlan.java`
- Create: `src/main/java/com/researchassistant/orchestrator/MultiAgentPlanExecuteLoop.java`
- Test: `src/test/java/com/researchassistant/orchestrator/MultiAgentPlanExecuteLoopTest.java`

- [ ] **Step 1: Test serial orchestration**

Assert that a complex research request runs Deep Research before Evidence Audit, and document requests run Composer after Audit.

- [ ] **Step 2: Implement serial execution**

`MultiAgentPlanExecuteLoop.run(...)` should return a result containing:

- `MultiAgentPlan`
- `ResearchPacket`
- `AuditVerdict`
- optional `DocumentDraft`
- final synthesis context for `SupervisorService`

- [ ] **Step 3: Run focused tests**

Run:

```powershell
mvn "-Dtest=MultiAgentPlanExecuteLoopTest" test
```

Expected: PASS.

## Task 5: Supervisor Integration

**Files:**
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectAgentRoutingTest.java`

- [ ] **Step 1: Add routing tests**

Cover:

- Simple chat remains on existing ReAct path.
- Complex research enters Plan-Execute.
- Document-format request enters Plan-Execute and produces composer output.
- Audit downgrade affects final answer mode.

- [ ] **Step 2: Integrate decider and plan loop**

Inject `MultiAgentWorkflowDecider` and `MultiAgentPlanExecuteLoop`. Keep existing `ProjectAgentToolLoop` as the ReAct path.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
mvn "-Dtest=ProjectAgentRoutingTest,MultiAgentPlanExecuteLoopTest" test
```

Expected: PASS.

## Task 6: Trace and SSE Contract

**Files:**
- Modify: `src/main/java/com/researchassistant/events/WorkbenchEventType.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/AgentTracePublisher.java`
- Test: `src/test/java/com/researchassistant/orchestrator/AgentTracePublisherTest.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java`

- [ ] **Step 1: Add tests for new trace semantics**

Assert events represent:

- mode selected
- plan created
- subagent started/completed/failed
- audit verdict
- composer completed

- [ ] **Step 2: Implement event publishing**

Prefer reusing `agent.step.*` with richer payloads unless a distinct event type materially improves frontend contract. Do not rename existing event wire names.

- [ ] **Step 3: Run event tests**

Run:

```powershell
mvn "-Dtest=AgentTracePublisherTest,ProjectRunEventFlowTest" test
```

Expected: PASS.

## Task 7: Frontend Trace Projection

**Files:**
- Modify: `src/main/resources/static/js/workbench-model.js`
- Modify: `src/main/resources/static/tests/workbench-model.test.mjs`
- Modify if needed: `src/main/resources/static/js/workbench-app.js`
- Modify if needed: `src/main/resources/static/css/workbench.css`

- [ ] **Step 1: Add model tests**

Add Node tests proving:

- `REACT` mode does not render fake subagents.
- `PLAN_EXECUTE` mode renders Supervisor plan and real child agents.
- serial child events remain ordered, not parallel.
- audit verdict and composer output appear in the research process summary.

- [ ] **Step 2: Update model folding**

Extend existing `agentTraces[runId]` folding logic to understand new actor roles and payload fields.

- [ ] **Step 3: Run frontend tests**

Run:

```powershell
node .\src\main\resources\static\tests\workbench-model.test.mjs
node .\src\main\resources\static\tests\f002-workbench-model.test.mjs
```

Expected: PASS.

## Task 8: Harness Closeout

**Files:**
- Update: `docs/evidence/EV-017-f018-multi-agent-evidence-grounded-workflow.md`
- Update: `docs/features/F018-multi-agent-evidence-grounded-workflow.md`
- Update: `docs/BACKLOG.md`

- [ ] **Step 1: Create Evidence after verification**

Record focused backend tests, frontend model tests, and `knowledge_check.py` result.

- [ ] **Step 2: Update Feature and Backlog**

Set F018 status according to delivery state and link Evidence.

- [ ] **Step 3: Run Harness check**

Run:

```powershell
python .\scripts\knowledge_check.py
```

Expected: `knowledge_check: ok`.

- [ ] **Step 4: Commit**

Use a commit message that explains why F018 uses Supervisor-led serial multi-agent workflow and why parallel runtime is deferred.
