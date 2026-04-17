# 智能研究助手 Phase 1 设计规格说明

## 1. 文档目的

本文将当前目录下已有的项目设计材料沉淀为可执行的正式规格，作为后续实施计划和代码开发的统一依据。

本规格聚焦 `Phase 1`，目标不是一次性做完整个秋招叙事里的全部能力，而是先落地一个**真实可运行**的研究助手主链路，同时为后续 `多 Agent / plan-execute / L2-L3 / feedback / benchmark` 留出清晰扩展缝。

本规格主要吸收以下材料中的稳定结论：

- `智能研究助手Agent-秋招主线版-V2.md`
- `智能研究助手Agent-实现对齐与追问护栏-V2.md`
- `智能研究助手Agent-架构图谱-V2.md`
- `智能研究助手Agent-记忆系统设计-V2.md`
- `智能研究助手Agent-L3语义记忆召回设计-V2.md`

同时参考 `D:\ai-research-assistant-ultimate-master` 的模块拆分经验，但不直接照搬其重型多模块与中间件组合。

## 2. 问题定义

项目最终目标是做成一个面向科研场景的可运行研究助手系统，而不是：

- 只用于面试表达的文档工程
- 只有单轮问答能力的聊天 Demo
- 架构上很完整但主链路不稳定的“半成品平台”

系统最终应支持的核心方向包括：

- 论文上传、解析、索引与基于论文的问答
- 研究资料分析与对比
- 结构化写作
- 长期研究上下文沉淀与召回
- 反馈驱动的检索优化

但从交付策略上，必须先把最关键、最可验证的主链路做通，再逐阶段补齐完整能力。

## 3. Phase 1 目标与边界

### 3.1 Phase 1 目标

Phase 1 交付一个可运行的研究助手首版，完成如下主链路：

`上传论文 -> 解析切块 -> 索引 -> 用户提问 -> Supervisor 路由 -> Paper RAG -> 证据边界判断 -> 带引用回答 -> 写回 L1`

### 3.2 Phase 1 必做能力

- 单 `Supervisor` 请求入口
- 论文上传与文档接入状态机
- 文本解析、切块、索引
- `Paper RAG` 混合检索
- 证据边界与回答模式控制
- 会话级 `L1 working memory`
- 基础 `REST API + SSE`
- 最小可用 Web 页面或等价演示入口

### 3.3 Phase 1 预留但不做满的能力

- 多 `Agent` 协作执行
- `plan-execute`
- `L2/L3` 完整落地
- `L3 Memory Recall`
- `Web Supplement`
- feedback 驱动检索优化
- benchmark 面板化与完整可观察性后台

### 3.4 Phase 1 明确不做的事情

- 一开始就拆成微服务
- 完整权限系统与复杂账号体系
- 一开始就引入 `MySQL + Milvus + Redis + MQ` 的重型中间件组合
- learned memory 或持续后台自治系统
- 为了对齐秋招话术而强行堆满所有功能点

## 4. 设计原则

整个系统在 Phase 1 与后续演进中都遵守以下原则：

1. **主链路优先**  
   先把上传到问答的主链路做通，再扩充高级能力。

2. **边界清晰**  
   `orchestrator / ingest / rag / evidence / memory / interface` 之间职责明确，不混写成“大一统服务”。

3. **先单 Supervisor，后多 Agent**  
   首版由 `Supervisor` 统一完成任务判断、上下文装配、检索路由与结果汇总；后续再把复杂任务拆进 `plan-execute` 与子 Agent。

4. **Memory 与 Retrieval 分离**  
   `Memory` 负责长期保留什么；`Retrieval` 负责当前任务按需召回什么。Phase 1 不混讲、不混实现。

5. **证据边界显式建模**  
   `Evidence Boundary` 作为独立服务存在，不把可信度治理埋进 prompt。

6. **扩展缝先设计，功能分阶段落地**  
   `PlanExecuteFacade`、`MemoryRecallPort`、`FeedbackPort` 在 Phase 1 先定义接口，后续增量补实现。

