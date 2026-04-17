# 智能研究助手Agent：架构图谱 V2

这份图谱文档服务于两类目标：

- 帮助自己快速建立对系统整体脉络的理解
- 帮助秋招面试时用一套自洽、稳定、可追问的方式讲清项目

全文统一采用 V2 口径：

- `Supervisor Agent` 主导的自治多Agent协作架构
- 简单任务由 `Supervisor` 直接处理
- 复杂任务由 `Supervisor` 在内部进入 `plan-execute`
- 专用子Agent在职责域内以 `ReAct` 为主要执行范式
- 上下文工程采用 `L1 / L2 / L3 + 双检索`
- 长期记忆采用“显式记忆 + compaction 前增量 flush + session close 补偿 flush”
- 可靠性围绕证据边界、拒答/降级、上传状态机、benchmark、联网补充与反馈驱动检索优化展开

## 1. 系统总体架构图

```mermaid
flowchart LR
    U["用户"] --> FE["Web 前端"]
    FE --> API["REST API / SSE 流式输出"]

    subgraph O["编排层"]
        SUP["Supervisor Agent\n全局上下文 / 路由 / 内部规划 / plan-execute / 结果汇总"]
        AUDIT["证据约束与输出收敛"]
    end

    API --> SUP
    SUP --> AUDIT
    AUDIT --> API

    subgraph A["Agent 执行层"]
        RA["Research Agent"]
        RE["Retrieval Agent"]
        WA["Writing Agent"]
    end

    SUP --> RA
    SUP --> RE
    SUP --> WA

    subgraph T["工具与服务层"]
        PARSE["论文解析"]
        CHUNK["Chunking"]
        RET["Hybrid Retrieval"]
        RERANK["Lightweight Rerank"]
        MEMIO["记忆读写 / merge"]
        WEB["Web Supplement"]
        FEEDSVC["Feedback Score Update"]
        REVIEW["输出审查"]
    end

    RA --> RET
    RE --> RET
    WA --> REVIEW
    RA --> REVIEW
    RE --> RERANK
    RET --> RERANK

    subgraph M["记忆与检索层"]
        L1["L1 短期记忆\nrecent turns / working memory"]
        L2["L2 全局认知\nUSER.md / SOUL.md / Research_state.md"]
        L3["L3 长期记忆\n每日记忆.md"]
        MR["L3 Memory Recall\n历史研究上下文"]
        PR["Paper RAG\n当前回答证据"]
    end

    MEMIO --> L1
    MEMIO --> L2
    MEMIO --> L3
    L3 --> MR
    RET --> PR
    PR --> RET
    WEB --> AUDIT

    subgraph K["论文知识接入层"]
        UP["上传接口"]
        SM["上传状态机\nUPLOADED -> PARSING -> INDEXING -> INDEXED / FAILED"]
        CORPUS["论文语料 / 索引"]
    end

    FE --> UP
    UP --> SM
    SM --> PARSE
    PARSE --> CHUNK
    CHUNK --> CORPUS
    CORPUS --> PR

    subgraph R["可靠性与闭环层"]
        EV["Evidence Boundary\nSUFFICIENT / WEAK / NONE"]
        OUT["输出模式\nLOCAL_EVIDENCE / LOCAL_WEAK_EVIDENCE / WEB_SUPPLEMENT / REFUSAL"]
        BM["Benchmark / Retrieval Trace"]
        FB["Feedback"]
        SCORE["论文证据 chunk feedbackScore"]
    end

    AUDIT --> EV
    EV --> OUT
    OUT --> API
    RET --> BM
    FE --> FB
    FB --> FEEDSVC
    FEEDSVC --> SCORE
    SCORE --> PR
```

## 2. 功能模块之间的数据流图

