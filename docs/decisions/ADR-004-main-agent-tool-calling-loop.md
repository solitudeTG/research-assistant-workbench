---
id: ADR-004
doc_kind: adr
status: accepted
date: 2026-05-11
feature_ids: [F013]
---
# 主 Agent Tool-Calling Loop

## 决策

F013 采用主 Agent tool-calling loop 替代 F012 的规则优先 `AgentIntentRouter` 主路径。项目级聊天中，模型应基于用户问题和工具说明自行决定调用 `web_search`、`paper_rag`、`memory_recall`。Spring Boot 继续作为主系统和证据/事件/数据权威；优先使用 Spring AI 原生 tool calling，若当前 OpenAI-compatible provider 不稳定，再实现显式 JSON action loop 作为 fallback。

暂不全量切换到 Python Agent 生态。Python/LangGraph/AutoGen 类 runtime 作为后续复杂子 Agent、长任务 checkpoint、人类审批或并行编排 Feature 的候选，不进入 F013。

## 背景

F012 已经接入 Tavily、web evidence 和工具 telemetry，但工具选择由规则路由预先决定。天气查询示例暴露了架构偏差：用户问“查询深圳今天的天气”，规则没有命中 `联网/搜索/latest` 等关键词，后端没有调用 Tavily，而模型仍生成“我现在就查询”的承诺式话术。

用户明确希望体验参考 Codex 或主流 Agent：主 Agent 自己判断是否联网、是否 RAG、是否调子 Agent。第一性原理上，工具选择是 Agent 推理的一部分，不应由应用层关键词表决定。应用层应该做的是工具执行、权限边界、证据记录、降级处理和可恢复性。

## 备选方案

- 继续扩展规则关键词：实现最快，但会把“查询/今天/天气”修成特例，继续漏掉同类自然表达，也违背主 Agent 自主工具选择的产品心智。
- 全量迁移 Python Agent 生态：LangGraph/AutoGen 等更适合复杂多 Agent 编排，但当前项目已有 Spring Boot 数据、事件、证据、Harness 和 UI 基线，全量迁移会显著增加一致性、部署和回归成本。
- Spring Boot 内实现主 Agent tool loop：能最小化迁移成本，同时把工具选择权交还模型。若 provider 原生 tool calling 不可用，可用 JSON action loop 保持同样架构边界。

## 影响

- `AgentIntentRouter` 不再作为项目聊天主路径的工具门卫；可暂时保留给旧测试或 fallback，后续再移除。
- 需要新增 `ProjectAgentToolLoop` / `ProjectAgentTools` 一类边界，避免 `SupervisorService` 继续膨胀。
- 测试重点从“规则分类正确”转为“模型工具循环暴露工具、工具执行被记录、证据边界正确”。
- 事件和证据合同沿用 F012，避免 UI 和数据层被架构切换牵连。
- Provider 能力成为显式风险：必须先做 Spring AI tool-calling spike，并记录是否需要 JSON action loop fallback。
