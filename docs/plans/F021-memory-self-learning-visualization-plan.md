---
id: F021-PLAN
doc_kind: plan
status: completed
updated: 2026-05-16
feature_ids: [F021]
---
# F021 Memory Self-Learning Visualization Plan

## Gate Results

- Start Gate: `needs feature/spec/plan`，已创建 F021 anchor。
- Task class: `high-risk`，因为该任务影响 UI 一级信息架构、事件语义、记忆可信边界和演示验收路径。
- Vision Anchor: [F021 Feature](../features/F021-memory-self-learning-visualization.md)。
- Delegation Gate: `authorized`，用户明确要求复杂任务拆分为 subagent 执行。
- TDD: implementation code 必须先写失败测试，再写生产代码。
- UI Gate: 涉及 UI 的实现前必须先用 Stitch 设计交互图。

## Steps

- [x] 创建 F021 Feature / Spec / Plan。
- [x] 用 Stitch 生成“认知/Knowledge 一级工作区”和“研究过程自学习闭环”交互图。
- [x] 根据 Stitch 设计和 spec 拆分 implementation subagents。
- [x] 后端 RED：补 memory/feedback/candidate 事件或 payload 所需 focused tests。
- [x] 后端 GREEN：最小扩展事件 data 或查询投影，不改存储模型。
- [x] 前端模型 RED：扩展 `f002-workbench-model.test.mjs` 覆盖 memory trace、candidate/knowledge transition、feedback applied。
- [x] 前端模型 GREEN：扩展 `workbench-model.js` 的状态折叠。
- [x] UI RED/verification target：定义认知工作区与研究过程闭环的可验证状态。
- [x] UI GREEN：在现有静态工作台中实现一级认知工作区和研究过程展示。
- [x] 浏览器验证桌面主路径，确认文本不重叠、一级工作区边界清楚。
- [x] 运行 focused backend tests、frontend model tests、`git diff --check`、`python .\scripts\knowledge_check.py`。
- [x] 记录 Evidence，更新 Backlog 和 Feature 状态。

## Subagent Split

- Backend worker：负责事件/payload、memory recall context-only 边界、feedback applied 可观察性、focused backend tests。
- Frontend model worker：负责 `workbench-model.js` 状态投影和 model tests。
- UI worker：负责根据 Stitch 设计实现工作区布局和研究过程展示，并与浏览器验证配合。

每个 worker 都不得回滚未识别的现有改动，不得越权修改其他 worker 的拥有文件。主 agent 负责集成、冲突处理、最终验证和 Harness closeout。

## Stitch Design Artifacts

- Cognition workspace screen: Stitch `projects/6874803135901272290/screens/8f3141d323bf48ec9e505b1728fe938d`
- Session memory loop screen: Stitch `projects/6874803135901272290/screens/72cdb73e24d74d239e91f48c2587e573`
- Local assets:
  - `.stitch/designs/f021-cognition-workspace.html`
  - `.stitch/designs/f021-cognition-workspace.png`
  - `.stitch/designs/f021-session-memory-loop.html`
  - `.stitch/designs/f021-session-memory-loop.png`
  - `.stitch/designs/f021-local-knowledge-smoke.png`

## Completion Update

- Implementation split completed through backend, frontend model, and UI subagents.
- Backend focused suites passed for memory context-only events, feedback-applied payloads, ranking score propagation, retrieval diagnostics, candidate confirmation, and knowledge board boundaries.
- Frontend model tests passed for L1/L3 memory trace separation, candidate/confirmed-knowledge separation, and feedback-applied trace folding.
- UI visual smoke passed through local Edge headless fallback after the Codex in-app Browser setup timed out; the rendered page activated the first-level `knowledge` workspace and displayed global cognition, confirmed project knowledge, candidate review queue, and learning trace.

## Rollback

F021 应保持存储模型不变。若 UI 或事件投影引发回归，可以回滚新增前端工作区和事件 payload 扩展，保留 F008/F009/F015/F016/F017/F020 已有能力不变。