## 5. 总体架构

Phase 1 采用单体应用、模块化分层架构，不直接复制参考仓的重型多模块方案。

### 5.1 四层结构

1. `Interface Layer`
   - `REST API`
   - `SSE` 流式输出
   - 上传接口
   - 最小 Web 页面

2. `Application Layer`
   - `ChatApplicationService`
   - `DocumentIngestApplicationService`
   - `SupervisorService`

3. `Domain/Core Layer`
   - `TaskRouter`
   - `PaperRagService`
   - `EvidenceBoundaryService`
   - `UploadStateMachine`
   - `WorkingMemoryService`
   - 预留 `PlanExecuteFacade`
   - 预留 `MemoryRecallPort`
   - 预留 `FeedbackPort`

4. `Infrastructure Layer`
   - `Spring AI Alibaba`
   - 大模型与 embedding 模型适配
   - `PostgreSQL + pgvector`
   - PDF/文本解析器
   - 本地文件存储
   - 异步任务执行

### 5.2 核心模块

#### `chat`

职责：

- 对外暴露聊天接口
- 支持同步回答与 SSE 流式回答
- 承接会话 ID、问题内容、文档过滤条件等输入

输出：

- 最终回答文本
- 回答模式
- 引用片段
- 会话上下文更新结果

#### `orchestrator`

职责：

- 由 `SupervisorService` 统一做任务识别
- 统一读取 `L1 working memory`
- 统一决定当前请求是 `No Retrieval` 还是 `Paper RAG Only`
- 统一汇总检索结果、证据判断和最终回答

Phase 1 范围内：

- 实现 `No Retrieval`
- 实现 `Paper RAG Only`
- 预留 `Memory Recall` 与 `plan-execute` 入口，不做完整执行

#### `ingest`

职责：

- 处理文档上传
- 推进文档接入状态机
- 解析文本
- 进行切块
- 写入检索与元数据存储

状态机固定为：

`UPLOADED -> PARSING -> INDEXING -> INDEXED / FAILED`

#### `rag`

职责：

- query understanding
- metadata filtering
- hybrid retrieval
- lightweight rerank
- retrieval trace 记录

输出：

- 候选 `chunk`
- 排序分数
- 引用元数据
- trace 信息

#### `evidence`

职责：

- 评估当前回答是否具备足够本地论文证据
- 将检索结果分成：
  - `SUFFICIENT`
  - `WEAK`
  - `NONE`
- 将最终输出模式映射为：
  - `LOCAL_EVIDENCE`
  - `LOCAL_WEAK_EVIDENCE`
  - `REFUSAL`

说明：

- `WEB_SUPPLEMENT` 在 Phase 1 只保留枚举与接口
- Phase 1 不把联网补充真正上线

#### `memory`

职责：

- 维护会话级 `L1`
- 保存最近 `K` 轮对话
- 维护结构化 `working memory`

Phase 1 范围内 `working memory` 字段：

- `rollingSummary`
- `salientFacts`
- `currentTask`
- `compressedRounds`

Phase 1 同时预留但不实现完整能力：

- `lastDepositedTurnId`
- `lastDepositAt`
- `L2/L3` 写入
- `L3 Memory Recall`

## 6. 核心数据流

### 6.1 论文上传与索引链路

1. 用户上传论文文件
2. 系统创建文档记录，状态为 `UPLOADED`
3. 异步任务启动，状态推进为 `PARSING`
4. 解析器抽取文本内容与基础元数据
5. 切块器生成 `chunk`
6. 系统推进状态为 `INDEXING`
7. 将 `chunk`、embedding、元数据、全文检索字段写入存储
8. 成功后状态更新为 `INDEXED`
9. 任一阶段失败则进入 `FAILED`，并记录 `failureStage` 与错误信息

### 6.2 研究问答链路

1. 用户发起问题
2. `ChatController` 调用 `SupervisorService`
3. `SupervisorService` 读取会话级 `L1`
4. `TaskRouter` 判断是：
   - `No Retrieval`
   - `Paper RAG Only`
