---
id: SPEC-F002
status: completed
feature_ids: [F002]
updated: 2026-05-10
---
# F002 下一代研究工作台正式规格

## 规格结构提案

本规格按“先合同，后实现”的顺序组织，作为下一轮多 Agent 并行重构的事实源：

1. 产品边界：定义本轮必须交付的主循环和明确不做的能力。
2. 领域模型：定义项目、会话、资料、消息、证据、候选、知识板和记忆的实体边界。
3. API 合同：定义前后端同步调用边界。
4. Redis Stream 事件合同：定义后端过程事件的事实记录。
5. SSE 前端事件合同：定义浏览器可消费的过程投影。
6. Agent 写入边界：定义 Supervisor、子 Agent、检索、记忆、反馈和资料接入各自能写什么。
7. 前端状态模型：定义三栏工作台的状态来源和本地状态。
8. 验收证据：定义完成声明前必须留下的可复查证据。

## 目标

F002 的目标是把系统从“单文档问答 Demo”重构为“项目级长期研究工作台”。用户应能围绕一个稳定研究主题持续导入资料、发起多轮研究会话、查看回答证据、确认知识候选，并把高价值结论沉淀到项目知识板。

本轮不追求把所有想象中的研究平台能力一次性铺满。核心判断是：先把项目模型、事件过程、证据边界和知识写入边界定清楚，再让多个 Agent 可以按互不重叠的切片实现。

## 产品边界

### 范围内

- 项目级工作区：`Project` 是最高层对象，`ResearchSession` 是项目下的研究分支。
- 资料接入：支持 PDF、网页、个人笔记进入项目资料库，并暴露阶段化状态。
- 多轮研究对话：以项目和会话为上下文发问，默认本地知识优先，证据不足时可联网补充。
- 多 Agent 过程可视化：Supervisor 和子 Agent 的计划、检索、分析、写作、审查过程通过事件流可见。
- 三层记忆：L1 会话工作记忆、L2 全局认知、L3 长期研究记忆分别有独立写入触发器。
- 混合检索：区分 `L3 Memory Recall`、`Paper RAG`、`Web Supplement`，并保留 retrieval trace。
- 证据边界：回答必须给出证据充分性和输出模式。
- 知识候选确认：AI 只能生成候选，用户确认后才能写入项目知识板。
- 反馈闭环：点赞/点踩定位到回答关联证据，并回流到 chunk 的 `feedbackScore`。

### 范围外

- 完整账号、权限、多租户 SaaS。
- 通用任务图平台或完全去中心化 Agent 网络。
- 把每轮回答自动写入长期知识板。
- 为兼容旧 `sessionKey + documentId` 接口牺牲项目级模型。
- 未经证据边界约束的联网回答。

## 核心用户主循环

```text
创建/进入项目
-> 导入资料
-> 资料解析、索引、抽取候选
-> 发起研究对话
-> 本地知识优先回答
-> 证据弱时按需联网补充
-> 生成知识候选
-> 用户审查、编辑、确认
-> 写入项目知识板
-> 未来回答可召回已确认知识
```

关键规则：

- 对话是过程，知识板是结果。
- 资料是项目资产，不是临时附件。
- 候选是草稿，不是长期记忆。
- 只有用户确认后的条目才进入项目知识板。

## 领域模型

### Project

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 项目 ID |
| `topic` | string | 稳定研究主题 |
| `summary` | string | 项目摘要 |
| `createdAt` | datetime | 创建时间 |
| `updatedAt` | datetime | 更新时间 |
| `stats` | object | 资料数、会话数、知识条目数、候选数 |

### ResearchSession

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 会话 ID |
| `projectId` | string | 所属项目 |
| `title` | string | 会话标题 |
| `status` | enum | `continue` / `deposited` / `drafting` / `archived` |
| `workingMemoryId` | string | L1 工作记忆引用 |
| `lastMessageAt` | datetime | 最近消息时间 |

### SourceDocument

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 资料 ID |
| `projectId` | string | 所属项目 |
| `type` | enum | `pdf` / `web_page` / `note` |
| `title` | string | 资料标题 |
| `uri` | string | 文件、网页或内部笔记位置 |
| `status` | enum | 见资料状态机 |
| `failureStage` | enum/null | 失败阶段 |
| `errorMessage` | string/null | 面向调试的失败原因 |
| `depositedKnowledgeCount` | number | 已沉淀知识数量 |

资料状态机：

