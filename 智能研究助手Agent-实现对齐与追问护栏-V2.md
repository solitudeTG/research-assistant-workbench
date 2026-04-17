# 智能研究助手Agent：实现对齐与追问护栏 V2

## 1. 文档目标

这份文档不是对外主材料，而是 `V2` 版本的内部校准文档。它的作用有两个：

- 把这轮已经确认的项目口径固定下来
- 给后面的对外材料提供“能讲什么、怎么讲更稳、哪些地方要控制边界”的统一依据

这次 `V2` 的核心原则很明确：

- 保留 `Supervisor + 多Agent` 的亮点
- 保留 `plan-execute + ReAct` 的主线
- 保留 `L1/L2/L3 + 双检索` 的上下文工程表达
- 保留三层记忆的清晰分层，并把长期记忆收敛成事件触发式增量沉淀
- 不把项目讲成空想平台，也不把项目降级成普通 service 编排

## 2. V2 的四个定稿结论

### 2.1 `Supervisor + 多Agent` 保留，不降级

V2 不再采用“把多Agent收缩成 planner-first 的轻量中心化编排 + 专用执行单元”这种对外表述。

V2 的正式口径是：

`系统采用 Supervisor Agent 主导的自治多Agent协作架构。Supervisor 持有全局上下文并负责任务编排、路径选择、上下文裁剪与结果汇总；复杂任务下，多个专用子Agent在职责域内具备局部自治决策和工具调用能力。`

这里的关键不是取消中心化，而是重新定义“自治”的边界：

- `自治` 指子Agent在职责域内能自主完成推理、工具调用、观察迭代与局部修正
- `不等于` 完全去中心化、无中心协商的自由 Agent 网络

这套说法既保住亮点，也更抗追问。

### 2.2 `plan-execute` 保留，并与 `ReAct` 分层

V2 继续保留：

- 简单任务：`Supervisor` 直接处理
- 复杂任务：`Supervisor` 在内部进入 `plan-execute`
- 执行阶段：子Agent以 `ReAct` 为主要执行范式

并且在这次收敛后，`planning` 不再作为独立 Agent 角色对外出现，而是统一收回 `Supervisor` 的内部能力。

V2 对这两者的边界定义为：

- `plan-execute` 是 Supervisor 层的规划与调度机制
- `ReAct` 是子Agent执行层的推理与工具调用机制

也就是说：

`plan-execute` 解决的是“复杂任务怎么拆、怎么排、怎么收敛”，  
`ReAct` 解决的是“拿到一个子任务后，Agent 怎么在职责域内完成执行”。

### 2.3 `L2` 视为已实现

V2 按你的最终要求处理：

- `L1`：短期记忆
- `L2`：全局认知，包含 `USER.md / SOUL.md / Research_state.md`
- `L3`：长期记忆，核心载体为 `每日记忆.md`
- `检索补全层`：由 `L3 Memory Recall / Paper RAG / Web Supplement` 共同构成

因此，V2 不再采用“L2 主要以 `Research_state` 为锚点、`USER/SOUL` 只是接口预留”的说法。

V2 的正式表述就是：

`系统采用 L1/L2/L3 三层记忆，其中 L2 全局认知由 USER.md、SOUL.md 和 Research_state.md 共同构成。`

### 2.4 长期记忆采用事件触发式增量沉淀

V2 的正式记忆口径不再把“回答级门控直写 L3”作为主叙事，而是统一收敛成：

- 用户显式要求记住时，按内容类型写入 `L2` 或 `L3`
- 会话压缩前，由 `Supervisor` 对 `turnId > lastDepositedTurnId` 的新增对话区间做一次增量抽取
- 会话关闭时，只在尚未成功 flush 时做补偿式写入
- 写入 `L3` 前对最近 daily memory 做轻量 merge，避免重复沉淀

这套设计的关键不是“每轮回答后要不要落盘”，而是：

- `L1` 只承担当前会话连续性
- `L3` 只承担长期研究沉淀
- 长期记忆写入必须由关键生命周期事件触发
- 长期沉淀必须是增量式，而不是全量重扫

## 3. V2 的统一架构主线

整套 V2 材料统一采用下面这条主线：

- `架构层`：Supervisor Agent 主导的自治多Agent协作架构
- `策略层`：简单任务直解，复杂任务进入 `plan-execute`
- `执行层`：子Agent 在职责域内以 `ReAct` 为主要执行范式
- `记忆层`：`L1 短期记忆 / L2 全局认知 / L3 长期记忆`
- `检索层`：`L3 Memory Recall / Paper RAG / Web Supplement` 共同组成检索补全层
- `可靠性层`：证据边界、拒答/降级、状态机、benchmark、联网补充、反馈驱动检索优化

在检索层上，V2 新增一条明确口径：

- `L3` 不直接并入论文 `RAG`
- `L3` 已形成独立的 `semantic/hybrid memory recall`
- 由 `Supervisor` 决定走 `No Retrieval / Memory Recall Only / Paper RAG Only / Memory + Paper`

## 4. 10 个增强点在 V2 里的最终定位

### 4.1 显式联网补充模式

保留，且作为可靠性与知识边界的重要亮点。

对外表述：

`系统采用本地证据优先、联网补充按需触发的策略，并显式区分本地证据与外部来源。`