5. 若走 `Paper RAG Only`：
   - 构造查询
   - 应用文档过滤
   - 执行混合检索
   - 执行轻量重排
   - 生成 retrieval trace
6. `EvidenceBoundaryService` 对证据充分性进行判断
7. `SupervisorService` 基于回答模式组织 prompt 与最终输出
8. 返回带引用片段的回答
9. 写回 `L1 working memory`

## 7. 技术选型

### 7.1 后端与 AI

- `Java 17`
- `Spring Boot`
- `Spring AI Alibaba`

选型理由：

- 与用户预期技术栈一致
- 便于统一接入聊天模型、embedding 模型和流式输出
- 适合作为后续 `Supervisor / Agent / Tool` 演进的基础

### 7.2 数据库与检索

- `PostgreSQL`
- `pgvector`

选型理由：

- Phase 1 可用一套存储同时承载：
  - 文档元数据
  - 上传状态机
  - `chunk` 元数据
  - 向量检索
  - 基础全文检索
- 运维复杂度低于 `MySQL + Milvus` 双存储
- 更适合“先跑通主链路，再逐步增强”

### 7.3 持久层

- `MyBatis` 或 `JdbcTemplate`

原则：

- 不强推重 ORM
- 检索 SQL、trace SQL 与状态查询需保持可控

### 7.4 文档解析

Phase 1 采用：

- `PDFBox` / `Tika`
- Java 侧解析接口封装

同时预留：

- `Python sidecar parser` 扩展口

原因：

- 首版先把解析链路跑通
- 遇到复杂 PDF 再平滑演进为独立 Python 解析服务

### 7.5 文件存储

Phase 1 采用：

- 本地文件系统

同时抽象：

- `FileStoragePort`

用于后续切换：

- `MinIO`
- `OSS`
- `S3`

### 7.6 异步机制

Phase 1 采用：

- `Spring @Async`

不在首版引入：

- 消息队列

原因：

- 上传解析和索引需要异步化
- 但当前阶段没有必要引入额外基础设施

### 7.7 前端

Phase 1 采用：

- 轻量 Web 页面
- 原生 JS 或最小前端技术栈
- SSE 展示流式回答

原则：

- 以验证主链路为主
- 不把前端复杂度提前成阻塞项

### 7.8 数据库迁移

- `Flyway`

理由：

- 从首阶段开始就保持数据库 schema 可演进

## 8. 数据模型建议

Phase 1 至少需要以下核心对象：

### 8.1 文档对象 `document`

字段建议：

- `id`
- `title`
- `original_file_name`
- `storage_path`
- `status`
- `failure_stage`
- `parse_error`
- `created_at`
- `updated_at`

### 8.2 文档切块对象 `document_chunk`

字段建议：

- `id`
- `document_id`
- `chunk_index`
- `content`
- `token_count`
- `embedding`
- `metadata_json`
- `created_at`

### 8.3 会话对象 `chat_session`

字段建议：

- `id`
- `session_key`
- `current_task`
- `rolling_summary`
- `salient_facts_json`
- `compressed_rounds_json`
- `created_at`
- `updated_at`

### 8.4 会话消息对象 `chat_message`

字段建议：

- `id`
- `session_id`
- `role`
- `content`
- `answer_mode`
- `created_at`

### 8.5 检索追踪对象 `retrieval_trace`

字段建议：

- `id`
- `session_id`
- `query_text`
- `filters_json`
- `top_chunks_json`
- `rerank_result_json`
- `created_at`

## 9. 检索与证据边界

### 9.1 检索链路

Phase 1 的 `Paper RAG` 链路收敛为：

1. query understanding
2. metadata filtering
3. keyword / full text retrieval
4. vector retrieval
5. hybrid merge
6. lightweight rerank
7. 生成引用片段与 retrieval trace

### 9.2 证据边界

`Evidence Boundary` 必须显式实现，而不是隐式依赖模型“自己保守”。

Phase 1 判断等级：

- `SUFFICIENT`
- `WEAK`
- `NONE`

