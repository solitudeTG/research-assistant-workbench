---
id: F003
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# 项目级数据模型与基础 API

## 目标

建立下一代研究工作台的项目级领域底座，让 `Project` 成为最高层对象，并让会话、资料、回答、证据、候选、知识条目和反馈都能挂接到项目边界下。

## 范围

- 范围内：项目、研究会话、资料、回答、证据、知识候选、知识条目、反馈、事件记录的数据库表。
- 范围内：`/api/projects` 与 `/api/projects/{projectId}/sessions` 基础 API。
- 范围内：仓储测试和控制器测试。
- 范围外：Redis Stream、资料处理状态机、Agent 编排、检索逻辑和前端 UI。

## 验收标准

- 可以创建、查询、列出项目。
- 可以在项目下创建和列出研究会话。
- 会话列表不会跨项目泄漏。
- 数据模型为后续资料、回答、证据、候选、知识板和反馈保留项目级外键。
- `mvn -Dtest=ProjectRepositoryTest,ProjectControllerTest test` 通过。

## 合同

- API：`GET/POST /api/projects`，`GET /api/projects/{projectId}`，`GET/POST /api/projects/{projectId}/sessions`。
- 事件：无。
- 数据：`research_project`、`research_session` 以及 F002 所需核心表骨架。
- UI：无直接 UI，供后续三栏工作台消费。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- Vision Gate：[VG-001-f002-before-development.md](../reviews/VG-001-f002-before-development.md)

## 下一步

按 F002 实施计划 Task 1 进行 TDD 实现。
