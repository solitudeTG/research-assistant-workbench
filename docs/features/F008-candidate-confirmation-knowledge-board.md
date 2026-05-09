---
id: F008
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# 候选确认与知识板

## 目标

建立“AI 生成候选，用户确认后写入知识板”的长期知识治理闭环，防止自动生成内容污染已确认项目知识。

## 范围

- 范围内：知识候选列表、接受、编辑后接受、标为待验证、忽略。
- 范围内：知识板分区、手动创建、更新、移动、归档。
- 范围内：`candidate.created` 与 `knowledge.entry.created` 事件。
- 范围外：候选生成模型质量优化、三栏 UI 细节、反馈评分。

## 验收标准

- 创建候选不会自动创建 `KnowledgeEntry`。
- 只有接受、编辑后接受或手动创建会写入知识板。
- 知识条目归档不做硬删除。
- 知识板按 `current_candidates`、`core_concept`、`method_route`、`confirmed_finding`、`open_question` 分区。
- `mvn -Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest test` 通过。

## 合同

- API：候选与知识板接口。
- 事件：`candidate.created`、`knowledge.entry.created`。
- 数据：`knowledge_candidate`、`knowledge_entry`。
- UI：右侧候选确认和知识板视图可消费。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## 下一步

按 F002 实施计划 Task 6 进行 TDD 实现。
