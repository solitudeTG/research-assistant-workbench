---
id: F013
doc_kind: feature
status: completed
owner: codex
updated: 2026-05-11
parent_feature: F002
evidence: [EV-012]
---
# Main Agent Tool-Calling Loop

## 目标

把项目级聊天从 F012 的“规则优先 Intent Router”升级为“主 Agent 自主选择工具”的交互模型。用户提出问题后，主 Agent 应基于问题、L1/L2 上下文和可用工具说明，自行决定是否调用 `web_search`、`paper_rag`、`memory_recall`，未来再扩展到子 Agent，而不是由应用层关键词规则预先决定工具路径。

这个 Feature 的直接痛点来自天气查询示例：用户问“帮我查询深圳今天的天气”，规则路由未命中联网信号，导致模型在没有真实工具结果时生成“我现在就查询”的伪联网话术。F013 要消除这种产品错觉：没有工具结果就不能声称已查询；需要外部实时事实时，应由主 Agent 调用联网工具。

## 范围

- 范围内：项目级 `POST /api/projects/{projectId}/sessions/{sessionId}/messages` 主路径改为主 Agent tool-calling loop。
- 范围内：将 `web_search`、`paper_rag`、`memory_recall` 作为主 Agent 可见工具暴露给模型。
- 范围内：工具执行结果继续进入现有证据边界、事件流和回答持久化，不把 web 证据伪装成本地论文证据。
- 范围内：验证 `深圳今天的天气` 这类自然中文实时事实请求会实际调用 web search。
- 范围内：保留 Spring Boot 作为主系统和数据/证据/事件权威；先使用 Spring AI 原生 tool calling，若当前 OpenAI-compatible provider 不支持，再落到显式 JSON action loop。
- 范围外：全量迁移到 Python 生态。
- 范围外：实现完整子 Agent 并行运行时、长任务队列、人类审批 checkpoint、独立 Python agent-runtime。
- 范围外：把 web 搜索结果自动写入长期记忆或知识板。

## 验收标准

- `你能帮我去查询一下深圳今天的天气吗` 不经过规则关键词预判也能实际调用 `web_search`，事件中出现 `tavily_web_search`。
- 本地论文/项目资料问题可由主 Agent 调用 `paper_rag`，并继续保存 `paper` evidence。
- 历史讨论类问题可由主 Agent 调用 `memory_recall`，且记忆上下文不提升论文证据强度。
- 简单寒暄不调用 Paper RAG 或 Tavily，也不返回缺少论文证据的拒答。
- 当模型或 provider 未产生工具调用时，回答不得声称“正在查询”或“已经查询”外部实时信息。
- Spring AI 原生 tool calling 的 spike 通过；若失败，F013 plan 明确切换到 JSON action loop 的 fallback。
- Focused backend tests 和 Harness validation 通过。

## 合同

- API：继续使用现有项目消息接口；请求体字段保持兼容。
- 事件：`retrieval.completed.toolsUsed` 反映主 Agent 实际调用的工具；`webSearchStatus` 只在 web 工具被调用后出现 `completed/degraded` 语义。
- 数据：`paper`、`web` evidence 继续写入 `evidence_source`；`conversation_memory` 仅作为回答上下文。
- UI：本 Feature 不重做界面，但现有事件/证据展示应能看出工具是否真实执行。
- 运行时：Spring Boot 继续是主系统；Python Agent Runtime 只作为后续 Feature 候选，不进入 F013 实现。

## 链接

- 规格：[F013-main-agent-tool-calling-loop-spec.md](../specs/F013-main-agent-tool-calling-loop-spec.md)
- 计划：[F013-main-agent-tool-calling-loop-plan.md](../plans/F013-main-agent-tool-calling-loop-plan.md)
- ADR：[ADR-004-main-agent-tool-calling-loop.md](../decisions/ADR-004-main-agent-tool-calling-loop.md)
- 前序 Feature：[F012-agentic-routing-and-tavily-web-search.md](F012-agentic-routing-and-tavily-web-search.md)
- Evidence：[EV-012-f013-main-agent-tool-calling-loop.md](../evidence/EV-012-f013-main-agent-tool-calling-loop.md)

## 下一步

F013 已完成聚焦后端验证与 Harness Evidence。后续如果真实 provider 无法稳定触发 Spring AI 原生工具调用，应打开独立 Feature，在同一个 `ProjectAgentToolLoop` 边界后实现 bounded JSON action loop fallback。
