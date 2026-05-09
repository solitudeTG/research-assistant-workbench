---
id: F004
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# 工作台事件骨干与 SSE 投影

## 目标

建立后端过程事件骨干，让 Agent、检索、证据、资料状态、候选、知识板和反馈过程都能以统一事件 envelope 记录，并通过 SSE 投影给前端。

## 范围

- 范围内：事件 envelope、事件类型枚举、事件发布端口、测试用内存实现。
- 范围内：Redis Stream 适配器或在编码前新增 ADR 明确替代方案。
- 范围内：`/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events` SSE 投影。
- 范围外：具体 Agent 业务编排、资料解析、前端三栏 UI。

## 验收标准

- 同一 `runId` 内事件 `sequence` 单调递增。
- SSE 事件 `id` 等于 `eventId`，事件名等于 `eventType`。
- `Last-Event-ID` 能跳过已消费事件。
- 前端不直接读取 Redis。
- `mvn -Dtest=StreamEventEnvelopeTest,SseProjectionControllerTest test` 通过。

## 合同

- API：`GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events`。
- 事件：F002 规格中的 `run.*`、`agent.*`、`retrieval.*`、`evidence.*`、`answer.*`、`candidate.*`、`knowledge.*`、`source.*`、`memory.*`、`feedback.*`。
- 数据：`stream_event_record` 或 Redis Stream 记录。
- UI：SSE 投影供前端过程时间线消费。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## 下一步

按 F002 实施计划 Task 2 进行 TDD 实现。
