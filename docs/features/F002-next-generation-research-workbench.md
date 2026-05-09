---
id: F002
status: epic
owner: codex
updated: 2026-05-09
---
# 下一代研究工作台

## 目标

围绕“项目级长期研究工作台”重建系统，让简历中的能力可以真实演示：多 Agent 协作、Redis Stream 到 SSE 的过程渲染、资料接入状态机、三层记忆、混合检索、证据治理和反馈驱动学习。

## 范围

- 范围内：项目级工作区模型、资料库、研究会话、知识板、候选确认、证据检查、Agent 过程时间线和流式事件合同。
- 范围内：当替换比保留更低成本时，直接替换当前低价值实现。
- 范围外：完整账号体系、权限系统、多租户 SaaS 行为，以及不支撑简历或产品主循环的发散功能。

## 验收标准

- UI、后端实体和流式事件共享同一份文档化合同。
- 用户可以进入研究项目、导入资料、提问、查看证据，并把确认后的知识沉淀到知识板。
- 多 Agent 或分阶段处理过程通过 SSE 事件可见，而不是只存在于最终回答背后。
- Redis Stream 要么作为事件骨干被实现，要么在编码前通过 ADR 明确否决。
- 长期记忆有清晰写入触发器，并避免自动污染已确认知识。
- 点赞点踩会更新检索评分；如果暂不实现，必须明确拆成后续 Feature。

## 合同

- API：在 F002 正式规格中定义。
- 事件：Agent、检索、证据、记忆和回答流事件。
- 数据：`Project`、`ResearchSession`、`SourceDocument`、`KnowledgeCandidate`、`KnowledgeEntry`、`EvidenceSource`、流式事件记录。
- UI：采用 `docs/product/research-workbench-ui-interaction-spec.md` 中的三栏研究工作台方向。

## 链接

- 正式规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 实施计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- 开发前 Vision Gate：[VG-001-f002-before-development.md](../reviews/VG-001-f002-before-development.md)
- 子 Feature：[F003 项目级数据模型与基础 API](F003-project-workbench-model-api.md)
- 子 Feature：[F004 工作台事件骨干与 SSE 投影](F004-workbench-event-stream-sse.md)
- 子 Feature：[F005 项目级资料接入状态机](F005-project-source-status-machine.md)
- 子 Feature：[F006 Agent 编排与过程事件](F006-agent-run-event-flow.md)
- 子 Feature：[F007 检索分层与证据边界](F007-retrieval-evidence-boundary.md)
- 子 Feature：[F008 候选确认与知识板](F008-candidate-confirmation-knowledge-board.md)
- 子 Feature：[F009 FeedbackScore 检索闭环](F009-feedback-score-loop.md)
- 子 Feature：[F010 三栏研究工作台 UI](F010-three-column-workbench-ui.md)
- 子 Feature：[F011 F002 端到端验收与 Evidence 收尾](F011-f002-end-to-end-validation.md)
- 产品 UI 规格：[research-workbench-ui-interaction-spec.md](../product/research-workbench-ui-interaction-spec.md)
- 架构护栏：[智能研究助手Agent-实现对齐与追问护栏-V2.md](../project-goal-alig/智能研究助手Agent-实现对齐与追问护栏-V2.md)
- ADR：[ADR-002-rebuild-around-project-workbench.md](../decisions/ADR-002-rebuild-around-project-workbench.md)

## 下一步

从 F003 项目级数据模型与基础 API 开始开发。F002 只作为 Epic 和总合同，不直接承载全部代码验收。
