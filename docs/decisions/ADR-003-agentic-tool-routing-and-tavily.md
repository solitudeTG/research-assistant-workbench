---
id: ADR-003
doc_kind: adr
status: accepted
date: 2026-05-10
feature_ids: [F012]
---
# 主 Agent 工具路由与 Tavily 联网查询

## 决策

F012 采用“主 Agent + 规则优先 Intent Router + 工具化检索”的架构。Paper RAG、L3 Memory Recall 和 Tavily Web Search 都作为主 Agent 可调用工具，而不是把联网查询固定为 Paper RAG 失败后的兜底。联网 provider 选择 Tavily。

## 背景

当前项目级聊天默认进入本地证据判断，导致 `你好` 这类简单输入也返回“项目资料中没有足够论文证据”的拒答。F007 已经把 `WEB_SUPPLEMENT` 定义为边界，但没有实现外部 web retrieval provider。用户明确希望体验接近主流 Agent 产品聊天：主 Agent 先理解任务，再决定是否调用工具。

## 备选方案

- 继续沿用“先 Paper RAG，失败再联网”：实现成本低，但会让简单输入继续被 RAG 语义污染，也会错误地把联网当作失败补丁。
- 完全交给 LLM 自由 tool calling：体验灵活，但测试不稳定，成本更高，不适合作为第一阶段基线。
- 使用规则优先的 Intent Router，再逐步引入 LLM fallback：可测试、成本低，能先修复已知问题，并保留演进空间。

## 影响

- `SupervisorService` 将承担更明确的主 Agent 编排职责。
- Tavily 搜索结果必须以 `web` evidence 保存，不能伪装成本地论文证据。
- L1/L2 默认上下文和 L3 recall 的边界需要在代码和测试中固定。
- 后续若引入子 Agent 或 LLM router，应保持当前工具边界，不让子 Agent 直接写入确认知识。