```mermaid
flowchart TD
    IN["用户请求 / 论文上传 / 反馈信号"] --> GATE{"请求类型"}

    GATE -->|聊天请求| CTX["Supervisor 读取上下文"]
    GATE -->|论文上传| INGEST["上传接入链路"]
    GATE -->|点赞 / 点踩反馈| FEEDBACK["反馈处理"]

    CTX --> READ1["读取 L1 短期记忆"]
    CTX --> READ2["读取 L2 全局认知"]
    CTX --> READ3["按需读取 L3 长期记忆"]

    READ1 --> DECIDE["Supervisor 内部规划决策"]
    READ2 --> DECIDE
    READ3 --> DECIDE

    DECIDE -->|简单任务| DIRECT["Supervisor 直接求解"]
    DECIDE -->|复杂任务| PE["Supervisor 内部 plan-execute"]

    DIRECT --> NEEDRET{"是否需要检索"}
    NEEDRET -->|Memory Recall| RETRIEVE_M["L3 Memory Recall"]
    NEEDRET -->|Paper RAG| RETRIEVE_P["Paper RAG"]
    NEEDRET -->|Memory + Paper| RETRIEVE_B["Memory First -> Paper Second"]
    NEEDRET -->|否| ANSWER["生成回答"]

    PE --> TASKS["任务拆解与子任务包"]
    TASKS --> RET_AGENT["Retrieval Agent"]
    TASKS --> RES_AGENT["Research Agent"]
    TASKS --> WRI_AGENT["Writing Agent"]

    RET_AGENT --> RETRIEVE_P
    RES_AGENT --> RETRIEVE_P
    RETRIEVE_M --> CONTEXT["历史研究上下文"]
    RETRIEVE_P --> EVIDENCE["论文证据包 / retrieval trace"]
    RETRIEVE_B --> CONTEXT
    RETRIEVE_B --> EVIDENCE
    CONTEXT --> RES_AGENT
    CONTEXT --> WRI_AGENT
    EVIDENCE --> RES_AGENT
    EVIDENCE --> WRI_AGENT
    RES_AGENT --> WRI_AGENT
    WRI_AGENT --> MERGE["Supervisor 汇总与审查"]

    RETRIEVE_M --> ANSWER
    RETRIEVE_P --> ANSWER
    DIRECT --> MERGE
    ANSWER --> MERGE
    MERGE --> BOUNDARY["证据边界判定"]
    BOUNDARY --> RESP["最终输出"]
    BOUNDARY -->|本地证据不足且允许联网| WEBFLOW["Web Supplement"]
    WEBFLOW --> RESP

    RESP --> WRITE_L1["写回 L1 与 working memory"]
    RESP --> MEM_ROUTE{"是否触发长期记忆沉淀"}
    MEM_ROUTE -->|用户显式记忆| EXPLICIT["按内容类型写入 L2\n或生成 L3 候选"]
    MEM_ROUTE -->|临近 compaction| FLUSH["只抽取 turnId > lastDepositedTurnId\n的新增区间"]
    MEM_ROUTE -->|会话关闭且未成功 flush| CLOSE_FLUSH["补偿式增量 flush"]

    FLUSH --> CAND["生成 daily memory candidate"]
    CLOSE_FLUSH --> CAND
    EXPLICIT --> WRITE_L2["写入 L2 全局认知\n或生成 L3 候选"]
    CAND --> L3MERGE{"与最近 daily memory\n是否重复/相近"}
    L3MERGE -->|是| MERGED["merge 到现有 L3 条目"]
    L3MERGE -->|否| WRITE_L3["append 新 L3 条目"]
    MERGED --> WATERMARK["更新 lastDepositedTurnId\n与 lastDepositAt"]
    WRITE_L3 --> WATERMARK

    INGEST --> STATUS["状态机推进"]
    STATUS --> PARSE2["解析"]
    PARSE2 --> CHUNK2["切块"]
    CHUNK2 --> INDEX["建立索引"]
    INDEX --> CORPUS2["论文知识底座"]
    CORPUS2 --> RETRIEVE_P

    FEEDBACK --> SIGNALS["定位关联证据 chunk"]
    SIGNALS --> SCORE2["更新论文证据 chunk feedbackScore"]
    SCORE2 --> CORPUS2
```

## 3. 业务逻辑图