Phase 1 输出模式：

- `LOCAL_EVIDENCE`
- `LOCAL_WEAK_EVIDENCE`
- `REFUSAL`

约束如下：

- `LOCAL_EVIDENCE`：必须有可引用的高相关论文证据
- `LOCAL_WEAK_EVIDENCE`：允许给出保守回答，但要显式体现证据不足
- `REFUSAL`：证据不足且不应臆测时直接拒答

## 10. 错误处理与可治理性

### 10.1 上传链路

必须能够明确区分：

- 上传成功但尚未解析
- 解析失败
- 索引失败

不能只返回笼统的“处理失败”。

### 10.2 问答链路

必须能够明确区分：

- 无需检索
- 检索命中较强
- 检索命中较弱
- 检索证据不足

### 10.3 可追踪性

Phase 1 至少保留：

- 查询文本
- 过滤条件
- 检索出的 top chunks
- rerank 后结果
- 最终输出模式

## 11. 分阶段演进路线

### 11.1 Phase 1

单 `Supervisor` 可运行主链路：

- 上传
- 解析
- 索引
- 问答
- 证据边界
- `L1`

### 11.2 Phase 2

引入 `AgentExecutor` 抽象：

- 检索子能力
- 分析子能力
- 写作子能力
- `plan-execute` 入口

### 11.3 Phase 3

补齐：

- `L2`
- `L3`
- 显式记忆
- compaction 前增量 flush
- session close 补偿 flush
- `L3 Memory Recall`

### 11.4 Phase 4

补齐：

- feedback 驱动检索优化
- benchmark snapshot
- 报告生成
- 更完整的研究任务链

## 12. Phase 1 验收标准

### 12.1 功能验收

必须满足：

1. 用户可以上传论文并看到明确状态流转
2. 已索引论文可以用于提问
3. `Supervisor` 可在 `No Retrieval / Paper RAG Only` 间路由
4. 系统返回带引用片段的回答
5. 系统输出 `LOCAL_EVIDENCE / LOCAL_WEAK_EVIDENCE / REFUSAL`
6. `L1 working memory` 支持连续追问
7. SSE 流式输出可以正常工作

### 12.2 工程验收

必须满足：

- 文档、切块、会话、trace 有明确数据结构
- 上传失败可定位阶段
- 检索链路可追踪
- 模块边界清晰
- 后续扩展接口已定义

### 12.3 测试验收

至少包含：

- `UploadStateMachine` 单元测试
- `EvidenceBoundaryService` 单元测试
- `PaperRagService` 检索流程测试
- `SupervisorService` 主链路集成测试
- 一条“上传论文 -> 提问 -> 返回引用回答”的端到端 happy path

## 13. 开发顺序

Phase 1 实际编码时建议按以下顺序推进：

1. 搭项目骨架与依赖
2. 落数据库 schema、上传状态机与文档接入
3. 实现切块、索引与检索主链路
4. 实现 `Supervisor + Evidence Boundary + Chat`
5. 补 SSE、最小前端与测试闭环

## 14. 参考实现的采用与舍弃

参考 `D:\ai-research-assistant-ultimate-master`，Phase 1 建议：

采用：

- Java + Spring Boot 主栈
- 按职责划分模块边界
- 后续可演进为更完整研究助手系统的结构思路

不直接采用：

- 首阶段就拆成重型多模块
- 首阶段就上 `MySQL + Milvus + Redis + 安全模块 + 多 Agent`
- 先追求“组件齐”而不是“主链路稳定”

## 15. 结论

Phase 1 的本质不是做一个“看起来先进”的 Agent 架构展示，而是做出一个真实可运行、边界清晰、能持续扩展的研究助手首版。

一句话总结就是：

`Phase 1 先以 Java + Spring Boot + Spring AI Alibaba 为主栈，围绕单 Supervisor、论文接入、Paper RAG、证据边界与 L1 working memory 做出可运行主链路，同时用清晰接口为多 Agent、L2/L3、memory recall、feedback 和 benchmark 的后续落地预留扩展缝。`