```text
PDF / note:
uploaded -> parsing -> indexing -> extracting -> indexed -> depositing -> deposited

web:
submitted -> fetching -> extracting -> indexed -> depositing -> deposited

failure:
任意处理中状态 -> failed
```

`failed` 必须保留 `failureStage`，可选值为 `fetching`、`parsing`、`indexing`、`extracting`、`depositing`。

### ChatMessage 与 AssistantAnswer

| 实体 | 关键字段 | 说明 |
| --- | --- | --- |
| `ChatMessage` | `id`、`projectId`、`sessionId`、`role`、`content`、`turnId` | 原始对话消息 |
| `AssistantAnswer` | `id`、`messageId`、`evidenceState`、`outputMode`、`citationCount`、`streamRunId` | 助手回答元数据 |

`evidenceState`：

- `SUFFICIENT`：本地证据足以支撑回答。
- `WEAK`：本地证据不足或覆盖不完整。
- `NONE`：没有足够证据，应拒答或要求补充资料。

`outputMode`：

- `LOCAL_EVIDENCE`
- `LOCAL_WEAK_EVIDENCE`
- `WEB_SUPPLEMENT`
- `REFUSAL`

### EvidenceSource

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 证据 ID |
| `answerId` | string | 所属回答 |
| `sourceType` | enum | `paper` / `project_knowledge` / `web` / `conversation_memory` |
| `sourceId` | string/null | 内部资料、知识条目或外部网页引用 |
| `snippet` | string | 证据片段或摘要 |
| `strength` | enum | `strong` / `medium` / `weak` |
| `relevanceScore` | number | 检索相关性 |
| `feedbackScore` | number | 用户反馈累计分 |
| `citationMeta` | object | 页码、URL、标题、作者等 |

### KnowledgeCandidate

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 候选 ID |
| `projectId` | string | 所属项目 |
| `sessionId` | string | 来源会话 |
| `answerId` | string/null | 来源回答 |
| `title` | string | 候选标题 |
| `statement` | string | 待确认知识表述 |
| `suggestedSection` | enum | `core_concept` / `method_route` / `confirmed_finding` / `open_question` |
| `sourceTypes` | array | `assistant_answer` / `paper_evidence` / `web_supplement` / `conversation_memory` / `user_note` |
| `status` | enum | `pending` / `accepted` / `edited_accepted` / `marked_unverified` / `ignored` |

### KnowledgeEntry

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 知识条目 ID |
| `projectId` | string | 所属项目 |
| `section` | enum | `current_candidates` / `core_concept` / `method_route` / `confirmed_finding` / `open_question` |
| `title` | string | 标题 |
| `content` | string | 用户确认后的内容 |
| `evidenceStatus` | enum | `confirmed` / `unverified` / `needs_review` |
| `sourceCandidateId` | string/null | 来源候选 |
| `evidenceSourceIds` | array | 支撑证据 |
| `updatedAt` | datetime | 更新时间 |

### Memory

| 层级 | 载体 | 写入规则 |
| --- | --- | --- |
| L1 短期记忆 | `SessionWorkingMemory` | 每轮对话更新，用于当前会话连续性 |
| L2 全局认知 | `USER.md` / `SOUL.md` / `Research_state.md` 或等价配置 | 仅在用户显式要求记住稳定偏好、身份、长期研究状态时写入 |
| L3 长期记忆 | daily memory / project memory recall index | compaction 前增量 flush、session close 补偿 flush、用户显式研究沉淀 |

`SessionWorkingMemory` 至少包含：

- `rollingSummary`
- `salientFacts`
- `currentTask`
- `compressedRounds`
- `lastDepositedTurnId`
- `lastDepositAt`

## API 合同

API 路径以 `/api/projects/:projectId` 为主轴。旧接口只有在能复用测试或局部逻辑时才作为参考，不作为兼容约束。

### 项目与会话

| 方法 | 路径 | 用途 | 返回 |
| --- | --- | --- | --- |
| `GET` | `/api/projects` | 列出项目 | `Project[]` |
| `POST` | `/api/projects` | 创建项目 | `Project` |
| `GET` | `/api/projects/:projectId` | 获取项目摘要 | `Project` |
| `GET` | `/api/projects/:projectId/sessions` | 列出会话 | `ResearchSession[]` |
| `POST` | `/api/projects/:projectId/sessions` | 创建会话 | `ResearchSession` |
| `GET` | `/api/projects/:projectId/sessions/:sessionId/messages` | 获取消息 | `ChatMessage[]` |
| `PATCH` | `/api/projects/:projectId/sessions/:sessionId` | 重命名或归档会话 | `ResearchSession` |

