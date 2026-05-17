---
id: SPEC-F012
doc_kind: spec
status: accepted
updated: 2026-05-10
feature_ids: [F012]
---
# F012 Agentic Routing and Tavily Web Search Spec

## Goal

F012 的目标是把项目级聊天链路改成“主 Agent 默认接收用户输入和 L1/L2 记忆，再按任务需要调用工具”的 Agentic 架构。它解决两个具体问题：简单输入被误判为 RAG 失败，以及联网查询只是 `WEB_SUPPLEMENT` 模式标记、没有真实 Tavily 工具。

## Product Boundary

### In Scope

- 主 Agent 在回答前执行意图路由。
- L1 工作记忆和 L2 稳定记忆摘要默认进入上下文。
- L3 Memory Recall、Paper RAG、Tavily Web Search 都作为工具，由主 Agent 选择调用。
- 简单对话不走 RAG 证据判断。
- Tavily 可由用户显式要求、主 Agent 判断或本地证据不足且允许联网补充三种路径触发。
- 回答必须明确区分本地论文证据、联网补充和历史记忆上下文。

### Out of Scope

- 不实现完整多 Agent delegation。
- 不把联网结果自动写入长期记忆或知识板。
- 不做账号级联网权限、用量计费、配额 UI。
- 不实现网页全文爬取和二次清洗。

## Architecture

```text
User input
  + L1 WorkingMemory
  + L2 GlobalKnowledge summary
  -> SupervisorService as Main Agent
      -> AgentIntentRouter
      -> Tool calls
          -> MemoryRecallPort
          -> PaperRagService
          -> WebSearchPort(Tavily)
      -> EvidenceBoundaryService
      -> Answer composer
      -> Persistence and events
```

## Intent Routing Contract

`AgentIntentRouter` returns a deterministic routing decision:

```text
SIMPLE_CHAT
MEMORY_RECALL
PROJECT_RAG
WEB_SEARCH
PROJECT_RAG_WITH_WEB
PLANNING
```

The decision includes:

- `intent`
- `needsMemoryRecall`
- `needsPaperRag`
- `needsWebSearch`
- `reason`

Rules run before any LLM classifier:

- Greeting and simple capability questions return `SIMPLE_CHAT`.
- Explicit web phrases such as `联网`, `搜索`, `最新`, `现在`, `today`, `latest`, `search` return `WEB_SEARCH` or `PROJECT_RAG_WITH_WEB` when the question also references local project/paper material.
- Explicit paper/project phrases return `PROJECT_RAG` unless the same request asks for latest/external comparison.
- Planning phrases continue to use the existing planning path.

LLM-based fallback can be added later, but F012 first delivers a rule-first router to avoid cost and nondeterminism.

## Memory Contract

- L1: `WorkingMemoryService.load(sessionId)` is loaded for every project message.
- L2: `GlobalKnowledgeService.snapshot()` is included as stable context for answer composition.
- L3: `MemoryRecallPort.recall(...)` runs only when the routing decision marks `needsMemoryRecall` or when Paper RAG query enrichment is required for a project research question.
- L3 recall never counts as paper evidence.

## Tool Contracts

### Paper RAG Tool

- Existing `PaperRagService` remains the project-local evidence retrieval tool.
- It should run only when `needsPaperRag=true` and project evidence scope has mapped indexed documents.
- Empty local evidence must not automatically imply Tavily unless routing or `allowWebSupplement` allows it.

### Tavily Web Search Tool

Introduce:

```java
public interface WebSearchPort {
    WebSearchResult search(String query, int maxResults);
}
```

Result model:

```java
public record WebSearchResult(
        String query,
        List<WebSearchHit> hits,
        String provider,
        boolean degraded,
        String message
) {}
```

```java
public record WebSearchHit(
        String title,
        String url,
        String snippet,
        double score
) {}
```

Tavily implementation:

- Reads `app.web-search.tavily.api-key`.
- Calls Tavily search endpoint.
- Maps results into `WebSearchHit`.
- Returns `degraded=true` with a user-safe message when disabled, missing key, timeout, or non-2xx response occurs.

## Evidence Boundary

Evidence source types:

```text
paper
web
conversation_memory
project_knowledge
```

Rules:

- `paper` evidence can support `LOCAL_EVIDENCE` or `LOCAL_WEAK_EVIDENCE`.
- `web` evidence supports `WEB_SUPPLEMENT`.
- `conversation_memory` may inform continuity but cannot raise paper evidence strength.
- If no requested evidence source is available, the answer should be a natural recovery response, not a technical RAG failure.

## Events

`retrieval.completed` payload should include:

```json
{
  "intent": "PROJECT_RAG_WITH_WEB",
  "toolsUsed": ["paper_rag", "tavily_web_search"],
  "paperEvidenceCount": 2,
  "memoryRecallCount": 1,
  "webEvidenceCount": 3,
  "webSearchStatus": "completed"
}
```

`evidence.evaluated` payload should include:

```json
{
  "evidenceState": "WEAK",
  "outputMode": "WEB_SUPPLEMENT",
  "citationCount": 5,
  "sourceTypes": ["paper", "web"]
}
```

## Error Handling

- Tavily disabled or missing key: answer can say联网查询尚未配置，并 continue with local context if available.
- Tavily timeout/non-2xx: emit `retrieval.completed.webSearchStatus=degraded` and avoid invented claims.
- Simple chat path should not fail because project has no indexed documents.

## Acceptance Evidence

- Router unit tests cover greetings, explicit web queries, local research queries, mixed local+latest queries, and planning queries.
- Project chat tests prove `SIMPLE_CHAT` skips Paper RAG and Tavily.
- Tavily client tests use a mock HTTP server or fake adapter and cover success, missing key, and error mapping.
- Evidence tests prove web evidence is persisted separately from paper evidence.
- Harness check passes.
