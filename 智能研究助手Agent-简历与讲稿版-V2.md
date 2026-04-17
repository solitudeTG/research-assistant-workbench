# 智能研究助手Agent：简历与讲稿版 V2

## 1. 这份材料怎么用

这份材料服务于三个场景：

- 简历项目描述
- 1 分钟项目开场
- 3 分钟技术面项目讲述

整份材料统一采用 `V2` 口径：

- `Supervisor Agent + 多Agent`
- 简单任务直解，复杂任务由 `Supervisor` 在内部进入 `plan-execute`
- 子Agent 在职责域内以 `ReAct` 为主要执行范式
- `L1/L2/L3 + 双检索`
- 证据边界、拒答/降级、状态机、offline benchmark snapshot、反馈驱动检索优化

## 2. 项目一句话版本

`我设计并实现了一个面向科研场景的智能研究助手系统，它采用 Supervisor Agent 主导的自治多Agent协作架构，结合分层记忆、混合检索、证据约束与反馈驱动检索优化，支持研究问答、资料分析、结构化写作与研究路线辅助等复杂任务。`

## 3. 简历项目描述

### 3.1 超短版

`设计并实现面向科研场景的智能研究助手Agent系统，采用 Supervisor Agent 主导的多Agent协作架构，结合 L1/L2/L3 分层记忆、双检索体系、证据边界控制与反馈驱动检索优化，支持研究问答与结构化写作。`

### 3.2 标准版

`负责设计并实现面向科研场景的智能研究助手Agent系统，围绕论文解析、研究问答、资料分析、结构化写作与研究路线辅助构建完整任务链路。系统采用 Supervisor Agent 主导的自治多Agent协作架构，简单任务由 Supervisor 直接处理，复杂任务由 Supervisor 在内部进入 plan-execute，并调度多个专用子Agent以 ReAct 方式执行。通过 L1/L2/L3 分层记忆、L3 Memory Recall + Paper RAG + Web Supplement 组成的检索补全层、证据边界控制、上传状态机、offline benchmark snapshot 与 feedback-driven retrieval optimization，提升复杂研究任务中的上下文连续性、输出可信度与长期复用能力。`

### 3.3 项目 bullet 版

- 设计 `Supervisor Agent + 多Agent` 协作架构，简单任务直解，复杂任务由 Supervisor 在内部进入 `plan-execute` 并调度专用子Agent执行。
- 构建 `L1 短期记忆 / L2 全局认知 / L3 长期记忆` 的分层记忆体系，并设计“显式记忆 + compaction 前增量 flush + session close 补偿 flush”的长期沉淀机制，以 `L3 Memory Recall + Paper RAG + Web Supplement` 组成按需触发的检索补全层增强当前任务上下文。
- 实现面向论文场景的混合检索、证据边界、结构化写作、拒答/降级与上传状态机，支持研究问答、资料分析与报告生成。
- 引入 retrieval trace、offline benchmark snapshot、联网补充与 feedback-driven retrieval optimization，增强系统可解释性、可评测性与长期连续性。

## 4. 简历里最值钱的关键词

建议优先保留这些词：

- `Supervisor + 多Agent`
- `plan-execute`
- `ReAct`
- `L1/L2/L3`
- `Paper RAG`
- `证据边界`
- `状态机`
- `benchmark`
- `feedback-driven retrieval optimization`

## 5. 1分钟开场稿

`我做的是一个面向科研场景的智能研究助手系统，不是普通聊天机器人，也不只是论文问答 Demo。它的目标是让系统能够持续参与研究过程，比如论文解析、研究问答、资料对比、结构化写作和研究路线辅助，而不是只回答一次问题。`

`在架构上，我采用的是 Supervisor Agent 主导的自治多Agent协作模式。简单任务由 Supervisor 直接完成，复杂任务由 Supervisor 在内部进入 plan-execute，再把检索、分析、写作等子任务交给多个专用子Agent在职责域内以 ReAct 方式执行。为了支撑长期连续性，我设计了 L1/L2/L3 分层记忆，并把 L3 Memory Recall、Paper RAG 和 Web Supplement 组成检索补全层。除此之外，我还重点做了证据边界、拒答/降级、上传状态机、offline benchmark snapshot、联网补充和反馈驱动检索优化，让它更像一个真实系统，而不是简单的 Agent Demo。`

## 6. 3分钟技术面讲稿

`我的项目是一个面向科研场景的智能研究助手系统，主要解决的问题不是单轮问答，而是研究过程中的持续上下文管理、证据可追溯、结构化写作、长期记忆沉淀和反馈闭环。普通大模型聊天产品在这些维度上往往比较弱，所以我把它做成了一个任务系统，而不是单纯的对话入口。`

