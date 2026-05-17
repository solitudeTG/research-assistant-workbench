---
id: PLAN-F012
doc_kind: plan
status: active
updated: 2026-05-10
feature_ids: [F012]
---
# F012 Agentic Routing and Tavily Web Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让项目级聊天具备主 Agent 工具路由能力，并接入 Tavily 联网查询。

**Architecture:** `SupervisorService` 继续作为主 Agent 编排入口。新增 `AgentIntentRouter` 先决定工具需求，再调用 L1/L2、L3 recall、Paper RAG、Tavily Web Search，并把结果交给证据边界和回答生成器。先使用规则优先路由，保留后续 LLM router 扩展点。

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring WebClient or RestClient, JDBC, Maven, JUnit 5, Mockito, Tavily Search API.

---

## File Structure

- Create: `src/main/java/com/researchassistant/orchestrator/AgentIntent.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AgentRoutingDecision.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AgentIntentRouter.java`
- Create: `src/test/java/com/researchassistant/orchestrator/AgentIntentRouterTest.java`
- Create: `src/main/java/com/researchassistant/websearch/WebSearchPort.java`
- Create: `src/main/java/com/researchassistant/websearch/WebSearchResult.java`
- Create: `src/main/java/com/researchassistant/websearch/WebSearchHit.java`
- Create: `src/main/java/com/researchassistant/websearch/TavilyWebSearchClient.java`
- Create: `src/main/java/com/researchassistant/websearch/NoopWebSearchClient.java`
- Create: `src/test/java/com/researchassistant/websearch/TavilyWebSearchClientTest.java`
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Modify: `src/main/java/com/researchassistant/evidence/EvidenceBoundaryService.java`
- Modify: `src/main/java/com/researchassistant/evidence/EvidenceSourceRepository.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`
- Create or modify: `src/test/java/com/researchassistant/orchestrator/ProjectAgentRoutingTest.java`

## Task 1: Intent Router

**Files:**
- Create: `src/main/java/com/researchassistant/orchestrator/AgentIntent.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AgentRoutingDecision.java`
- Create: `src/main/java/com/researchassistant/orchestrator/AgentIntentRouter.java`
- Create: `src/test/java/com/researchassistant/orchestrator/AgentIntentRouterTest.java`

- [ ] **Step 1: Write failing router tests**

Test names:

```java
@Test
void greetingRoutesToSimpleChat()

@Test
void explicitWebRequestRoutesToWebSearch()

@Test
void localPaperQuestionRoutesToProjectRag()

@Test
void localPaperLatestComparisonRoutesToProjectRagWithWeb()

@Test
void planningQuestionRoutesToPlanning()
```

Run:

```powershell
mvn -Dtest=AgentIntentRouterTest test
```

Expected: fail because router classes do not exist.

- [ ] **Step 2: Implement routing records and enum**

`AgentIntent` values:

```text
SIMPLE_CHAT
MEMORY_RECALL
PROJECT_RAG
WEB_SEARCH
PROJECT_RAG_WITH_WEB
PLANNING
```

`AgentRoutingDecision` fields:

```java
AgentIntent intent
boolean needsMemoryRecall
boolean needsPaperRag
boolean needsWebSearch
String reason
```

- [ ] **Step 3: Implement rule-first router**

Rules:

- Greetings: `你好`, `hello`, `hi`, `你是谁` -> `SIMPLE_CHAT`.
- Web phrases: `联网`, `搜索`, `最新`, `现在`, `today`, `latest`, `search` -> `WEB_SEARCH`.
- Paper/project phrases: `论文`, `paper`, `项目资料`, `本地资料`, `source` -> `PROJECT_RAG`.
- Both local and web phrases -> `PROJECT_RAG_WITH_WEB`.
- Planning phrases: `计划`, `方案`, `roadmap`, `plan`, `step by step` -> `PLANNING`.

- [ ] **Step 4: Verify router tests pass**

Run:

```powershell
mvn -Dtest=AgentIntentRouterTest test
```

Expected: all router tests pass.

## Task 2: Tavily Web Search Tool

**Files:**
- Create: `src/main/java/com/researchassistant/websearch/WebSearchPort.java`
- Create: `src/main/java/com/researchassistant/websearch/WebSearchResult.java`
- Create: `src/main/java/com/researchassistant/websearch/WebSearchHit.java`
- Create: `src/main/java/com/researchassistant/websearch/TavilyWebSearchClient.java`
- Create: `src/main/java/com/researchassistant/websearch/NoopWebSearchClient.java`
- Create: `src/test/java/com/researchassistant/websearch/TavilyWebSearchClientTest.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Write failing web search tests**

Cover:

```java
@Test
void mapsSuccessfulTavilyResponseToWebSearchResult()

@Test
void missingApiKeyReturnsDegradedResult()

