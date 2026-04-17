# 智能研究助手Agent：秋招主线版 V2

## 1. 项目定位

这个项目不是普通聊天机器人，也不是单纯的论文问答 Demo，而是一个面向科研场景的智能研究助手系统。

它解决的核心问题不是“多回答一次问题”，而是让系统能够持续参与研究过程中的关键任务：

- 论文上传、解析、索引与基于论文的研究问答
- 基于证据的资料检索、分析与对比
- 周报、阅读笔记、阶段总结等结构化写作
- 基于长期研究状态的研究路线辅助
- 基于点赞点踩反馈的检索优化闭环

一句话介绍版本可以固定成：

`我设计并实现了一个面向科研场景的智能研究助手系统，它以 Supervisor Agent 主导的自治多Agent协作架构为核心，结合分层记忆、混合检索、证据约束与反馈驱动检索优化，支持研究问答、分析、写作、研究路线辅助等复杂任务。`

## 2. 为什么这个项目值得写进秋招简历

这个项目最大的价值不在于“用了 Agent”，而在于它把下面几类能力真正串成了一套系统：

- 后端工程能力
- AI 应用架构能力
- 上下文工程与记忆设计能力
- RAG 与证据约束能力
- 可靠性与状态机设计能力

从面试视角看，它天然覆盖这些高频问题：

- 项目深挖：你到底解决了什么真实问题
- Agent 架构：为什么要 `Supervisor + 多Agent`
- 任务编排：为什么简单任务不默认多Agent
- 记忆系统：`L1/L2/L3` 分别做什么
- RAG：如何做质量优化和证据边界控制
- 可靠性：如何处理弱证据、拒答、降级、失败恢复
- 反馈闭环：点赞点踩为什么会影响后续检索效果

## 3. 项目核心亮点

### 亮点一：Supervisor Agent 主导的自治多Agent协作架构

系统采用 `Supervisor Agent + 多个专用子Agent` 的协作架构。

Supervisor 的职责是：

- 持有全局上下文
- 判断任务复杂度
- 选择执行路径
- 裁剪上下文
- 汇总结果
- 施加证据约束

复杂任务下，多个专用子Agent在各自职责域内以局部自治方式完成执行。这里的自治不是指完全去中心化协商网络，而是指：

- 子Agent能够在职责域内自主完成推理
- 自主调用工具
- 根据观察结果局部修正执行过程

这使系统既保留了多Agent的亮点，也保留了工程上的可控性。

### 亮点二：简单任务直解，复杂任务进入 `plan-execute`

我没有把所有请求都强行包装成多Agent协作，而是做了分层决策：

- 简单任务：Supervisor 直接完成
- 复杂任务：Supervisor 在内部进入 `plan-execute`

这样做的收益是：

- 避免简单任务被不必要地拆分
- 控制延迟和系统复杂度
- 把多Agent真正用在复杂任务上

### 亮点三：子Agent以 `ReAct` 为主要执行范式

复杂任务下，子Agent以 `ReAct` 为主要执行方式，在职责域内完成：

- 推理
- 工具调用
- 观察结果
- 局部修正

这条主线非常适合技术面试讲清：

- `plan-execute` 在 Supervisor 层
- `ReAct` 在子Agent执行层

前者负责“怎么拆、怎么排、怎么收敛”，后者负责“子任务怎么做”。

### 亮点四：`L1/L2/L3 + 双检索` 的上下文工程

系统采用分层记忆与检索补全相结合的上下文工程方案：

- `L1 短期记忆`
- `L2 全局认知：USER.md / SOUL.md / Research_state.md`
- `L3 长期记忆：每日记忆.md`
- `L3 Memory Recall / Paper RAG / Web Supplement` 作为按需触发的检索补全层

这套设计的核心价值是：

- 短期上下文连续
- 全局认知稳定
- 长期研究轨迹可沉淀
- 当前任务证据可按需召回

并且，`L3` 不是回答后就直接按规则落盘，而是采用事件触发式长期沉淀：

- 用户显式要求记忆时按内容类型写入 `L2` 或 `L3`
- 会话压缩前由 `Supervisor` 对新增对话区间做增量抽取并写入 `L3`
- 会话关闭时只做补偿式 flush
- 通过 `lastDepositedTurnId + 轻量 merge` 避免重复沉淀

### 亮点五：证据边界、拒答/降级与状态机

系统不是只追求“能答”，而是优先保证“答得住”。

