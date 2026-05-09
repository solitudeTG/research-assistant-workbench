---
id: F006
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# Agent 编排与过程事件

## 目标

让项目级研究问答通过 Supervisor 主导的过程事件可见化，复杂任务进入 plan-execute，子 Agent 步骤、检索、证据和回答增量都通过同一事件合同输出。

## 范围

- 范围内：project-scoped chat request。
- 范围内：run lifecycle、plan、step、retrieval、evidence、answer delta 和 completion 事件。
- 范围内：现有 Supervisor、TaskRouter、PlanExecuteFacade 的项目级改造。
- 范围外：完整候选确认、三栏 UI 和最终 Evidence 文档。

## 验收标准

- `POST /api/projects/{projectId}/sessions/{sessionId}/messages` 可以创建一次 project-scoped run。
- 一次回答至少产生 `run.started -> agent.plan.created -> agent.step.started -> retrieval.started -> retrieval.completed -> evidence.evaluated -> answer.delta -> answer.completed -> run.completed`。
- 不引入第二套不兼容 SSE 格式。
- `mvn -Dtest=ProjectRunEventFlowTest,SupervisorServiceLogicTest,TaskRouterTest,ChatControllerTest,ChatStreamControllerTest test` 通过。

## 合同

- API：项目级消息发送接口和 run SSE 地址。
- 事件：`run.*`、`agent.*`、`retrieval.*`、`evidence.evaluated`、`answer.*`。
- 数据：`assistant_answer`、`stream_event_record`。
- UI：中心对话和过程时间线可消费。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## 下一步

按 F002 实施计划 Task 4 进行 TDD 实现。
