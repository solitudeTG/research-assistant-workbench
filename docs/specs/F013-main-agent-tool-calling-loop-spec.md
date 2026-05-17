---
id: SPEC-F013
doc_kind: spec
status: accepted
updated: 2026-05-11
feature_ids: [F013]
---
# F013 Main Agent Tool-Calling Loop Spec

## 目标

F013 将项目级聊天升级为主 Agent tool-calling loop。应用层不再先用 `AgentIntentRouter` 决定是否联网、是否 RAG、是否 recall；主 Agent 看到用户问题、L1/L2 上下文、项目证据范围和工具说明后，自行选择工具。应用层负责执行工具、记录工具轨迹、保存证据、处理降级和生成可追踪事件。

## Product Boundary

### In Scope

- 主 Agent 使用工具选择来决定 `web_search`、`paper_rag`、`memory_recall` 是否执行。
- `web_search` 调用现有 `WebSearchPort`，默认 Tavily。
- `paper_rag` 调用现有 `PaperRagService`，并受项目 evidence scope 限制。
- `memory_recall` 调用现有 `MemoryRecallPort`。
- 工具调用结果进入最终回答上下文，并产生 `toolsUsed`、source types、evidence count 等 telemetry。
- 当模型/provider 原生 tool calling 不可用时，允许 fallback 到显式 JSON action loop，但工具边界和事件合同不变。

### Out of Scope

- 不全量迁移到 Python。
- 不实现独立子 Agent runtime。
- 不实现长任务暂停/恢复、人类审批 checkpoint、多 Agent 并行调度。
- 不把 web result 自动沉淀为长期记忆或知识板条目。

## Architecture

```text
ProjectMessageRequest
  -> SupervisorService
      -> load WorkingMemory (L1)
      -> load GlobalKnowledge (L2)
      -> load ProjectEvidenceScope
      -> ProjectAgentToolLoop
          -> ChatClient with tools
              -> ProjectAgentTools.web_search(...)
              -> ProjectAgentTools.paper_rag(...)
              -> ProjectAgentTools.memory_recall(...)
          -> ProjectAgentRun(answer, tool results, toolsUsed)
      -> Evidence assessment
      -> persist assistant answer and evidence
      -> publish retrieval/evidence/answer events
```

`SupervisorService` 仍负责项目消息生命周期、事件发布、事务持久化和证据边界；`ProjectAgentToolLoop` 负责模型调用和工具暴露；`ProjectAgentTools` 负责把工具调用映射到现有端口并记录结果。

## Tool Contracts

### web_search

Purpose: answer external, fresh, real-time, or non-project factual requests.

Input:

```json
{
  "query": "深圳今天的天气",
  "maxResults": 5
}
```

Output:

```json
{
  "provider": "tavily",
  "degraded": false,
  "message": "",
  "hits": [
    {
      "title": "深圳天气",
      "url": "https://...",
      "snippet": "...",
      "score": 0.91
    }
  ]
}
```

### paper_rag

Purpose: retrieve current project-local paper/source evidence.

Input:

```json
{
  "query": "这篇论文的核心方法是什么",
  "maxResults": 5
}
```

Constraints:

- If the project has no scoped indexed paper evidence, return an empty result with a clear message.
- Returned chunks must be filtered through `ProjectEvidenceScope.sourceIdByIndexedDocumentId()`.

### memory_recall

Purpose: retrieve historical project conversation context.

Input:

```json
{
  "query": "我们之前讨论过什么",
  "maxResults": 4
}
```

Constraints:

- Memory recall informs continuity only.
- It never counts as `paper` evidence.

## Provider Strategy

Primary path:

- Use Spring AI 1.1.2 `ChatClient.tools(...)` or `toolCallbacks(...)`.
- Tool methods return JSON strings or serializable objects.
- Internal tool execution remains enabled so model-requested tools are executed before final answer content is returned.

Fallback path:

- If the configured OpenAI-compatible provider does not support Spring AI tool calling reliably, implement an explicit JSON action loop:

```json
{
  "action": "web_search",
  "arguments": {
    "query": "深圳今天的天气",
    "maxResults": 5
  }
}
```

- The application validates the action name and arguments, executes at most a bounded number of tool rounds, then asks the model for the final answer with observations.
- The fallback must produce the same `ProjectAgentRun` shape as the Spring AI native path.

## Evidence Boundary

- If web hits exist, persist them through `EvidenceSourceRepository.insertWebSources(...)`.
- If paper chunks exist, persist them through `insertPaperSources(...)`.
- If only memory was used, answer mode remains weak/local context, not paper evidence.
- If no tool was called and no evidence exists, simple chat may answer conversationally, but must not claim external facts were retrieved.

## Event Contract

`retrieval.completed` payload:

```json
{
  "retrievalMode": "WEB_SUPPLEMENT",
  "toolsUsed": ["tavily_web_search"],
  "paperEvidenceCount": 0,
  "memoryRecallCount": 0,
  "webEvidenceCount": 3,
  "webSearchStatus": "completed",
  "sourceTypes": ["web"]
}
```

`evidence.evaluated` payload:

```json
{
  "evidenceState": "WEAK",
  "outputMode": "WEB_SUPPLEMENT",
  "citationCount": 3,
  "sourceTypes": ["web"]
}
```

## Acceptance Evidence

- Unit tests for `ProjectAgentTools` prove web, paper, and memory tools call the correct ports and record tool usage.
- Project chat tests prove weather-style requests call web search without relying on `AgentIntentRouter`.
- Project chat tests prove simple chat does not call retrieval tools.
- Evidence boundary tests prove web and paper evidence remain separated.
- A provider spike test or manual run records whether Spring AI native tool calling works with the configured provider.
- Harness `knowledge_check.py` passes for updated docs.
