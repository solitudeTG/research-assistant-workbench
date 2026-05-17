---
id: F021-SPEC
doc_kind: spec
status: active
updated: 2026-05-16
feature_ids: [F021]
---
# F021 Memory Self-Learning Visualization Spec

## Problem

项目已经具备 L1 working memory、L2 global/project knowledge、L3 memory recall、candidate confirmation、feedback scoring 和 live trace，但这些能力分散在后端服务、事件和局部 UI 中。用户无法稳定看见系统如何“记起、沉淀、确认、反馈、再影响检索”，因此三层记忆和自学习闭环很难被验证，也容易被误解成普通 RAG 聊天。

## Required Behavior

1. 定义 UI 可见的三层记忆语义：
   - L1：当前会话工作记忆，来自 `WorkingMemory` 的 `rollingSummary`、`salientFacts`、`compressedRounds` 和本轮短期上下文。
   - L2：稳定认知层，包括 `global_knowledge_note` 的 `USER`、`SOUL`、`RESEARCH_STATE`，以及用户确认后的 `knowledge_entry`。
   - L3：长期研究沉淀 `memory_entry`，由 memory recall 检索后作为 context 使用。
2. 一级“认知/Knowledge”工作区必须把 confirmed knowledge、pending candidates 和 global cognition 分开展示。
3. 待确认候选在被用户 accept、edit-and-accept 或手动创建前，不得显示为 confirmed knowledge。
4. `memory.hit` 和 `memory.completed` 必须进入前端状态模型，并能支撑研究过程摘要显示命中层级、snippet、score 和来源。
5. `feedback.applied` 必须进入前端状态模型，并能显示 rating、updated evidence count、updated chunk count。
6. 后续 retrieval 或 diagnostics 必须能表达 feedback score 对排序的影响，至少在测试中可断言。
7. memory context 在 prompt、trace、UI 和最终答案审阅中必须与 citation evidence 区分。

## Data Contract

### Memory Trace Item

- `memoryLayer`: `L1`、`L2` 或 `L3`
- `sourceType`: `working_memory`、`global_knowledge`、`project_knowledge` 或 `long_term_memory`
- `snippet`
- `score`，可选；没有真实分数时不伪造
- `sourceId`，可选
- `contextOnly`: `true`

### Cognition Workspace Sections

- `global_cognition`
  - `USER.md`
  - `SOUL.md`
  - `Research_state.md`
- `confirmed_knowledge`
  - 来自 `knowledge_entry`
- `pending_candidates`
  - 来自 `knowledge_candidate`
- `recent_changes`
  - 来自 candidate / knowledge / feedback / memory events 的 bounded 摘要

### Feedback Applied Projection

- `rating`
- `feedbackScore`
- `updatedEvidenceSourceCount`
- `updatedChunkCount`
- `appliedEvidenceSourceIds`
- `runId` / `answerId` / `projectId`，按现有事件上下文携带

## UI Requirements

- 左侧一级 rail 增加或强化“认知/Knowledge”入口，使其替换主工作区，而不是作为聊天右侧小 tab。
- 认知工作区采用工具型布局：顶部上下文栏、主列表/分区、右侧详情 inspector。
- 研究过程模块显示本轮 memory recall 和 feedback loop，不把过程命中写成最终证据。
- L3 记忆以“长期记忆召回/沉淀记录”呈现，默认只读。
- 文案应强调候选、确认知识、长期记忆三者的可信度差异。

## Non-Goals

- 不新增 L2 或 L3 存储表。
- 不把 `source_document` 直接纳入 L2；资料源只有被提炼并确认后才成为知识。
- 不把 `memory_entry` 提升为 confirmed knowledge。
- 不实现账号偏好、多租户画像、反馈撤销或复杂 reranking。
- 不为演示伪造并行 Agent、伪造 memory hit 或伪造 score。

## Verification

- 后端 RED/GREEN tests：
  - memory recall trace 保持 context-only 语义。
  - candidate accept 后知识板更新，pending candidate 不提前进入 confirmed knowledge。
  - feedback applied 更新 evidence/chunk count，并影响后续 retrieval 排序。
- 前端 RED/GREEN tests：
  - `workbench-model.js` 折叠 `memory.*`、`candidate.*`、`knowledge.entry.created`、`feedback.applied`。
  - research process summary 区分 memory context 与 citation evidence。
  - knowledge workspace projection 区分 global cognition、confirmed knowledge、pending candidates。
- UI 验证：
  - Stitch 设计先行。
  - 本地应用启动后用浏览器验证桌面主路径。
- Harness：
  - `git diff --check`
  - `python .\scripts\knowledge_check.py`

