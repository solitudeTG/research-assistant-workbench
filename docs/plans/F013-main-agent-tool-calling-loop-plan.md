---
id: PLAN-F013
doc_kind: plan
status: completed
updated: 2026-05-11
feature_ids: [F013]
---
# F013 Main Agent Tool-Calling Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace rule-first project chat routing with a main Agent tool-calling loop that lets the model decide whether to use web search, Paper RAG, or memory recall.

**Result:** Completed in F013. Implementation and verification are recorded in [EV-012-f013-main-agent-tool-calling-loop.md](../evidence/EV-012-f013-main-agent-tool-calling-loop.md).

**Architecture:** Keep Spring Boot as the authoritative application boundary. Add a focused `ProjectAgentToolLoop` that calls `ChatClient` with project tools, and a `ProjectAgentTools` object that maps model tool calls to existing ports while recording tool results for evidence and telemetry. Use Spring AI native tool calling first; if the configured provider cannot execute tools reliably, switch the same boundary to a bounded JSON action loop.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AI 1.1.2 ChatClient tools, JUnit 5, Mockito, JDBC, Tavily WebSearchPort, existing PaperRagService and MemoryRecallPort.

---

## File Structure

- Create: `src/main/java/com/researchassistant/orchestrator/ProjectAgentRequest.java`
- Create: `src/main/java/com/researchassistant/orchestrator/ProjectAgentRun.java`
- Create: `src/main/java/com/researchassistant/orchestrator/ProjectAgentToolLoop.java`
- Create: `src/main/java/com/researchassistant/orchestrator/DefaultProjectAgentToolLoop.java`
- Create: `src/main/java/com/researchassistant/orchestrator/ProjectAgentTools.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectAgentToolsTest.java`
- Test: `src/test/java/com/researchassistant/orchestrator/DefaultProjectAgentToolLoopTest.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Modify: `src/test/java/com/researchassistant/orchestrator/ProjectAgentRoutingTest.java`
- Modify: `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`
- Modify after implementation: `docs/evidence/EV-012-f013-main-agent-tool-calling-loop.md`
- Modify after implementation: `docs/features/F013-main-agent-tool-calling-loop.md`
- Modify after implementation: `docs/BACKLOG.md`

## Task 1: Spring AI Tool-Calling Spike

**Files:**
- Test: `src/test/java/com/researchassistant/orchestrator/DefaultProjectAgentToolLoopTest.java`

- [ ] **Step 1: Write the failing tool exposure test**

Create a test proving `DefaultProjectAgentToolLoop.run(...)` calls `ChatClient.prompt().system(...).user(...).tools(projectAgentTools).call().content()`.

Run:

```powershell
mvn -Dtest=DefaultProjectAgentToolLoopTest test
```

Expected: compile failure because `DefaultProjectAgentToolLoop`, `ProjectAgentRequest`, `ProjectAgentRun`, and `ProjectAgentTools` do not exist.

- [ ] **Step 2: Implement minimal loop types**

Add:

```java
public interface ProjectAgentToolLoop {
    ProjectAgentRun run(ProjectAgentRequest request);
}
```

`ProjectAgentRequest` contains question, working memory, global knowledge snapshot, project evidence scope, and `allowWebSupplement`.

`ProjectAgentRun` contains final answer, `RagResult`, `WebSearchResult`, `MemoryRecallResult`, and `toolsUsed`.

- [ ] **Step 3: Implement Spring AI native tool exposure**

`DefaultProjectAgentToolLoop.run(...)` creates a `ProjectAgentTools` instance and passes it to `ChatClient.tools(...)`.

Run:

```powershell
mvn -Dtest=DefaultProjectAgentToolLoopTest test
```

Expected: test passes with mocked `ChatClient`.

## Task 2: Project Agent Tools

**Files:**
- Create: `src/main/java/com/researchassistant/orchestrator/ProjectAgentTools.java`
- Test: `src/test/java/com/researchassistant/orchestrator/ProjectAgentToolsTest.java`

- [ ] **Step 1: Write failing tool tests**

Cover:

```java
@Test
void webSearchToolCallsWebSearchPortAndRecordsToolUse()

@Test
void paperRagToolCallsPaperRagServiceAndFiltersProjectScope()

@Test
void memoryRecallToolCallsMemoryRecallPortAndDoesNotCreateEvidence()
```

Run:

```powershell
mvn -Dtest=ProjectAgentToolsTest test
```

Expected: fail until tools exist.

- [ ] **Step 2: Implement `web_search`**

Expose a Spring AI `@Tool` method named `web_search`. It calls `WebSearchPort.search(query, maxResults)` and records `tavily_web_search` in `toolsUsed`.

- [ ] **Step 3: Implement `paper_rag`**

Expose `paper_rag`. It calls `PaperRagService.retrieve(sessionId, query, scopedDocumentIds, maxResults)`, filters returned chunks by `ProjectEvidenceScope`, and records `paper_rag`.

- [ ] **Step 4: Implement `memory_recall`**

Expose `memory_recall`. It calls `MemoryRecallPort.recall(sessionId, query, maxResults)` and records `memory_recall`.

- [ ] **Step 5: Verify tool tests pass**

Run:

```powershell
mvn -Dtest=ProjectAgentToolsTest test
```

Expected: all tool tests pass.

## Task 3: Supervisor Integration

**Files:**
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Modify: `src/test/java/com/researchassistant/orchestrator/ProjectAgentRoutingTest.java`

- [ ] **Step 1: Write failing project chat tests**

Add coverage:

```java
@Test
void weatherQuestionUsesMainAgentToolLoopAndPersistsWebTelemetry()