### 4.2 planner-first 决策链

保留，且作为 `Supervisor` 内部决策能力的重要证明。

可以讲到明确意图层：

- `CONTEXT_RECALL`
- `CONTEXT_FOLLOW_UP`
- `LOCAL_RESEARCH`
- `LOCAL_THEN_WEB`
- `WEB_KNOWLEDGE`
- `WRITING`

### 4.3 structured working memory

保留，并作为 `L1` 的关键实现点。

推荐明确讲到字段：

- `rollingSummary`
- `salientFacts`
- `currentTask`
- `compressedRounds`
- `lastDepositedTurnId`
- `lastDepositAt`

### 4.4 事件触发式长期记忆沉淀

保留，并且在 V2 里提升为正式主线。

因为它能很好地回答：

- 长期记忆什么时候写入
- 会话压缩前怎么沉淀长期记忆
- 为什么不会重复写入同一批内容
- 你对长期上下文治理有没有工程意识

### 4.5 证据边界与拒答/降级机制

保留，而且是核心亮点之一。

推荐直接讲到枚举：

- `SUFFICIENT / WEAK / NONE`
- `LOCAL_EVIDENCE / LOCAL_WEAK_EVIDENCE / WEB_SUPPLEMENT / REFUSAL`

### 4.6 RAG 质量优化

保留，作为 AI 系统设计能力的重要支撑点。

推荐保持这组表达：

- overlap-aware chunking
- intent-aware query planning
- hybrid retrieval
- explainable lightweight rerank
- retrieval trace

### 4.7 `L3 memory recall`

保留，并作为正式能力纳入 V2 口径。

推荐固定讲法：

- `L3` 和记忆召回、论文证据检索分层
- `L3 recall` 负责历史研究上下文
- `paper RAG` 负责当前回答证据
- 双检索默认 `Memory First -> Paper Second`

### 4.8 benchmark 快照

保留，作为“不是只靠感觉优化”的证据。

推荐固定数字：

- 样本数：`10`
- `Top1 hit rate: 0.70 -> 0.90`
- `Multilingual hit rate: 0.00 -> 1.00`

### 4.9 feedback 驱动检索优化

保留。

更稳的讲法是：

`feedback 已经形成可观察闭环，其中最明确的落点是用户的点赞点踩会回流到证据 chunk 的 feedbackScore，并在后续检索与重排时改变优先级。`

### 4.10 联网补充与反馈的边界

保留。

推荐固定讲法：

- 联网补充只在本地证据不足时触发
- 外部来源与论文证据严格分层展示
- feedback 直接作用于检索层
- 点赞点踩会更新关联 chunk 的 `feedbackScore`

### 4.11 上传状态机

保留，作为后端工程化亮点。

推荐固定讲法：

`系统把论文接入工程化成显式状态机：UPLOADED -> PARSING -> INDEXING -> INDEXED / FAILED，并保留 failureStage 与 parseError 来区分解析失败和索引失败。`

## 5. V2 面试时最重要的边界控制

### 5.1 关于“自治”

要讲：

- 子Agent在职责域内具备自治执行能力

不要讲：

- Agent 之间已经形成完全去中心化自治网络

### 5.2 关于“多Agent”

要讲：

- 多Agent 是复杂任务下的核心能力
- 不是装饰性概念

不要讲：

- 所有任务都默认进入多Agent协作

### 5.3 关于 `plan-execute`

要讲：

- 是复杂任务的规划与执行分层机制

不要讲：

- 已经做成通用重型任务图规划平台

### 5.4 关于长期记忆

要讲：

- 当前长期记忆采用事件触发式增量沉淀
- `lastDepositedTurnId` 控制增量边界
- 写入 daily memory 前会做轻量 merge

不要讲：

- 当前已经做成持续后台异步的高级记忆系统
- 当前会把每轮回答自动完整落盘到长期记忆

这部分的关键不是把长期记忆讲成复杂，而是讲成清楚：什么时候写、谁来写、怎么避免重复写。

### 5.5 关于 `L3 semantic/hybrid memory recall`

要讲：

- `L3 recall` 与论文 `RAG` 解耦
- `L3 recall` 提供历史研究上下文
- `paper RAG` 提供当前回答证据
- 双检索默认 `Memory First -> Paper Second`

不要讲：

- 当前已经把 `L3` 无差别并入论文证据库
- 当前双检索默认每次并行触发
- 历史记忆已经直接参与 `Evidence Boundary` 的主判断

## 6. V2 对外材料的改写目标

基于这份护栏，V2 的 3 份对外材料要同时达到下面三个目标：

- 亮点足够强
- 口径足够稳
- 被追问时能自然落回代码实现

这意味着：

- `秋招主线版 V2` 要强调完整项目叙事和技术亮点
- `简历与讲稿版 V2` 要强调对外表达效率
- `高频追问题库 V2` 要强调追问时的抗压能力

## 7. 最终一句话结论

V2 的核心判断可以压成一句话：

`这个项目可以继续保留 Supervisor + 多Agent、plan-execute、L1/L2/L3、双检索、证据边界、联网补充、feedback 驱动检索优化这些秋招亮点；但所有亮点都要落回“有真实代码支撑的工程化实现”，而不是空泛的平台想象。`