在研究问答链路中，系统显式区分证据充分性：

- `SUFFICIENT`
- `WEAK`
- `NONE`

再联动不同输出模式：

- `LOCAL_EVIDENCE`
- `LOCAL_WEAK_EVIDENCE`
- `WEB_SUPPLEMENT`
- `REFUSAL`

在上传与接入链路中，系统又通过显式状态机管理：

`UPLOADED -> PARSING -> INDEXING -> INDEXED / FAILED`

这样项目会比普通 AI Demo 更像一个可上线、可调试、可治理的系统。

### 亮点六：Paper RAG 质量优化与离线 benchmark snapshot

项目中的 RAG 不是只接一个向量库，而是做了实际可讲的质量优化：

- overlap-aware chunking
- intent-aware query planning
- hybrid retrieval
- explainable lightweight rerank
- retrieval trace
- offline benchmark snapshot

当前 benchmark 更适合作为一个小规模离线 snapshot 来讲，用来验证优化方向是否有效，而不是充当完整泛化评测。代表性结果可以讲：

- 样本数：`10`
- `Top1 hit rate: 0.70 -> 0.90`
- `Multilingual hit rate: 0.00 -> 1.00`

并且，`L3` 不会直接并入论文 `RAG`，而是采用一条独立的 `semantic/hybrid memory recall` 链路，由 `Supervisor` 统一路由到：

- `No Retrieval`
- `Memory Recall Only`
- `Paper RAG Only`
- `Memory + Paper`

默认双检索顺序采用：

`Memory First -> Paper Second`

也就是先用历史研究记忆补足任务上下文，再进入论文证据检索。

### 亮点七：反馈驱动的检索优化闭环

系统不是做完一次任务就结束，而是把点赞点踩反馈直接回流到检索层。

当前更稳的设计是：

- 用户对回答进行点赞 / 点踩
- 系统把反馈映射到关联论文证据 `chunk` 的 `feedbackScore`
- 后续召回和重排时，把这个分数作为额外排序信号
- 高质量、被用户认可的证据片段在后续类似任务中优先级更高

这让系统具备了长期自适应能力，而且这个优化方向和主链路天然一致，因为它直接作用在检索质量上。

## 4. 系统整体架构

系统可以抽象成六层：

### 4.1 接入层

- Web 前端
- REST API
- SSE 流式输出
- 论文上传接口

### 4.2 编排层

- `Supervisor`
- 会话状态管理
- 任务识别与路由
- 内部规划与 `plan-execute`
- 上下文裁剪
- 结果汇总

### 4.3 Agent 执行层

- `Research Agent`
- `Retrieval Agent`
- `Writing Agent`

### 4.4 工具与服务层

- 解析
- 切块
- 检索
- 重排
- 记忆读写
- 输出审查
- 联网补充
- feedback 驱动的论文证据 chunk score 更新

### 4.5 记忆与检索层

- `L1 短期记忆`
- `L2 全局认知`
- `L3 长期记忆`
- `L3 Memory Recall`
- `Paper RAG`
- `Web Supplement`

### 4.6 可靠性与运行时层

- 证据边界
- 拒答与降级
- 上传状态机
- benchmark
- feedback 驱动检索优化

## 5. 核心请求链路

### 5.1 简单研究问答链路

链路大致是：

1. 用户发起请求
2. Supervisor 读取 L1/L2 上下文
3. Supervisor 在内部完成路径判断，决定是否需要本地检索
4. 必要时触发 RAG 补证据
5. Supervisor 直接生成回答
6. 写回 L1，必要时更新 working memory 与反馈闭环

### 5.2 复杂任务链路

链路大致是：

1. Supervisor 判定任务复杂
2. 进入 `plan-execute`
3. 拆成检索、分析、写作等子任务
4. 分配给多个专用子Agent
5. 子Agent 以 `ReAct` 方式执行
6. Supervisor 汇总、审查、收敛输出
7. 更新记忆与反馈闭环

### 5.3 报告/周报生成链路

重点不是“一次性生成一段话”，而是：

- 聚合长期研究记忆
- 聚合研究状态和历史材料
- 必要时触发补检索
- 通过 Writing Agent 生成结构化输出

### 5.4 反馈驱动检索优化链路

链路大致是：