```mermaid
flowchart TD
    START["用户进入系统"] --> TYPE{"输入类型"}

    TYPE -->|研究问答 / 追问| CHAT["进入聊天主链路"]
    TYPE -->|上传论文| UPLOAD["进入上传索引链路"]
    TYPE -->|点赞 / 点踩| FEED["进入反馈优化链路"]

    CHAT --> LOAD["Supervisor 读取 L1/L2/L3"]
    LOAD --> SIMPLE{"是否为简单任务"}

    SIMPLE -->|是| SIMPLE_FLOW["Supervisor 直接处理"]
    SIMPLE -->|否| COMPLEX["Supervisor 在内部进入 plan-execute"]

    SIMPLE_FLOW --> SIMPLE_RET{"是否需要检索或联网"}
    SIMPLE_RET -->|Memory Recall| MRQ["查 L3 Memory Recall"]
    SIMPLE_RET -->|Paper RAG| PRQ["查 Paper RAG"]
    SIMPLE_RET -->|Memory + Paper| BPQ["先 Memory Recall 再 Paper RAG"]
    SIMPLE_RET -->|联网补充| WSQ["Web Supplement"]
    SIMPLE_RET -->|否| SIMPLE_GEN["直接生成回答"]
    MRQ --> SIMPLE_GEN
    PRQ --> SIMPLE_GEN
    BPQ --> SIMPLE_GEN
    WSQ --> SIMPLE_GEN
    SIMPLE_GEN --> SIMPLE_BOUND["证据边界判定"]

    COMPLEX --> SPLIT["拆解检索 / 分析 / 写作子任务"]
    SPLIT --> REACT["子Agent 以 ReAct 执行"]
    REACT --> GATHER["Supervisor 汇总与收敛"]
    GATHER --> COMPLEX_BOUND["证据边界判定"]

    SIMPLE_BOUND --> MODE["输出模式选择"]
    COMPLEX_BOUND --> MODE

    MODE -->|证据充分| OUT1["LOCAL_EVIDENCE"]
    MODE -->|证据较弱| OUT2["LOCAL_WEAK_EVIDENCE"]
    MODE -->|需联网补充| OUT3["WEB_SUPPLEMENT"]
    MODE -->|证据不足| OUT4["REFUSAL"]

    OUT1 --> MEMORY["写回记忆与反馈闭环"]
    OUT2 --> MEMORY
    OUT3 --> MEMORY
    OUT4 --> MEMORY

    MEMORY --> L1W["更新 L1 短期记忆"]
    MEMORY --> FLUSH_DECIDE{"是否触发长期记忆沉淀"}
    FLUSH_DECIDE -->|显式记忆| L2L3["写入 L2\n或生成 L3 候选"]
    FLUSH_DECIDE -->|临近 compaction| INC["只抽取 turnId > lastDepositedTurnId\n的新增区间"]
    FLUSH_DECIDE -->|会话关闭且未成功 flush| FALLBACK["补偿式增量抽取"]
    INC --> L3MERGE2["轻量 merge 后写入 L3"]
    FALLBACK --> L3MERGE2
    MEMORY --> RECLOOP["等待用户反馈驱动后续检索优化"]

    UPLOAD --> ST["UPLOADED"]
    ST --> P1["PARSING"]
    P1 --> P2["INDEXING"]
    P2 --> P3{"是否成功"}
    P3 -->|成功| P4["INDEXED -> 进入论文知识底座"]
    P3 -->|失败| P5["FAILED -> 记录 failureStage / parseError"]

    FEED --> SCORE["更新关联论文证据 chunk feedbackScore"]
    SCORE --> NEXT["影响下一轮召回与重排优先级"]
```

## 4. 时序图一：简单研究问答

```mermaid
sequenceDiagram
    actor User as 用户
    participant FE as 前端
    participant SUP as Supervisor Agent
    participant MEM as L1/L2/L3
    participant MR as L3 Memory Recall
    participant PR as Paper RAG
    participant WEB as Web Supplement
    participant EB as Evidence Boundary
    participant L3F as L3 Flush

    User->>FE: 发起研究问答/追问
    FE->>SUP: 发送请求
    SUP->>MEM: 读取 L1/L2/L3 上下文
    MEM-->>SUP: 返回上下文包
    SUP->>SUP: 判断为简单任务
    SUP->>MR: 按需召回历史研究上下文
    MR-->>SUP: 返回历史上下文
    SUP->>PR: 按需检索论文证据
    PR-->>SUP: 返回证据片段
    SUP->>EB: 提交回答草稿与证据
    EB-->>SUP: 返回证据充分性与输出模式
    opt 本地证据不足且允许联网
        SUP->>WEB: 触发外部联网补充
        WEB-->>SUP: 返回外部补充信息
    end
    SUP->>MEM: 写回 L1 与 working memory
    opt 用户显式要求记忆
        SUP->>L3F: 按内容类型写入 L2 或生成 L3 候选
    end
    SUP-->>FE: 返回最终回答
    FE-->>User: 流式展示结果
```

## 5. 时序图二：复杂任务 plan-execute