### 资料

| 方法 | 路径 | 用途 | 返回 |
| --- | --- | --- | --- |
| `POST` | `/api/projects/:projectId/sources` | 导入 PDF、网页或笔记 | `SourceDocument` |
| `GET` | `/api/projects/:projectId/sources` | 列出资料库 | `SourceDocument[]` |
| `GET` | `/api/projects/:projectId/sources/:sourceId` | 获取资料状态和分析摘要 | `SourceDocument` |
| `POST` | `/api/projects/:projectId/sources/:sourceId/retry` | 从失败阶段重试 | `SourceDocument` |

### 对话与流式回答

发送消息：

```http
POST /api/projects/:projectId/sessions/:sessionId/messages
```

请求体：

```json
{
  "question": "string",
  "sourceFilters": ["source-id"],
  "allowWebSupplement": true,
  "extractKnowledgeCandidates": true,
  "answerMode": "local_first"
}
```

返回：

```json
{
  "messageId": "msg_123",
  "answerId": "ans_123",
  "streamRunId": "run_123",
  "sseUrl": "/api/projects/prj_1/sessions/ses_1/runs/run_123/events"
}
```

SSE：

```http
GET /api/projects/:projectId/sessions/:sessionId/runs/:runId/events
```

### 证据、候选与知识板

| 方法 | 路径 | 用途 | 返回 |
| --- | --- | --- | --- |
| `GET` | `/api/projects/:projectId/answers/:answerId/evidence` | 获取回答证据 | `EvidenceSource[]` |
| `GET` | `/api/projects/:projectId/answers/:answerId/candidates` | 获取回答候选 | `KnowledgeCandidate[]` |
| `GET` | `/api/projects/:projectId/candidates` | 按项目或会话列候选 | `KnowledgeCandidate[]` |
| `POST` | `/api/projects/:projectId/candidates/:candidateId/accept` | 直接写入知识板 | `KnowledgeEntry` |
| `POST` | `/api/projects/:projectId/candidates/:candidateId/edit-and-accept` | 编辑后写入 | `KnowledgeEntry` |
| `POST` | `/api/projects/:projectId/candidates/:candidateId/mark-unverified` | 标为待验证 | `KnowledgeCandidate` |
| `POST` | `/api/projects/:projectId/candidates/:candidateId/ignore` | 忽略候选 | `KnowledgeCandidate` |
| `GET` | `/api/projects/:projectId/knowledge-board` | 获取知识板分区和条目 | `KnowledgeBoardSection[]` |
| `POST` | `/api/projects/:projectId/knowledge-board/entries` | 手动创建条目 | `KnowledgeEntry` |
| `PATCH` | `/api/projects/:projectId/knowledge-board/entries/:entryId` | 更新或移动条目 | `KnowledgeEntry` |
| `DELETE` | `/api/projects/:projectId/knowledge-board/entries/:entryId` | 归档条目 | `{ "archived": true }` |

### 反馈

```http
POST /api/projects/:projectId/answers/:answerId/feedback
```

请求体：

```json
{
  "rating": "up",
  "reason": "optional string",
  "evidenceSourceIds": ["ev_1", "ev_2"]
}
```

规则：

- `rating=up` 增加关联 chunk 的 `feedbackScore`。
- `rating=down` 降低关联 chunk 的 `feedbackScore`，并保留原因。
- 没有关联证据的反馈只记录到回答层，不得伪造 chunk 关联。

## Redis Stream 事件合同

Redis Stream 是后端过程事件骨干。若编码前决定不用 Redis Stream，必须先新增 ADR 明确替代机制及取舍。

### Stream 命名

| Stream | 用途 |
| --- | --- |
| `project:{projectId}:events` | 项目级资料、知识、反馈事件 |
| `run:{runId}:events` | 单次回答或 Agent 执行过程事件 |
| `source:{sourceId}:events` | 资料接入状态机事件 |

### 事件 Envelope

所有事件必须使用统一 envelope：

```json
{
  "eventId": "evt_123",
  "eventType": "agent.step.started",
  "projectId": "prj_1",
  "sessionId": "ses_1",
  "runId": "run_1",
  "sourceId": null,
  "answerId": null,
  "turnId": 12,
  "actor": "supervisor",
  "sequence": 42,
  "createdAt": "2026-05-09T12:00:00Z",
  "payload": {}
}
```