1. 系统基于当前回答绑定证据 `chunk`
2. 用户进行点赞 / 点踩
3. 反馈服务更新关联论文证据 `chunk` 的 `feedbackScore`
4. 后续检索与重排将该分数作为额外排序信号
5. 用户认可过的高质量证据在相似任务中更容易排前

## 6. Agent 编排与执行机制

### 6.1 为什么要 `Supervisor`

因为多Agent系统最容易失控的地方恰恰是：

- 上下文重复加载
- 角色边界模糊
- token 成本失控
- 结果汇总不一致

Supervisor 的价值就在于：

- 统一持有全局上下文
- 统一控制执行路径
- 统一裁剪任务包
- 统一做结果汇总与证据约束

### 6.2 为什么简单任务不默认多Agent

因为多Agent不是目的，复杂任务完成质量和成本控制才是目的。

简单任务强行多Agent会带来：

- 不必要的上下文拆分
- 调度开销
- 延迟增加
- 失败点增多

所以系统采用：

- 简单任务：Supervisor 直接解
- 复杂任务：多Agent协作

### 6.3 `plan-execute` 和 `ReAct` 的分层

这部分是秋招里非常值得主动讲的点：

- `plan-execute`：在 Supervisor 层负责规划和调度
- `ReAct`：在子Agent层负责执行与工具调用

一句话压缩版：

`plan-execute 决定怎么拆和怎么排，ReAct 决定拿到子任务后怎么做。`

## 7. 记忆系统与上下文工程

### 7.1 `L1 短期记忆`

L1 负责维护当前会话和当前任务状态，关键内容包括：

- recent turns
- `rollingSummary`
- `salientFacts`
- `currentTask`
- `compressedRounds`

### 7.2 `L2 全局认知`

L2 由三份全局认知文件组成：

- `USER.md`
- `SOUL.md`
- `Research_state.md`

它们分别解决：

- 用户偏好与背景
- 系统行为约束与表达边界
- 当前研究阶段与近期重点

### 7.3 `L3 长期记忆`

L3 主要通过 `每日记忆.md` 沉淀长期研究轨迹，但它存储的不是原始聊天记录，而是抽取后的高价值研究条目，记录内容包括：

- 当天研究进展
- 阅读记录
- 阶段性结论
- 待解决问题
- 后续待办

它的核心目标不是“备份聊天”，而是“把研究过程沉淀成长期可复用资产”。

### 7.4 为什么检索层不属于记忆层

因为：

- `Memory` 回答的是“系统应该长期保留什么”
- `Retrieval` 回答的是“当前任务需要按需召回什么历史上下文、论文证据或外部补充信息”

这两者职责不同，不能混讲。

### 7.5 长期记忆的写入机制

长期记忆不是在每轮回答后对全量上下文直接落盘，而是采用事件触发式沉淀：

1. 用户显式要求记住时，由 `Supervisor` 判断写入 `L2` 还是 `L3`
2. 会话压缩前，由 `Supervisor` 对 `turnId > lastDepositedTurnId` 的新增对话区间做一次增量抽取
3. 会话关闭时，如果当前会话尚未完成成功 flush，再做一次补偿式写入

这套机制的关键是：

- `L1` 只负责当前会话连续性
- `L3` 只存抽取后的高价值研究条目
- 通过 `lastDepositedTurnId` 控制增量沉淀边界
- 通过轻量 merge 避免把同一主题和同一结论重复写入 daily memory

这能很好回答面试官的一个高频问题：

`长期记忆怎么沉淀、又怎么避免重复和失控？`

## 8. 检索与 RAG 设计

### 8.1 目标

`Paper RAG` 的目标不是“找相似文本”，而是“为当前任务提供可引用、可追溯、可信度可判断的论文证据”。

### 8.2 `Paper RAG` 检索链路

核心链路可以概括为：

1. query understanding
2. metadata filtering
3. BM25 召回
4. 向量召回
5. 去重与融合
6. lightweight rerank

### 8.3 `L3 Memory Recall`

当前论文 `RAG` 负责外部证据检索，`L3` 负责长期研究沉淀。为了增强长期连续性，系统为 `L3` 提供一条独立的 `semantic/hybrid memory recall` 链路。

这条链路的定位不是“论文证据库”，而是“历史研究上下文库”：

- 论文 `RAG` 提供当前回答证据
- `L3 recall` 提供历史研究上下文

因此两者必须解耦，不能无差别混成同一个检索池。

### 8.4 为什么不把 `L3` 直接并入论文 `RAG`

