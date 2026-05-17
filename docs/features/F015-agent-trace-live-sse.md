---
id: F015
doc_kind: feature
status: completed
owner: codex
created: 2026-05-11
updated: 2026-05-12
parent_feature: F002
---
# Agent Trace Event Contract and Live SSE

## Goal

F015 将现有“可回放的 run event”升级为“真实可直播、可审计、可恢复的 Agent Trace”。它的目标不是先做一个完整多 Agent 平台，而是先修正当前 SSE 的核心限制：前端不应只在回答完成后拿到一批已经生成好的事件，而应在研究流程真实发生时持续收到结构化事件，从而支撑会话页中的“研究过程、证据矩阵、技术轨迹” UI。

这个 Feature 直接服务两个产品目标：

- 真实用户能看到回答为何可信：检索、证据评估、候选知识和弱证据边界是可追踪的。
- 简历/演示场景能证明系统能力：Agent 规划、工具调用、RAG、记忆命中和可靠性检查不是静态文案，而是来自后端事件流。

## Scope

- In scope: 定义 Agent Trace 事件合同，统一 `runId`、`messageId`、`actor`、`step`、`payload`、`sequence` 和 `createdAt` 语义。
- In scope: 将 F004 的 SSE replay projection 扩展为 live-tail projection，支持运行中事件持续推送。
- In scope: 使用现有事件发布端口和 Redis Stream adapter 作为事件总线候选，明确内存 backend 与 Redis backend 的行为差异。
- In scope: 保持 `Last-Event-ID` 恢复语义，断线后跳过已消费事件并继续读取后续事件。
- In scope: 让 F013 主 Agent 工具调用过程能发出真实 `tool.*`、`retrieval.*`、`memory.*`、`evidence.*`、`answer.*` trace 事件。
- In scope: 前端可由事件恢复出用户摘要、执行时间线、证据矩阵和技术轨迹所需的最小模型。
- Out of scope: 完整独立子 Agent runtime。
- Out of scope: Supervisor-Worker 并发调度、长任务队列、人类审批 checkpoint、跨进程 worker 编排。
- Out of scope: 把 Redis Stream 作为长期审计数据库。
- Out of scope: 重新设计资料库、知识库或会话 UI。F015 只提供 UI 所需真实事件合同。

## Phasing

### Phase 1: True Trace Over Current Main Agent

先不声称系统已经有真实并发子 Agent。主 Agent 仍可串行执行现有工具，但每个真实步骤必须发布事件：

- 运行开始和完成。
- 计划生成。
- 工具调用开始和完成。
- 检索开始、查询改写、命中、完成。
- 记忆命中。
- 证据评估。
- 回答增量和完成。
- 候选知识生成。
- 运行失败。

这一期的交付重点是“过程可观察性是真的”。

### Phase 2: Supervisor-Worker Runtime

在 Phase 1 合同稳定后，再把 `actor.agentRole` 从逻辑 worker 扩展为真实并发 worker。Redis Stream 汇聚各 worker 的事件，UI 的任务树自然展示并发、耗时、失败、重试和合并过程。

这一期不属于 F015 的首个实现范围，除非后续规格或计划显式升级。

## Acceptance Criteria

- 运行中的项目消息能够通过 SSE 持续收到事件，而不是仅在回答完成后 replay 一次。
- `answer.delta` 在回答生成过程中分片到达前端，不能只发送最终完整答案。
- `tool.called` 和 `tool.completed` 反映真实工具调用，不得由前端模拟。
- `retrieval.hit` 反映真实命中的 paper/web/memory/project knowledge 来源。
- `evidence.evaluated` 能表达强证据、弱证据、证据缺口和待确认结论。
- SSE `id` 继续使用 `eventId`，`event` 继续使用 wire-name `eventType`。
- `Last-Event-ID` 能在 replay 历史事件后继续 live-tail 后续事件。
- Redis Stream 后端可作为 live-tail 来源；内存 backend 在测试中也能验证 live-tail 语义。
- 前端只消费 SSE，不直接读取 Redis。
- 文档、后端 focused tests、前端模型测试和 Harness validation 通过。

## Contract

- API: 继续使用 `POST /api/projects/{projectId}/sessions/{sessionId}/messages` 创建 run。
- SSE: 继续使用 `GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events`，但语义从“replay then complete”升级为“replay then live-tail until terminal event or timeout”。
- Events: 见 [F015-agent-trace-live-sse-spec.md](../specs/F015-agent-trace-live-sse-spec.md)。
- Runtime: Spring Boot 继续是主系统和事件权威。Redis Stream 是运行期事件总线和短期恢复来源，不是长期审计数据库。
- UI: F014/F015 后续 UI 可根据 trace events 渲染研究过程模块，但不得展示后端没有真实发出的 Agent 或工具步骤。

## Design Anchors

- Prior event backbone: [F004-workbench-event-stream-sse.md](F004-workbench-event-stream-sse.md)
- Current run flow: [F006-agent-run-event-flow.md](F006-agent-run-event-flow.md)
- Main Agent tool loop: [F013-main-agent-tool-calling-loop.md](F013-main-agent-tool-calling-loop.md)
- Session process UI designs:
  - Stitch screen `23165dcfd0b047a7b2a78e488f4c6090`
  - Stitch screen `01b5f4bc0f1144b08d42c598c76cb948`

## Vision Gate Entry

- Original intent: 用户认可会话中展示 Agent 处理过程，但指出当前 SSE 仍像“伪流式”，并希望 UI 能真实展示中间流经哪些 Agent。
- User pain point: 如果后端只一次性返回完整结果，UI 的研究过程只能是回放或模拟，无法支撑简历中多 Agent、RAG、Redis Stream、SSE 和可靠性叙事。
- Alignment: F015 先补真实事件流和 live SSE 合同，再谈完整 Supervisor-Worker 并发。这样避免 UI 比系统真实能力跑得快。
- Non-goal: 不在本 Feature 首期实现完整多 Agent runtime。
- Exit Gate source: 本 Feature 与 linked spec。

## Links

- Spec: [F015-agent-trace-live-sse-spec.md](../specs/F015-agent-trace-live-sse-spec.md)
- Plan: [F015-agent-trace-live-sse-plan.md](../plans/F015-agent-trace-live-sse-plan.md)
- Evidence: [EV-013-f015-agent-trace-live-sse.md](../evidence/EV-013-f015-agent-trace-live-sse.md)
- UI Evidence: [EV-014-f015-research-process-ui.md](../evidence/EV-014-f015-research-process-ui.md)
- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)

## Result

F015 Phase 1 is complete. Project run SSE now replays and live-tails until terminal run events; the main Agent tool loop publishes trace events for tools, retrieval hits, memory hits, conservative evidence gaps, and paragraph answer deltas; and the frontend model folds these events into `agentTraces[runId]`.

The visual session UI now renders the tested `agentTraces` projection under assistant answers as a research process module. The UI stays honest that Phase 1 uses logical step labels, not a true parallel Supervisor-Worker runtime.
