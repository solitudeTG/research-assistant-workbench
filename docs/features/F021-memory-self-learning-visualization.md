---
id: F021
doc_kind: feature
status: completed
owner: codex
created: 2026-05-16
updated: 2026-05-17
parent_feature: F002
---
# Memory Self-Learning Visualization

## 目标

F021 把已有 L1/L2/L3 记忆、候选知识确认和反馈评分闭环串成一个可观察、可演示、可验收的研究自学习流程。用户应能看见系统从哪里记起、沉淀了什么、哪些内容仍待确认、反馈如何影响后续检索，而不是只能相信这些能力在后端发生。

## 范围

- 范围内：定义并展示三层记忆的产品边界。
- 范围内：L1 短期会话工作记忆展示为当前会话连续性、压缩摘要和本轮命中状态。
- 范围内：L2 作为一级“认知/Knowledge”工作区，管理项目知识板、待确认候选，以及 `USER.md`、`SOUL.md`、`Research_state.md` 代表的全局认知。
- 范围内：L3 长期研究沉淀 `memory_entry` 展示为 memory recall 上下文和历史沉淀记录，不作为 confirmed knowledge 直接编辑。
- 范围内：在研究过程 UI 中展示 `memory.hit`、`memory.completed`、候选状态变化、`knowledge.entry.created` 和 `feedback.applied`。
- 范围内：让用户反馈后的 evidence/chunk 更新数量可见，并让后续检索或诊断能体现 feedback score 参与排序。
- 范围内：UI 实现前先用 Stitch 设计一级认知工作区和研究过程闭环交互图。
- 范围外：不重写记忆存储表。
- 范围外：不新增向量库、复杂 reranker、完整用户画像或多租户偏好系统。
- 范围外：不允许 AI 自动写入 confirmed knowledge；确认知识仍必须来自用户 accept、edit-and-accept 或手动创建。
- 范围外：不把 memory context 当作 paper/web citation evidence。
- 范围外：不实现并行 Agent runtime。

## 验收标准

- 在一次项目消息回答中，前端能显示 L1/L3 memory hit，或明确显示本轮无记忆命中。
- `memory.completed` 能稳定进入 research process summary，且不会和最终 citation evidence 混淆。
- 一级“认知/Knowledge”工作区能区分 confirmed knowledge、pending candidate 和 global cognition。
- 用户确认候选后，candidate 状态更新，知识板更新，UI 不把 pending candidate 误当 confirmed knowledge。
- 用户提交 answer feedback 后，UI 或 trace 能显示 `feedback.applied` 的 evidence/chunk 更新数量。
- 后续 retrieval/ranking 或 retrieval diagnostics 能体现 feedback score 参与排序，至少后端 focused test 能断言排序变化。
- L3 memory recall 在 prompt、trace、UI 中标注为 memory context，不提升 paper evidence 强度。
- 后端 focused tests 覆盖 memory recall、candidate accept、feedback applied。
- 前端 model tests 覆盖 memory trace、candidate/knowledge transition、feedback-applied state。
- Harness validation 通过。

## 合同

- API：优先复用现有 candidate、knowledge-board、feedback、run event 和 retrieval diagnostics 端点；只有当前 payload 不足以支撑可见闭环时才扩展响应或事件 data。
- 事件：消费并必要时增强 `memory.hit`、`memory.completed`、`candidate.created`、`knowledge.entry.created`、`feedback.applied`、`retrieval.completed`。
- 数据：复用 `global_knowledge_note`、`knowledge_candidate`、`knowledge_entry`、`memory_entry`、`answer_feedback`、`evidence_source.feedback_score`、`document_chunk.feedback_score`。
- UI：新增或强化一级“认知/Knowledge”工作区；研究过程模块展示 L1/L3 召回与反馈闭环；L3 以只读上下文/沉淀记录呈现。

## Vision Anchor

原始目标不是“再加一个记忆页面”，而是回答面试和真实用户都会追问的问题：三层记忆是否真的参与回答，反馈是否真的改变系统行为，长期知识如何避免污染，以及这个系统为什么不是普通 RAG 聊天壳。F021 的最小正确路径是先让闭环可见、可信、可验收，再考虑更复杂的存储或算法扩张。

## 链接

- Spec: [F021-memory-self-learning-visualization-spec.md](../specs/F021-memory-self-learning-visualization-spec.md)
- Plan: [F021-memory-self-learning-visualization-plan.md](../plans/F021-memory-self-learning-visualization-plan.md)
- Evidence: [EV-020-f021-memory-self-learning-visualization.md](../evidence/EV-020-f021-memory-self-learning-visualization.md)
- Handoff: [2026-05-16-memory-self-learning-visualization.md](../handoffs/2026-05-16-memory-self-learning-visualization.md)
- Related Feature: [F008-candidate-confirmation-knowledge-board.md](F008-candidate-confirmation-knowledge-board.md)
- Related Feature: [F009-feedback-score-loop.md](F009-feedback-score-loop.md)
- Related Feature: [F015-agent-trace-live-sse.md](F015-agent-trace-live-sse.md)
- Related Feature: [F016-retrieval-observability.md](F016-retrieval-observability.md)
- Related Feature: [F017-session-delete-and-review-sidebar.md](F017-session-delete-and-review-sidebar.md)
- Related Feature: [F020-plan-execute-evidence-carry-through.md](F020-plan-execute-evidence-carry-through.md)

## Completion Update

2026-05-16: Completed. F021 now exposes the existing memory and self-learning loop without adding a new storage model: L3 memory recall is projected as context-only trace state, L2 cognition is a first-level Knowledge workspace boundary, pending candidates remain separate from confirmed knowledge, and feedback-applied events expose affected evidence/chunk counts plus ranking score propagation.

## Reset Update

2026-05-16: Reopened for F021.1 zero-base correction after live service validation found the visible feedback path missing and follow-up review found the complete demo loop was not yet recoverable. F021.1 narrows the remaining work to L2 confirmed project knowledge recall, real `feedback.applied` run-stream identity, and session-history trace recovery. Plan: [F021.1-self-learning-closed-loop-reset-plan.md](../plans/F021.1-self-learning-closed-loop-reset-plan.md)

## F021.1 Closeout

2026-05-17: Completed. The F021.1 reset verified the smallest recoverable self-learning loop from code rather than only UI state:

- Confirmed `knowledge_entry` rows are loaded as L2 project knowledge and included in the next project-agent prompt as context explicitly marked not citation evidence.
- The run trace publishes confirmed project knowledge as `memory.hit` with `memoryLayer=L2`, `sourceType=project_knowledge`, and `contextOnly=true`; this contributes to memory context counts but does not create `evidence_source` rows or citation counts.
- Project answer feedback resolves persisted answer context before publishing `feedback.applied`, so the event carries the answer's `projectId`, `sessionId`, `runId`, and `answerId`.
- Reloaded project session messages return assistant `answerId` and `runId`, preserving feedback controls and research-process trace recovery after restart.

Closeout verification is recorded in [EV-020](../evidence/EV-020-f021-memory-self-learning-visualization.md). Remaining caution: if future work requires strict replay of feedback events after `run.completed`, treat that as an event-stream policy hardening item, not as a F021 memory-loop blocker.
