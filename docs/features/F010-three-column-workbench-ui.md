---
id: F010
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# 三栏研究工作台 UI

## 目标

把前端从单文档问答界面升级为三栏研究工作台：左侧项目/会话/资料，中间多轮研究对话，右侧知识板/证据来源/候选确认。

## 范围

- 范围内：静态 HTML/CSS/JS 工作台结构。
- 范围内：前端状态模型和 SSE 去重合并。
- 范围内：资料状态、回答 streaming、证据与候选右侧栏切换。
- 范围外：完整设计系统重做、账号偏好同步、多租户 UI。

## 验收标准

- 桌面端三栏均可用。
- 窄屏下文本和控件不重叠。
- `source.status.changed` 更新左侧资料状态。
- `answer.delta` 更新中间回答。
- `candidate.created` 不会直接增加知识板条目。
- `knowledge.entry.created` 会更新正确知识板分区。
- `node --test src/main/resources/static/tests/f002-workbench-model.test.mjs` 通过。

## 合同

- API：消费 F002 项目级 API。
- 事件：消费 F002 SSE 投影。
- 数据：前端本地状态模型。
- UI：三栏研究工作台。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- UI 规格：[research-workbench-ui-interaction-spec.md](../product/research-workbench-ui-interaction-spec.md)

## 下一步

按 F002 实施计划 Task 8 进行 TDD 和浏览器验证。
