---
id: F005
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# 项目级资料接入状态机

## 目标

把论文、网页和个人笔记接入从临时附件升级为项目资料库动作，并用阶段化状态机暴露解析、索引、抽取、沉淀和失败重试过程。

## 范围

- 范围内：project-scoped source import API。
- 范围内：PDF/note 与 web 两条状态链。
- 范围内：失败阶段 `failureStage` 和可重试入口。
- 范围内：`source.status.changed` 事件发布。
- 范围外：知识候选确认、Agent 回答、三栏 UI 完整实现。

## 验收标准

- PDF/note 支持 `uploaded -> parsing -> indexing -> extracting -> indexed -> depositing -> deposited`。
- Web 支持 `submitted -> fetching -> extracting -> indexed -> depositing -> deposited`。
- 任意处理阶段失败后记录具体 `failureStage`。
- 失败资料可以重试，并再次发布状态事件。
- `mvn -Dtest=ProjectSourceStatusMachineTest,DocumentControllerTest,DocumentControllerStatusTest,DocumentProcessingJobTest,DocumentProcessingJobFailureTest test` 通过。

## 合同

- API：`POST/GET /api/projects/{projectId}/sources`，`GET/POST /api/projects/{projectId}/sources/{sourceId}` 相关状态与重试接口。
- 事件：`source.status.changed`。
- 数据：`source_document`、chunk、索引状态、失败阶段。
- UI：左侧资料库可消费状态和失败阶段。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## 下一步

按 F002 实施计划 Task 3 进行 TDD 实现。
