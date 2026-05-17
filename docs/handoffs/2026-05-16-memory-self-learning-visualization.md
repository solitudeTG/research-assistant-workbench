---
id: HANDOFF-2026-05-16-MEMORY-SELF-LEARNING
doc_kind: handoff
status: active
updated: 2026-05-16
feature_ids: []
---
# 三层记忆 + 自学习闭环可视化交接

## 当前状态

用户希望下一阶段实现“**三层记忆 + 自学习闭环可视化**”，用于强化简历中的以下卖点：

- `L1短期会话 + L2全局认知 + L3长期研究沉淀` 三层记忆系统。
- Token 水位线触发的记忆压缩与增量沉淀。
- 用户反馈闭环影响后续检索/回答。
- 知识候选经用户确认后写入知识板。
- 前端能展示记忆召回、自学习沉淀、反馈影响，而不是只在后端发生。

这不是一个“重写记忆系统”的任务。项目已经有不少底座，下一会话要做的是把现有能力串成**可演示、可验收的闭环**。

## 已有底座

### F008 候选确认与知识板

Source: [F008-candidate-confirmation-knowledge-board.md](../features/F008-candidate-confirmation-knowledge-board.md)

已有能力：

- `knowledge_candidate` 与 `knowledge_entry` 已分离。
- AI/流程只能创建 candidate draft。
- 只有用户 accept、edit-and-accept 或手动创建才能写入 `KnowledgeEntry`。
- 已有 API：
  - `GET /api/projects/{projectId}/answers/{answerId}/candidates`
  - `GET /api/projects/{projectId}/candidates`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/accept`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/edit-and-accept`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/mark-unverified`
  - `POST /api/projects/{projectId}/candidates/{candidateId}/ignore`
  - `GET /api/projects/{projectId}/knowledge-board`
  - `POST /api/projects/{projectId}/knowledge-board/entries`
  - `PATCH /api/projects/{projectId}/knowledge-board/entries/{entryId}`
  - `DELETE /api/projects/{projectId}/knowledge-board/entries/{entryId}`
- 已有事件：`candidate.created`、`knowledge.entry.created`。

### F009 反馈分数闭环

Source: [F009-feedback-score-loop.md](../features/F009-feedback-score-loop.md)

已有能力：

- `POST /api/projects/{projectId}/answers/{answerId}/feedback`
- `rating=up/down` 记录 answer-level feedback。
- 可选择 `evidenceSourceIds`，同项目同 answer 的证据会更新 `feedback_score`。
- paper evidence 可映射到 `document_chunk.feedback_score`。
- 后续 keyword/vector retrieval 会使用：
  `finalScore = relevanceScore + clamp(feedbackScore * 0.05, -0.2, 0.2)`
- 已有事件：`feedback.applied`。

### 记忆后端

关键代码：

- [WorkingMemoryService.java](../../src/main/java/com/researchassistant/memory/WorkingMemoryService.java)
- [MemoryDepositService.java](../../src/main/java/com/researchassistant/memory/MemoryDepositService.java)
- [MemoryRecallService.java](../../src/main/java/com/researchassistant/memory/MemoryRecallService.java)
- [GlobalKnowledgeService.java](../../src/main/java/com/researchassistant/memory/GlobalKnowledgeService.java)
- [SupervisorService.java](../../src/main/java/com/researchassistant/orchestrator/SupervisorService.java)
- [ProjectAgentTools.java](../../src/main/java/com/researchassistant/orchestrator/ProjectAgentTools.java)

已有行为：

- L1：`WorkingMemoryService` 会维护 rolling summary、salient facts、compressed rounds。
- L3：`MemoryDepositService` 会在 message count 达到阈值后进行 incremental flush，写入 `memory_entry`。
- L2 倾向：`GlobalKnowledgeService` 提供全局 knowledge snapshot，并写每日记忆文件。
- `MemoryRecallService` 已可按关键词、时间、同 session boost 召回长期记忆。
- `SupervisorService` 和 `ProjectAgentTools` 已能发布 `memory.hit` / `memory.completed` trace events。

### 前端模型

关键代码：

- [workbench-model.js](../../src/main/resources/static/js/workbench-model.js)
- [workbench-app.js](../../src/main/resources/static/js/workbench-app.js)
- [f002-workbench-model.test.mjs](../../src/main/resources/static/tests/f002-workbench-model.test.mjs)

已有行为：

- 前端状态模型已经能折叠 `memory.hit` / `memory.completed`。
- trace summary 中已有 `memoryCount`。
- 右侧 sidebar 已能展示 evidence / candidate / knowledge board 相关状态。

## 推荐新 Feature

建议新会话创建：

```text
F021 Memory Self-Learning Visualization
```

推荐目标：

> 把已有 L1/L2/L3 记忆、知识候选确认、反馈分数闭环串成一个可观察的研究自学习流程，让用户能看到“系统从哪里记起、沉淀了什么、哪些内容待确认、反馈如何影响后续检索”。

## 建议范围

### In Scope

- 定义三层记忆的产品可见模型：
  - L1：当前会话工作记忆。
  - L2：项目/全局认知或确认后的知识板摘要。
  - L3：长期研究沉淀 `memory_entry`。
- 在研究过程 UI 中更清楚展示 memory recall：
  - 命中层级。
  - 命中 snippet。
  - score。
  - 来源是 working memory、global/project knowledge，还是 long-term memory。
- 在回答后展示候选知识流：
  - candidate created。
  - pending / accepted / edited_accepted / ignored / marked_unverified。
  - accepted 后进入知识板。
- 展示反馈闭环：
  - 用户 up/down 后显示 `feedback.applied`。
  - 展示更新了多少 evidence / chunk。
  - 后续检索 trace 能显示命中 evidence/chunk 的 feedback score 或“受反馈加权影响”。
- 补一条可演示路径：
  1. 第一轮对话产生记忆或候选。
  2. 用户确认候选进入知识板。
  3. 下一轮问题触发 memory recall。
  4. 用户反馈某个答案。
  5. 后续 retrieval/ranking 能看到反馈权重参与。

### Out Of Scope

- 不重写记忆存储表。
- 不做完整用户画像或多租户偏好系统。
- 不引入新的向量库或复杂 reranker。
- 不让 AI 自动写入 confirmed knowledge，仍必须经过用户确认。
- 不把 memory context 当作 paper evidence；记忆只能作为历史上下文或弱证据。
- 不做并行 Agent runtime。

## 第一性原理判断

这个功能的原始目标不是“再加一个记忆页面”，而是解决面试追问：

- 你的三层记忆是否真的参与回答？
- 用户反馈是否真的改变系统行为？
- 长期知识如何避免污染？
- 这个系统为什么不是普通 RAG 聊天壳？

所以优先实现**闭环可见性**，不要优先扩张存储、算法或中间件。

## 建议验收标准

- 在一次项目消息回答中，前端能看到 L1/L3 memory hit 或明确显示本轮无记忆命中。
- `memory.completed` 能稳定进入 research process summary。
- 用户确认候选后，知识板更新，candidate 状态更新，且 UI 不把 candidate 误当 confirmed knowledge。
- 用户提交 answer feedback 后，UI 或 trace 能显示 `feedback.applied` 的 evidence/chunk 更新数量。
- 后续检索结果或 retrieval diagnostics 能体现 feedback score 参与排序，至少后端测试能断言排序变化。
- 记忆召回内容在 prompt / trace / UI 中标注为 memory context，不被当作 citation evidence。
- focused backend tests 覆盖 memory recall、candidate accept、feedback applied。
- frontend model tests 覆盖 memory trace、candidate/knowledge transition、feedback-applied state。

## 建议开发顺序

1. **Start Gate + Retrieval**
   - 读取本 handoff。
   - 读取 F008、F009、F015、F016、F017、F020。
   - 创建 F021 Feature / Spec / Plan。

2. **后端最小闭环**
   - 如果 `feedback.applied` 前端所需 payload 不完整，先补后端事件 payload。
   - 如果 memory trace payload 对 L2/L3 区分不清，补结构化字段，不改存储。
   - 不要先做 UI 大改。

3. **前端模型**
   - 扩展 `workbench-model.js`，让 `memory.*`、`candidate.*`、`knowledge.entry.created`、`feedback.applied` 形成可展示状态。
   - 先写 `f002-workbench-model.test.mjs` RED。

4. **前端展示**
   - 在现有 research process / sidebar 内展示，不新建复杂页面。
   - 重点是“用户能看懂系统记起了什么、沉淀了什么、反馈改了什么”。

5. **验证**
   - 后端 focused tests。
   - 前端 model tests。
   - `git diff --check`。
   - `python .\scripts\knowledge_check.py`。
   - 如果改 UI，启动本地应用并用浏览器验证主路径。

## 可能需要看的测试

- [WorkingMemoryServiceTest.java](../../src/test/java/com/researchassistant/memory/WorkingMemoryServiceTest.java)
- [WorkingMemoryServiceLogicTest.java](../../src/test/java/com/researchassistant/memory/WorkingMemoryServiceLogicTest.java)
- [MemoryRecallServiceTest.java](../../src/test/java/com/researchassistant/memory/MemoryRecallServiceTest.java)
- [KnowledgeCandidateControllerTest.java](../../src/test/java/com/researchassistant/candidates/KnowledgeCandidateControllerTest.java)
- [KnowledgeBoardControllerTest.java](../../src/test/java/com/researchassistant/knowledge/KnowledgeBoardControllerTest.java)
- [ProjectFeedbackServiceTest.java](../../src/test/java/com/researchassistant/feedback/ProjectFeedbackServiceTest.java)
- [FeedbackControllerTest.java](../../src/test/java/com/researchassistant/feedback/FeedbackControllerTest.java)
- [ProjectRunEventFlowTest.java](../../src/test/java/com/researchassistant/orchestrator/ProjectRunEventFlowTest.java)
- [f002-workbench-model.test.mjs](../../src/main/resources/static/tests/f002-workbench-model.test.mjs)

## 当前工作区注意事项

- F020 已完成并记录到 [EV-019](../evidence/EV-019-f020-plan-execute-evidence-carry-through.md)。
- 当前工作区可能仍有未提交改动，包括 F020 和先前已存在的前端/F016 改动。新会话开始前先运行：

```powershell
git status --short
```

- 不要随手回滚未识别改动；先判断哪些是当前任务相关，哪些是已有用户/其他会话改动。

## 推荐首条开发命令

```powershell
Get-Content -Raw .\docs\handoffs\2026-05-16-memory-self-learning-visualization.md
Get-Content -Raw .\docs\BACKLOG.md
Get-Content -Raw .\docs\features\F008-candidate-confirmation-knowledge-board.md
Get-Content -Raw .\docs\features\F009-feedback-score-loop.md
```

然后走 Harness Start Gate，创建 F021 Feature / Spec / Plan，再按 TDD 开发。
