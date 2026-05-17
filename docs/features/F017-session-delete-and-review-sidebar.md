---
id: F017
doc_kind: feature
status: completed
owner: codex
created: 2026-05-13
updated: 2026-05-13
parent_feature: F002
---
# Session Delete and Review Sidebar

## 目标

修正研究会话页的两个产品边界问题：会话必须支持带二次确认的真删除；右侧栏必须清楚表达“当前回答审阅”职责，避免把过程命中、最终证据、候选草稿和长期知识混成同一类信息。

## 范围

- 范围内：会话列表增加常规操作入口，包含重命名和删除。
- 范围内：删除会话是真删除，用户点击删除后必须二次确认。
- 范围内：后端提供 `DELETE /api/projects/{projectId}/sessions/{sessionId}`，并显式清理该会话的消息、回答、证据、候选和运行事件。
- 范围内：右侧栏文案和空状态收束为当前回答审阅区，明确区分最终证据、候选草稿和项目知识摘要。
- 范围内：中间研究过程里的“证据与记忆”改成过程语义，避免和最终证据来源重名。
- 范围外：候选生成质量优化、自动知识沉淀、完整知识工作区重构、资料库重构、账号权限和多租户删除策略。

## 验收标准

- 会话列表每个会话有操作入口，可重命名，也可删除。
- 点击删除会出现二次确认；取消不改变任何数据。
- 确认删除后，当前项目下该会话不再出现在会话列表，前端自动切换到剩余会话或创建/等待新会话。
- 删除不存在或跨项目会话返回 `404`。
- 删除会话会清理该会话关联的聊天消息、`assistant_answer`、`evidence_source`、`knowledge_candidate`、`stream_event_record`，不留下可被 UI 继续召回的孤儿研究痕迹。
- 右侧栏标题或上下文文案表达“当前回答审阅”，不再暗示它是完整知识库。
- 右侧“知识”为空或内容很少时给出明确说明和跳转/切换到完整知识工作区的入口。
- 右侧“候选”为空时说明本轮尚未生成候选，而不是看起来像数据坏了。
- 中间研究过程将过程命中与最终证据来源区分开。
- 后端、前端模型测试和 Harness 文档检查通过。

## 合同

- API：新增 `DELETE /api/projects/{projectId}/sessions/{sessionId}`，成功返回 `204 No Content`。
- 事件：本 Feature 不新增事件合同。
- 数据：删除会话采用后端显式清理，避免依赖既有 `on delete set null` 产生孤儿研究痕迹。
- UI：会话行操作为局部菜单或等价紧凑操作组；删除使用浏览器确认或等价二次确认控件。

## 入口判断

Start Gate: `needs retrieval` 后升级为本 Feature 提供 durable Vision Anchor。

Vision Gate Entry:

- Original intent: 用户发现会话只能改名、不能删除；右侧知识/证据/候选和中间研究过程的证据概念混乱；知识和候选长期无数据造成误导。
- User pain point: 用户无法完成基础会话清理，也无法判断哪些信息是过程、哪些是最终证据、哪些是可沉淀知识。
- Alignment: 本 Feature 只处理已确认的产品边界和真删除能力，不扩大到候选生成质量或知识库完整重构。
- Non-goal: 不为了填满右侧 tab 而伪造知识或候选数据。
- Exit Gate source: 本 Feature 与 linked spec。

## 链接

- Spec: [F017-session-delete-and-review-sidebar-spec.md](../specs/F017-session-delete-and-review-sidebar-spec.md)
- Plan: [F017-session-delete-and-review-sidebar-plan.md](../plans/F017-session-delete-and-review-sidebar-plan.md)
- Evidence: [EV-016-f017-session-delete-and-review-sidebar.md](../evidence/EV-016-f017-session-delete-and-review-sidebar.md)
- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Related Feature: [F008-candidate-confirmation-knowledge-board.md](F008-candidate-confirmation-knowledge-board.md)
- Related Feature: [F010-three-column-workbench-ui.md](F010-three-column-workbench-ui.md)
- Related Feature: [F015-agent-trace-live-sse.md](F015-agent-trace-live-sse.md)

## 结果

F017 已完成。项目会话现在支持带二次确认的真删除；后端按项目/会话边界显式清理该会话的研究痕迹；前端会话行提供重命名和删除操作；右侧栏收束为当前回答审阅区，并区分最终证据、候选草稿和项目知识摘要；中间研究过程改用“过程命中”避免和最终证据来源混淆。

## 下一步

回到 F016 Retrieval Observability 或后续候选生成质量改进；不要在 F017 内继续扩展知识生成链路。