@Test
void simpleChatCanAnswerWithoutRetrievalTools()
```

Run:

```powershell
mvn -Dtest=ProjectAgentRoutingTest test
```

Expected: fail because `answerProject` still uses `AgentIntentRouter`.

- [ ] **Step 2: Inject `ProjectAgentToolLoop` into `SupervisorService`**

Keep existing collaborators for legacy `/api/chat` and evidence persistence. Project message path should call `projectAgentToolLoop.run(...)` after loading memory, global knowledge, and evidence scope.

- [ ] **Step 3: Map `ProjectAgentRun` to evidence assessment**

Use existing rules:

- web hits -> `WEB_SUPPLEMENT`
- paper chunks -> local evidence assessment
- memory only -> weak local context
- no tools/simple chat -> weak/no evidence without retrieval claims

- [ ] **Step 4: Publish actual tool telemetry**

`retrieval.completed.toolsUsed` comes from `ProjectAgentRun.toolsUsed()`, not from rule routing booleans.

- [ ] **Step 5: Verify project routing tests pass**

Run:

```powershell
mvn -Dtest=ProjectAgentRoutingTest test
```

Expected: all project routing tests pass.

## Task 4: Evidence Boundary Regression

**Files:**
- Modify: `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`

- [ ] **Step 1: Add failing regression for web and paper separation through tool loop**

The test should construct a project message whose `ProjectAgentRun` includes both paper chunks and web hits. It should assert `evidence_source.source_type` includes both `paper` and `web`.

- [ ] **Step 2: Run evidence tests**

Run:

```powershell
mvn -Dtest=ProjectEvidenceBoundaryTest test
```

Expected: fail until Supervisor maps `ProjectAgentRun` evidence correctly.

- [ ] **Step 3: Fix mapping only if needed**

Reuse existing `insertPaperSources(...)` and `insertWebSources(...)`; do not add a parallel evidence table or duplicate source model.

- [ ] **Step 4: Verify evidence tests pass**

Run:

```powershell
mvn -Dtest=ProjectEvidenceBoundaryTest test
```

Expected: all evidence tests pass.

## Task 5: Provider Fallback Decision

**Files:**
- Modify if needed: `src/main/java/com/researchassistant/orchestrator/DefaultProjectAgentToolLoop.java`
- Create only if native tool calling fails: `src/main/java/com/researchassistant/orchestrator/JsonActionProjectAgentToolLoop.java`
- Test if fallback added: `src/test/java/com/researchassistant/orchestrator/JsonActionProjectAgentToolLoopTest.java`

- [ ] **Step 1: Run local provider smoke check**

With configured `.env`, ask the app or a focused integration path: `你能帮我去查询一下深圳今天的天气吗`.

Expected: `web_search` is called and `toolsUsed` includes `tavily_web_search`.

- [ ] **Step 2: Decide native or fallback**

If Spring AI native tool calling works, record that in Evidence and do not implement JSON fallback.

If it does not work, implement bounded JSON action loop with max tool rounds and validated action names.

- [ ] **Step 3: Verify chosen path**

Run focused tests after the decision:

```powershell
mvn -Dtest=DefaultProjectAgentToolLoopTest,ProjectAgentToolsTest,ProjectAgentRoutingTest,ProjectEvidenceBoundaryTest test
```

Expected: all focused tests pass.

## Task 6: Harness Closeout

**Files:**
- Create: `docs/evidence/EV-012-f013-main-agent-tool-calling-loop.md`
- Modify: `docs/features/F013-main-agent-tool-calling-loop.md`
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Run backend verification**

Run:

```powershell
mvn -Dtest=DefaultProjectAgentToolLoopTest,ProjectAgentToolsTest,ProjectAgentRoutingTest,ProjectEvidenceBoundaryTest test
```

Expected: focused tests pass.

- [ ] **Step 2: Run Harness verification**

Run:

```powershell
python scripts\knowledge_check.py
```

Expected: `knowledge_check: ok`.

- [ ] **Step 3: Write Evidence**

Record implementation summary, provider decision, commands, results, known limitations, and rollback path in `EV-012`.

- [ ] **Step 4: Update Feature and Backlog**

Only mark F013 completed after tests and Evidence pass. Until then, keep F013 active with next step recorded.

## Self-Review

Spec coverage:

- Main Agent chooses tools: Tasks 1-3.
- Web/weather request calls web search: Tasks 2-3 and provider smoke.
- Evidence separation: Task 4.
- Provider fallback: Task 5.
- Harness closeout: Task 6.

Placeholder scan:

- No `TBD`, unbounded TODO, or “implement later” step remains.

Type consistency:

- Plan consistently uses `ProjectAgentToolLoop`, `ProjectAgentTools`, `ProjectAgentRun`, `ProjectAgentRequest`, `web_search`, `paper_rag`, and `memory_recall`.

## Execution Handoff

Recommended execution: inline TDD in this session after user confirms the Harness pre-work, because F013 is a single backend architecture slice with tight coupling between tool loop, Supervisor telemetry, and evidence persistence.