```mermaid
sequenceDiagram
    actor User as 用户
    participant FE as 前端
    participant SUP as Supervisor Agent
    participant RET as Retrieval Agent
    participant RES as Research Agent
    participant WRI as Writing Agent
    participant MEM as L1/L2/L3
    participant MR as L3 Memory Recall
    participant RAG as Paper RAG
    participant WEB as Web Supplement
    participant EB as Evidence Boundary
    participant FB as Feedback Score Update
    participant L3F as L3 Flush

    User->>FE: 发起复杂研究任务
    FE->>SUP: 发送请求
    SUP->>MEM: 读取全局上下文
    MEM-->>SUP: 返回上下文包
    SUP->>SUP: 在内部进入 plan-execute\n完成任务分解与执行排序
    SUP->>MR: 先召回 L3 历史研究上下文
    MR-->>SUP: 返回 Memory Context
    SUP->>RET: 分发检索子任务
    RET->>RAG: 基于历史上下文检索论文证据
    RAG-->>RET: 返回证据包与 trace
    RET-->>SUP: 返回检索结果
    SUP->>RES: 分发分析子任务
    RES->>RAG: 补充检索/验证证据
    RAG-->>RES: 返回补充证据
    RES-->>SUP: 返回分析结论
    SUP->>WRI: 分发写作子任务
    WRI-->>SUP: 返回结构化输出
    SUP->>EB: 汇总结果并做证据边界校验
    EB-->>SUP: 返回输出模式
    opt 本地证据不足且允许联网
        SUP->>WEB: 触发 Web Supplement
        WEB-->>SUP: 返回补充信息
    end
    SUP->>MEM: 更新 L1 与 working memory
    opt 临近 compaction
        SUP->>L3F: 只抽取 turnId > lastDepositedTurnId 的新增区间
        L3F-->>SUP: merge/append 到每日记忆并更新 watermark
    end
    SUP->>FB: 等待用户反馈更新论文证据 chunk feedbackScore
    SUP-->>FE: 返回最终结果
    FE-->>User: 展示结构化回答/报告
```

## 6. 时序图三：论文上传与索引

```mermaid
sequenceDiagram
    actor User as 用户
    participant FE as 前端
    participant API as 上传接口
    participant SM as 上传状态机
    participant PARSE as 解析服务
    participant CHUNK as Chunking 服务
    participant INDEX as 索引服务
    participant CORPUS as 论文知识底座

    User->>FE: 上传论文
    FE->>API: 提交文件
    API->>SM: 创建接入任务
    SM->>SM: 标记 UPLOADED
    SM->>PARSE: 进入 PARSING
    PARSE-->>SM: 返回结构化文本/失败原因
    alt 解析成功
        SM->>CHUNK: 切块
        CHUNK-->>SM: 返回 chunk 集合
        SM->>INDEX: 进入 INDEXING
        INDEX->>CORPUS: 写入索引
        CORPUS-->>INDEX: 写入成功
        INDEX-->>SM: 标记 INDEXED
        SM-->>FE: 返回可检索状态
    else 解析或索引失败
        SM->>SM: 标记 FAILED
        SM-->>FE: 返回 failureStage / parseError
    end
```

## 7. 记忆系统架构图

