---
id: F007
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# 检索分层与证据边界

## 目标

明确 L3 Memory Recall、Paper RAG 和 Web Supplement 的边界，确保回答可以被证据状态约束，而不是把历史记忆、论文证据和联网内容混成不可审计来源。

## 范围

- 范围内：检索模式分层。
- 范围内：证据来源持久化和 retrieval trace。
- 范围内：`SUFFICIENT / WEAK / NONE` 与 `LOCAL_EVIDENCE / LOCAL_WEAK_EVIDENCE / WEB_SUPPLEMENT / REFUSAL`。
- 范围外：用户候选确认、feedbackScore 更新、UI 展示细节。

## 验收标准

- 强论文证据输出 `SUFFICIENT` 与 `LOCAL_EVIDENCE`。
- 弱本地证据且允许联网时输出 `WEAK` 与 `WEB_SUPPLEMENT`。
- 无证据且不允许联网时输出 `NONE` 与 `REFUSAL`。
- L3 记忆可补充上下文，但不计作当前论文证据。
- `mvn -Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest test` 通过。

## 合同

- API：回答证据查询接口。
- 事件：`retrieval.completed`、`evidence.evaluated`。
- 数据：`evidence_source`、retrieval trace、answer evidence state。
- UI：右侧证据来源视图可消费。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## 下一步

按 F002 实施计划 Task 5 进行 TDD 实现。
