---
id: F001
status: completed
owner: codex
updated: 2026-05-09
---
# Harness 工程闭环

## 目标

建立一套轻量 Harness 层，让下一轮重构可以被拆分、分派、验证和恢复，同时不丢失架构意图。

## 范围

- 范围内：工作看板、Feature、ADR、Evidence、模板和结构校验脚本。
- 范围内：为下一代研究工作台重构建立初始记录。
- 范围外：数据库化知识系统、搜索索引、自定义 Harness UI。

## 验收标准

- 仓库存在 `docs/BACKLOG.md`。
- 仓库存在 `docs/features`、`docs/decisions`、`docs/evidence`、`docs/harness/templates`。
- 有脚本可以校验 Harness 文档的必要结构。
- 下一轮重构有 Feature 页和 ADR，能防止目标漂移。
- F001 标记完成前，必须有验证输出写入 Evidence。

## 合同

- API：无。
- 事件：无。
- 数据：Markdown 文档是事实源。
- UI：无。

## 链接

- 计划：[2026-05-09-harness-engineering-loop.md](../superpowers/plans/2026-05-09-harness-engineering-loop.md)
- ADR：[ADR-001-markdown-harness-source-of-truth.md](../decisions/ADR-001-markdown-harness-source-of-truth.md)
- Evidence：[EV-001-harness-bootstrap.md](../evidence/EV-001-harness-bootstrap.md)

## 下一步

已完成 Harness 基线建立。后续开发从 F002 Epic 下的 F003+ 子 Feature 开始。