@Test
void nonSuccessResponseReturnsDegradedResult()
```

Run:

```powershell
mvn -Dtest=TavilyWebSearchClientTest test
```

Expected: fail because web search classes do not exist.

- [ ] **Step 2: Add configuration**

Add to `application.yml`:

```yaml
app:
  web-search:
    tavily:
      api-key: ${TAVILY_API_KEY:}
      base-url: ${TAVILY_BASE_URL:https://api.tavily.com}
      max-results: ${TAVILY_MAX_RESULTS:5}
      search-depth: ${TAVILY_SEARCH_DEPTH:basic}
```

- [ ] **Step 3: Implement port and result records**

`WebSearchPort.search(String query, int maxResults)` returns `WebSearchResult`.

- [ ] **Step 4: Implement Tavily client**

Use Spring HTTP client already available in the project. Send query and max result settings to Tavily. Map title, URL, content/snippet, and score. On missing key or request failure, return degraded result instead of throwing into the chat path.

- [ ] **Step 5: Verify web search tests pass**

Run:

```powershell
mvn -Dtest=TavilyWebSearchClientTest test
```

Expected: all tests pass.

## Task 3: Project Chat Main Agent Flow

**Files:**
- Modify: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Create or modify: `src/test/java/com/researchassistant/orchestrator/ProjectAgentRoutingTest.java`

- [ ] **Step 1: Write failing project routing tests**

Cover:

```java
@Test
void simpleGreetingSkipsPaperRagAndWebSearch()

@Test
void explicitWebQuestionCallsTavilyWithoutWaitingForPaperRagFailure()

@Test
void localQuestionCallsPaperRagOnly()

@Test
void mixedLocalAndLatestQuestionCallsPaperRagAndTavily()
```

Run:

```powershell
mvn -Dtest=ProjectAgentRoutingTest test
```

Expected: fail because `SupervisorService.answerProject(...)` still evaluates paper evidence before intent routing.

- [ ] **Step 2: Inject router and web search port**

Add `AgentIntentRouter` and `WebSearchPort` dependencies to `SupervisorService`.

- [ ] **Step 3: Route before retrieval**

In `answerProject(...)`, after loading L1/L2 and before Paper RAG:

```text
routingDecision = agentIntentRouter.route(question, allowWebSupplement)
```

Use the decision to decide whether to call memory recall, Paper RAG, Tavily, or planning.

- [ ] **Step 4: Implement simple chat answer path**

For `SIMPLE_CHAT`, call the chat model with L1/L2 context and a system prompt that explains the assistant can help with project research and optional web search. Do not call Paper RAG or Tavily.

- [ ] **Step 5: Verify project routing tests pass**

Run:

```powershell
mvn -Dtest=ProjectAgentRoutingTest test
```

Expected: all routing tests pass.

## Task 4: Evidence Boundary and Persistence

**Files:**
- Modify: `src/main/java/com/researchassistant/evidence/EvidenceBoundaryService.java`
- Modify: `src/main/java/com/researchassistant/evidence/EvidenceSourceRepository.java`
- Modify: `src/test/java/com/researchassistant/evidence/ProjectEvidenceBoundaryTest.java`

- [ ] **Step 1: Write failing evidence tests**

Cover:

```java
@Test
void webResultsPersistAsWebEvidence()

@Test
void memoryRecallDoesNotRaisePaperEvidenceLevel()

@Test
void webSupplementAnswerSeparatesPaperAndWebSourceTypes()
```

Run:

```powershell
mvn -Dtest=ProjectEvidenceBoundaryTest test
```

Expected: fail until web evidence persistence is implemented.

- [ ] **Step 2: Persist web evidence**

Add repository method to insert web evidence rows with `sourceType=web`, URL/title/snippet/provider in citation metadata, and score as relevance.

- [ ] **Step 3: Extend evidence events**

`retrieval.completed` includes `intent`, `toolsUsed`, `webEvidenceCount`, and `webSearchStatus`.

`evidence.evaluated` includes `sourceTypes`.

- [ ] **Step 4: Verify evidence tests pass**

Run:

```powershell
mvn -Dtest=ProjectEvidenceBoundaryTest test
```

Expected: all evidence tests pass.

## Task 5: Final Verification and Harness Evidence

**Files:**
- Create: `docs/evidence/EV-011-f012-agentic-routing-web-search.md`
- Modify: `docs/features/F012-agentic-routing-and-tavily-web-search.md`
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Run focused backend tests**

Run:

```powershell
mvn -Dtest=AgentIntentRouterTest,ProjectAgentRoutingTest,TavilyWebSearchClientTest,ProjectEvidenceBoundaryTest test
```

Expected: all focused tests pass.

- [ ] **Step 2: Run full backend verification**

Run:

```powershell
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 3: Run Harness verification**

Run:

```powershell
python scripts\knowledge_check.py
```

Expected:

```text
knowledge_check: ok
```

- [ ] **Step 4: Write Evidence**

Record commands, results, implemented behavior, known limitations, config requirements, and rollback notes in `EV-011-f012-agentic-routing-web-search.md`.

- [ ] **Step 5: Update Feature and Backlog**

Set F012 status based on verification result. Move F012 from Active Work to Recently Completed only after tests and evidence pass.

## Self-Review

Spec coverage:

- Simple chat not entering RAG: Task 1 and Task 3.
- Tavily as active tool, not fallback only: Task 2 and Task 3.
- L1/L2 default and L3 on demand: Task 3.
- Evidence separation: Task 4.
- Harness closeout: Task 5.

Placeholder scan:

- No `TBD` or unbounded “implement later” requirements remain.

Type consistency:

- Plan uses `AgentIntent`, `AgentRoutingDecision`, `WebSearchPort`, `WebSearchResult`, `WebSearchHit`, `paper`, `web`, `conversation_memory`, and `project_knowledge` consistently with the spec.

## Execution Handoff

Recommended execution: inline TDD in this session, because F012 is a single coherent backend slice and the first task is a dependency for all later tasks.