因为如果把历史记忆和论文 chunk 放在同一个排序池中，会有两个问题：

1. 系统自己过去写下来的总结可能压过论文原文
2. 历史沉淀会被模型误当成当前外部证据

这会直接破坏项目中很重要的：

- `Evidence Boundary`
- grounded answer
- 引用与证据追溯

### 8.5 双检索路由设计

检索默认不应每次都同时查两个库，而是由 `Supervisor` 先判断任务属于哪一类：

- `No Retrieval`
- `Memory Recall Only`
- `Paper RAG Only`
- `Memory + Paper`

其中：

- `Memory Recall Only` 适合历史连续性问题
- `Paper RAG Only` 适合需要 grounded evidence 的论文问题
- `Memory + Paper` 适合“延续历史工作 + 结合外部证据”的复杂任务

双检索默认推荐顺序不是并行，而是：

`Memory First -> Paper Second`

也就是：

1. 先召回 `L3` 中的历史研究上下文
2. 再基于历史上下文改写 query 或缩小论文检索范围
3. 最后进入 `Paper RAG`

### 8.6 `Paper RAG` 质量优化

当前最值得讲的优化点是：

- overlap-aware chunking
- intent-aware query planning
- lightweight rerank
- retrieval trace
- offline benchmark snapshot

### 8.7 联网补充边界

系统不是默认联网，而是：

- 本地证据优先
- Supervisor 内部规划判断是否需要联网
- 用户开关决定是否允许联网
- 本地 evidence 与 web source 严格分层展示

联网链路的定位很清楚：

- `Paper RAG` 负责本地论文证据
- `Web Supplement` 负责外部补充信息
- 本地证据不足时才进入联网补充
- 联网结果不会伪装成论文证据，而是作为单独来源展示

## 9. 工程化与可靠性设计

### 9.1 上传状态机

上传链路的显式状态机是：

`UPLOADED -> PARSING -> INDEXING -> INDEXED / FAILED`

同时保留：

- `failureStage`
- `parseError`

这让错误可以被明确定位。

### 9.2 证据边界与降级

系统显式区分：

- `SUFFICIENT`
- `WEAK`
- `NONE`

并基于此决定：

- 本地 grounded answer
- 保守回答
- web supplement
- refusal

### 9.3 benchmark 与可观察性

项目不是靠感觉调优，而是已经形成：

- retrieval trace
- benchmark snapshot
- 明确的 before/after 对比

这会让你在面试里更容易回答“你怎么证明优化有效”。

## 10. 反馈驱动检索优化

这里我把它收敛成一条更扎实的主链能力：反馈驱动的检索优化。

当前流程是：

1. 回答生成后，系统保留本轮使用过的关键证据 `chunk`
2. 用户点赞 / 点踩
3. feedback 服务把反馈映射到对应论文证据 `chunk` 的 `feedbackScore`
4. 后续相似问题再次检索时，这个分数参与召回 / 重排
5. 被多次正反馈命中的 chunk 更容易排前，被负反馈命中的 chunk 优先级下降

这条链路的价值在于：

- 不偏离主线功能
- 直接作用于检索效果
- 能解释“为什么系统越用越贴合当前研究方向”
- 面试时也更容易和 RAG、chunk metadata、重排逻辑串起来讲

## 11. 秋招高频追问的标准主线

### 11.1 为什么是 `Supervisor + 多Agent`

因为复杂科研任务天然需要分工协作，但系统又必须有统一上下文控制和结果收敛能力。

### 11.2 为什么不是所有任务都多Agent

因为简单任务强行多Agent只会增加开销和复杂度，收益不明显。

### 11.3 `plan-execute` 和 `ReAct` 的关系

一个负责规划和调度，一个负责执行和工具调用。

### 11.4 `L1/L2/L3` 和检索层的关系

前者负责长期上下文保留，后者负责当前任务按需召回。

### 11.5 你怎么控制幻觉

通过混合检索、证据边界、保守回答、联网补充、拒答和降级来控制。

### 11.6 这个项目最难的点是什么

不是单个模块，而是如何把编排、记忆、检索、写作和可靠性约束串成一条真正可运行的科研任务链。

## 12. 一句话收尾

这个项目最能体现的，不是“我接过大模型接口”，而是：

`我把 Agent、RAG、分层记忆、证据边界、联网补充和反馈驱动检索优化真正组织成了一套能支撑复杂科研任务的系统。`
