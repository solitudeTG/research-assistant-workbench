---
id: F009
status: planned
owner: codex
updated: 2026-05-09
parent_feature: F002
---
# FeedbackScore 检索闭环

## 目标

让用户点赞/点踩形成可观察闭环，回流到回答关联证据 chunk 的 `feedbackScore`，并影响后续检索与轻量重排优先级。

## 范围

- 范围内：项目级 answer feedback API。
- 范围内：有证据关联时更新 chunk `feedbackScore`。
- 范围内：无证据关联时只记录回答级反馈。
- 范围内：`feedback.applied` 事件。
- 范围外：复杂个性化推荐、长期用户画像和不可解释重排模型。

## 验收标准

- `rating=up` 增加关联证据 chunk 分数。
- `rating=down` 降低关联证据 chunk 分数并保留原因。
- 无 `evidenceSourceIds` 时不伪造 chunk 关联。
- 后续检索排序使用可解释轻量公式纳入 `feedbackScore`。
- `mvn -Dtest=ProjectFeedbackServiceTest,FeedbackControllerTest,PaperRagServiceTest test` 通过。

## 合同

- API：`POST /api/projects/{projectId}/answers/{answerId}/feedback`。
- 事件：`feedback.applied`。
- 数据：`answer_feedback`、chunk `feedbackScore`。
- UI：回答级点赞/点踩动作可消费。

## 链接

- 父 Feature：[F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## 下一步

按 F002 实施计划 Task 7 进行 TDD 实现。