```mermaid
flowchart TD
    U["用户请求 / 多轮对话"] --> SUP["Supervisor Agent\n对话编排 / 记忆管理 / 路径选择"]

    subgraph L1ZONE["L1 短期记忆层"]
        L1["最近 K 轮对话"]
        WM["working memory\nrollingSummary / salientFacts / currentTask / compressedRounds"]
        META["session metadata\nlastDepositedTurnId / lastDepositAt"]
    end

    subgraph L2ZONE["L2 全局认知层"]
        USER["USER.md"]
        SOUL["SOUL.md"]
        RS["Research_state.md"]
    end

    subgraph L3ZONE["L3 长期记忆层"]
        DAILY["每日记忆.md"]
    end

    subgraph RAGZONE["双检索与联网补充层"]
        PAPERS["论文知识库"]
        LONGCTX["L3 Memory Recall"]
        WEBR["Web Supplement"]
        RET["Paper Evidence Retrieval"]
    end

    SUP --> L1
    SUP --> WM
    SUP --> USER
    SUP --> SOUL
    SUP --> RS
    SUP --> RET
    PAPERS --> RET
    LONGCTX --> RET
    DAILY --> LONGCTX
    WEBR --> EXEC

    RET --> EXEC["回答 / 分析 / 写作"]
    L1 --> EXEC
    WM --> EXEC
    USER --> EXEC
    SOUL --> EXEC
    RS --> EXEC

    EXEC --> RESP["最终回答"]
    RESP --> L1UPD["更新 L1 与 working memory"]
    L1UPD --> L1
    L1UPD --> WM

    RESP --> EXPLICIT{"用户是否显式要求记住"}
    EXPLICIT -->|是| ROUTE{"写入 L2 还是 L3"}
    EXPLICIT -->|否| WAIT["继续会话"]

    ROUTE -->|稳定认知| L2WRITE["写入 L2"]
    ROUTE -->|研究沉淀| CAND["生成 daily memory candidate"]

    WAIT --> COMPACT{"即将触发会话压缩?"}
    WAIT --> CLOSE{"会话关闭?"}

    COMPACT -->|是| INC1["只抽取 turnId > lastDepositedTurnId 的增量区间"]
    CLOSE -->|是且本轮未成功 flush| INC2["补偿式增量抽取"]

    INC1 --> EXTRACT["Supervisor 抽取高价值研究记忆"]
    INC2 --> EXTRACT
    EXTRACT --> CAND

    CAND --> MERGE3{"与最近 daily memory 是否重复/相近"}
    MERGE3 -->|是| MERGED2["merge 到现有条目"]
    MERGE3 -->|否| APPEND2["append 新条目"]

    MERGED2 --> L3OK["写入/更新 L3 成功"]
    APPEND2 --> L3OK
    L3OK --> WATERMARK2["更新 lastDepositedTurnId / lastDepositAt"]
    WATERMARK2 --> META
```

## 8. 双检索路由图

```mermaid
flowchart TD
    Q["用户请求"] --> SUP["Supervisor Agent\n检索路由决策"]

    SUP --> ROUTE{"检索模式"}

    ROUTE -->|No Retrieval| DIRECT["直接进入回答链路"]
    ROUTE -->|Memory Recall Only| MR["L3 Memory Recall"]
    ROUTE -->|Paper RAG Only| PR["Paper RAG"]
    ROUTE -->|Memory + Paper| MF["Memory First"]

    MR --> MC["Memory Context"]
    PR --> PE["Paper Evidence"]

    MF --> MR2["先查 L3 Memory Recall"]
    MR2 --> ENRICH["补充历史上下文\n改写 query / 约束任务"]
    ENRICH --> PR2["再查 Paper RAG"]
    PR2 --> PE2["Paper Evidence"]
    MR2 --> MC2["Memory Context"]

    MC --> GEN["回答 / 分析 / 写作"]
    PE --> GEN
    MC2 --> GEN
    PE2 --> GEN
    DIRECT --> GEN

    PE --> EB["Evidence Boundary"]
    PE2 --> EB
    EB --> OUT["最终输出"]
```

这张图表达的核心是：

- `L3 recall` 和论文 `RAG` 是两条独立检索链
- `L3 recall` 负责历史研究上下文
- 论文 `RAG` 负责当前回答证据
- 双检索默认采用 `Memory First -> Paper Second`

## 9. 图谱解读建议

如果是自己复习，推荐按下面顺序看：

1. 先看“系统总体架构图”，建立六层视图
2. 再看“数据流图”，理解上下文、检索、记忆、反馈如何流动
3. 再看“业务逻辑图”，理解简单任务与复杂任务的分流
4. 再看“记忆系统架构图”，把 L1/L2/L3 的写入边界彻底看清
5. 再看“双检索路由图”，理解 `L3 recall`、`paper RAG` 与 `Web Supplement` 的关系
6. 最后看三张时序图，把聊天、复杂任务、论文上传三条关键链路串起来

如果是面试讲项目，推荐按下面顺序讲：

1. 先用总体架构图介绍 `Supervisor + 多Agent`
2. 再用业务逻辑图讲 `简单任务直解 / 复杂任务 plan-execute`
3. 再用记忆系统架构图讲 `L1/L2/L3 + 双检索` 和事件触发式长期沉淀
4. 再用双检索路由图讲 `L3 recall` 与 `paper RAG` 的边界
5. 再用时序图讲一条简单链路和一条复杂链路
6. 最后补充上传状态机、证据边界、联网补充与 feedback 驱动检索优化，体现系统工程化能力