规则：

- `sequence` 在同一 `runId` 内单调递增。
- 事件一旦写入不得修改；需要修正时写入补偿事件。
- `payload` 不得包含未裁剪的大段原始论文全文。
- 前端只消费 SSE 投影，不直接读 Redis。

### 事件类型

| 类型 | 写入者 | 说明 |
| --- | --- | --- |
| `run.started` | API / Supervisor | 一次回答或任务开始 |
| `run.completed` | Supervisor | 任务结束 |
| `run.failed` | Supervisor | 任务失败 |
| `agent.plan.created` | Supervisor | 复杂任务规划完成 |
| `agent.step.started` | Supervisor / 子 Agent | 子步骤开始 |
| `agent.step.completed` | Supervisor / 子 Agent | 子步骤完成 |
| `retrieval.started` | Retrieval Agent | 检索开始 |
| `retrieval.completed` | Retrieval Agent | 检索完成，含 trace 摘要 |
| `evidence.evaluated` | Evidence Boundary | 证据充分性判定 |
| `answer.delta` | Writing Agent / Supervisor | 回答文本增量 |
| `answer.completed` | Supervisor | 回答完成，含模式和引用数 |
| `candidate.created` | Supervisor / Knowledge Agent | 知识候选生成 |
| `knowledge.entry.created` | Candidate API | 用户确认后写入知识板 |
| `source.status.changed` | Ingestion Worker | 资料状态推进 |
| `memory.flush.started` | Supervisor / Memory Worker | 长期记忆 flush 开始 |
| `memory.flush.completed` | Supervisor / Memory Worker | 长期记忆 flush 完成 |
| `feedback.applied` | Feedback Service | 反馈写入检索评分 |

## SSE 前端事件合同

SSE 是 Redis Stream 面向浏览器的投影。服务端负责鉴权、过滤、重放窗口和格式转换。

### SSE Envelope

```text
event: answer.delta
id: evt_123
data: {"runId":"run_1","sequence":42,"payload":{"text":"..."}}
```

前端必须按 `id` 去重，并按 `sequence` 合并同一 run 内的状态。

### 前端消费事件

| SSE event | 前端作用 |
| --- | --- |
| `run.started` | 标记当前回答进入 streaming |
| `agent.plan.created` | 在过程时间线显示计划摘要 |
| `agent.step.started` | 显示 Agent 当前步骤 |
| `agent.step.completed` | 折叠或标记步骤完成 |
| `retrieval.completed` | 更新证据计数和检索 trace 摘要 |
| `evidence.evaluated` | 显示 `本地证据强`、`证据偏弱`、`联网补充` 或 `拒答` |
| `answer.delta` | 追加回答文本 |
| `answer.completed` | 结束 streaming，解锁回答操作 |
| `candidate.created` | 右侧栏增加候选 |
| `source.status.changed` | 更新左侧资料状态 |
| `knowledge.entry.created` | 更新知识板分区计数和条目 |
| `run.failed` | 显示可恢复失败状态 |

## Agent 写入边界

| 组件 | 可写入 | 不可写入 |
| --- | --- | --- |
| `Supervisor Agent` | `run` 事件、规划事件、回答元数据、L1 工作记忆、候选生成请求 | 直接写入已确认知识板 |
| `Research Agent` | 分析步骤事件、草稿结论、候选草稿 | 项目知识板、L2 全局认知 |
| `Retrieval Agent` | retrieval trace、证据引用、检索事件 | 用户确认知识、长期记忆 |
| `Writing Agent` | `answer.delta`、回答草稿、结构化输出 | 证据评分、知识板 |
| `Evidence Boundary` | `evidence.evaluated`、输出模式 | 改写原始证据内容 |
| `Ingestion Worker` | 资料状态、chunk、索引、资料抽取候选 | 会话回答、知识板确认条目 |
| `Memory Worker` | L1 更新、L3 增量 flush 候选、watermark | 自动把候选变成 confirmed knowledge |
| `Feedback Service` | answer feedback、chunk `feedbackScore` | 删除或篡改历史证据 |
| `Candidate API` | 用户确认后的 `KnowledgeEntry` | 绕过用户确认创建知识条目 |

写入原则：