`整个系统的核心架构是 Supervisor Agent 主导的自治多Agent协作。Supervisor 负责持有全局上下文、判断任务复杂度、选择执行路径、在内部完成 planning 与 plan-execute、裁剪上下文并汇总结果。对于简单任务，比如基于当前会话的追问、单篇论文理解和短链路问答，由 Supervisor 直接处理；对于复杂任务，比如多资料对比、研究路线规划、结构化报告生成，Supervisor 会在内部完成任务拆解与执行排序，再把检索、分析、写作等子任务交给多个专用子Agent执行。子Agent 在各自职责域内以 ReAct 作为主要执行范式，通过推理、工具调用和观察迭代完成任务。`

`在上下文工程上，我采用了 L1/L2/L3 分层记忆。L1 是短期记忆，维护当前会话中的任务状态、近期事实和结构化 working memory；L2 是全局认知，由 USER.md、SOUL.md 和 Research_state.md 构成，负责保存用户偏好、系统行为约束和研究阶段状态；L3 是长期记忆，通过每日记忆.md 沉淀研究轨迹、阶段结论和可复用经验。这里我没有把长期记忆做成“回答后直接落盘”的模式，而是设计成事件触发式沉淀：用户显式要求记住时可直接写入，临近会话压缩时由 Supervisor 对新增对话区间做增量抽取写入 L3，会话关闭时再做补偿式 flush，并通过 lastDepositedTurnId 和轻量 merge 避免重复沉淀。检索补全层我单独拆成 L3 Memory Recall、Paper RAG 和 Web Supplement，其中前者负责历史研究上下文，后两者负责当前任务证据与外部补充。`

`在检索层上，我还把 L3 的长期记忆检索和论文 RAG 明确解耦。系统不会把每日记忆直接并入论文证据库，而是为 L3 提供一条独立的 semantic/hybrid memory recall 链路。Supervisor 会根据任务类型在 No Retrieval、Memory Recall Only、Paper RAG Only、Memory + Paper 四种模式中做路由；如果两者都需要，默认先做 Memory Recall，再用历史研究上下文去约束论文检索。这样能同时保住长期连续性和证据边界。`

`在可靠性上，我重点做了三件事。第一是证据边界控制，系统会显式区分证据的充分程度，并根据情况选择 grounded answer、保守回答、联网补充或 refusal；第二是上传与处理状态机，把论文接入工程化成 UPLOADED、PARSING、INDEXING、INDEXED、FAILED 这些状态，方便定位解析和索引阶段的问题；第三是 offline benchmark snapshot 与反馈驱动检索优化，通过 retrieval trace、小规模固定样本快照以及论文证据 chunk feedbackScore 的反馈联动，让系统具备可评测性和长期优化能力。`

`我认为这个项目最大的价值在于，它把 Agent、RAG、分层记忆、证据边界和工程治理真正串成了一套完整系统，这也是它最适合秋招项目表达的地方。`

## 7. 讲述时最该主动打的点

### 7.1 `Supervisor + 多Agent`

强调这是复杂任务下的能力设计，不是装饰性概念。

### 7.2 `plan-execute + ReAct`

强调规划层和执行层的职责分离。

### 7.3 `L1/L2/L3 + 双检索`

强调长期上下文保留与当前任务证据召回的分工。

### 7.4 证据边界与状态机

强调项目不是“能跑起来”，而是“可控、可追踪、可降级”。

### 7.5 offline benchmark snapshot、联网补充与反馈驱动检索优化

强调项目具备优化闭环，而不是纯概念展示。

## 8. 不要这样讲

- 不要把项目讲成“纯框架堆砌”
- 不要把 `plan-execute` 和 `ReAct` 说成一个层次
- 不要把 `RAG` 和 `记忆系统` 混成一件事
- 不要把“自治”讲成完全去中心化自由协商网络
- 不要把长期记忆讲成已经做成高级 learned memory system

## 9. 最终自检清单

面试前至少确认自己能稳定讲清：

- 为什么这个项目不是普通聊天机器人
- 为什么是 `Supervisor + 多Agent`
- 为什么简单任务不默认多Agent
- `plan-execute` 和 `ReAct` 的边界
- `L1/L2/L3` 分别存什么
- 为什么检索补全层要拆成 `L3 Memory Recall / Paper RAG / Web Supplement`
- 长期记忆为什么采用事件触发式增量沉淀
- 证据边界和拒答/降级怎么设计
- benchmark、联网补充和 feedback 怎么支持后续优化
