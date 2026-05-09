---
id: F011
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# F002 端到端验收与 Evidence 收尾

## 目标

在 F003-F010 实现后，完成 F002 端到端验证、Evidence 记录和 Harness 收尾，确保下一代研究工作台不是只由局部测试证明，而是由可追溯证据证明。

## 范围

- 范围内：后端全量测试、前端模型测试、Harness 校验。
- 范围内：API/SSE 样例、浏览器验证记录、已知限制和回滚说明。
- 范围内：更新 F002 Feature 与 BACKLOG 状态。
- 范围外：新增业务能力。

## 验收标准

- `mvn test` 通过。
- `node --test src/main/resources/static/tests/f002-workbench-model.test.mjs` 通过。
- `python scripts/knowledge_check.py` 通过。
- 存在 `docs/evidence/EV-002-f002-implementation-validation.md`。
- F002 Feature 链接到最终 Evidence。

## 合同

- API：不新增。
- 事件：记录验证样例。
- 数据：Evidence 文档。
- UI：记录浏览器验证证据。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## 下一步

等待 F003-F010 完成后执行 F002 实施计划 Task 9。