- Agent 可以生成候选，不能替用户确认知识。
- 联网补充必须标记来源，不得伪装成本地论文证据。
- L3 记忆可以服务召回上下文，但不得直接替代当前回答证据边界。
- 反馈影响后续检索优先级，不 retroactively 修改已展示回答。

## 前端状态模型

### 服务端状态

- `projects`
- `activeProject`
- `sessions`
- `activeSession`
- `sourceLibrary`
- `messages`
- `assistantAnswers`
- `evidenceSources`
- `knowledgeCandidates`
- `knowledgeBoardSections`
- `streamRuns`

### 本地 UI 状态

- `selectedSidebarView`: `knowledge_board` / `evidence_sources` / `candidate_confirmation`
- `collapsedBoardSections`
- `activeAnswerContext`
- `editingCandidateId`
- `composerDraft`
- `allowWebSupplement`
- `extractKnowledgeCandidates`
- `streamConnectionState`

本地状态可以先存在浏览器。只有当跨设备偏好同步成为明确需求时，才增加偏好 API。

### 三栏联动规则

- 选择左侧 session：中心消息和右侧上下文一起切换。
- 点击回答的 `查看引用`：右侧切到 `证据来源`，并绑定当前 `answerId`。
- 点击回答的 `查看候选`：右侧切到 `候选确认`，并定位当前回答候选。
- 确认候选：右侧知识板对应分区增加条目，候选状态变为 `accepted` 或 `edited_accepted`。
- 资料状态变化：左侧资料库实时更新；失败时展示具体阶段。

## 错误与恢复

- API 错误返回稳定 `code`、`message`、`retryable`、`details`。
- SSE 断线后前端用 `Last-Event-ID` 恢复同一 run 的后续事件。
- 资料接入失败必须允许从失败阶段重试。
- Agent run 失败必须保留已经完成的过程事件，方便调试和 Evidence。
- 如果联网补充失败，系统应回退到本地证据模式或明确拒答，不得静默编造。

## 实施切片建议

1. 数据模型与迁移：项目、会话、资料、消息、回答、证据、候选、知识板、反馈。
2. Redis Stream 与 SSE 投影：事件 envelope、run/source/project stream、断线恢复。
3. 资料接入状态机：PDF/web/note 状态推进、失败阶段、重试。
4. Agent 编排合同：Supervisor plan-execute、子 Agent 步骤事件、回答 delta。
5. 检索与证据边界：L3 recall、Paper RAG、Web Supplement、evidence state。
6. 候选确认与知识板：候选生成、编辑确认、知识板分区。
7. 反馈闭环：answer feedback 到 chunk feedbackScore。
8. 三栏工作台 UI：左资料/会话、中对话、右知识/证据/候选，并用浏览器验证。

## 验收证据

F002 进入“实现完成”前至少需要以下证据：

- 数据模型证据：迁移或 schema 检查通过，能创建项目、会话、资料、回答、证据、候选、知识条目。
- API 证据：项目、会话、资料、对话、证据、候选、知识板、反馈的接口测试通过。
- 事件证据：一次复杂回答产生 Redis Stream 事件，并能投影为 SSE；保留事件样例。
- SSE 证据：浏览器断线重连后能从 `Last-Event-ID` 继续消费。
- 资料状态机证据：成功链路和至少一个失败重试链路被测试覆盖。
- 证据边界证据：`SUFFICIENT`、`WEAK`、`NONE` 三类路径有测试或手工 trace。
- 知识写入证据：候选不会自动进入知识板；确认后才生成 `KnowledgeEntry`。
- 反馈证据：点赞/点踩会更新关联 chunk 的 `feedbackScore`，无证据关联时只记录回答反馈。
- UI 证据：三栏工作台在桌面和移动/窄屏降级下无明显遮挡，关键流程有截图或浏览器验证记录。
- Harness 证据：更新 Feature 链接，运行 `python scripts/knowledge_check.py`，并把关键验证结果写入 Evidence。

## Harness Links

- Feature：[F002-next-generation-research-workbench.md](../features/F002-next-generation-research-workbench.md)
- ADR：[ADR-002-rebuild-around-project-workbench.md](../decisions/ADR-002-rebuild-around-project-workbench.md)
- 产品 UI 规格：[research-workbench-ui-interaction-spec.md](../product/research-workbench-ui-interaction-spec.md)
- 架构护栏：[智能研究助手Agent-实现对齐与追问护栏-V2.md](../project-goal-alig/智能研究助手Agent-实现对齐与追问护栏-V2.md)
