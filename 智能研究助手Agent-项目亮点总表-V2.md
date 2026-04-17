# 智能研究助手Agent：项目亮点总表 V2

## 1. 这份表怎么用

这份总表的目标不是罗列功能点，而是把项目亮点压缩成少数几个“能力母体”，方便同时服务三类场景：

- 简历项目描述
- 技术面项目讲述
- 深挖追问时的展开回答

因此，整份表按三层组织：

1. `主亮点`
2. `工程亮点`
3. `表达优先级`

## 2. 一句话总判断

这个项目最值钱的地方，不是单独做了某一个 Agent 或某一个 RAG，而是把：

- `多Agent协作`
- `三级记忆与上下文工程`
- `双检索体系`
- `证据边界与可靠性治理`
- `反馈驱动检索优化`

真正组织成了一套长期连续、可追溯、可优化的科研任务系统。

## 3. 主亮点总表

| 主亮点 | 简历版怎么写 | 面试版怎么讲 | 深挖版核心点 |
|---|---|---|---|
| `1. Supervisor + 多Agent协作` | `设计 Supervisor Agent 主导的多Agent协作架构，简单任务直解，复杂任务由 Supervisor 在内部进入 plan-execute 并调度专用子Agent执行。` | 强调不是为了追热点堆多Agent，而是为了把复杂科研任务拆成检索、分析、写作等可收敛子任务。 | `Supervisor` 统一持有上下文、控制执行路径、裁剪任务包、汇总结果；复杂任务走 `plan-execute`，子Agent 在职责域内以 `ReAct` 执行。 |
| `2. 三级记忆 + 上下文工程` | `构建 L1/L2/L3 分层记忆体系，支撑会话连续性、全局认知和长期研究沉淀。` | 强调不是把记忆当聊天历史堆起来，而是按时间尺度治理：L1 连续性，L2 稳定认知，L3 长期研究沉淀。 | `L1` 包括 recent turns 与 working memory；`L2` 为 `USER.md / SOUL.md / Research_state.md`；`L3` 采用显式记忆、compaction 前增量 flush、session close 补偿 flush，并通过 `lastDepositedTurnId + merge` 避免重复沉淀。 |
| `3. 双检索体系：L3 Recall + Paper RAG` | `设计长期记忆检索与论文证据检索解耦的双检索体系，由 Supervisor 按任务路由到 Memory Recall、Paper RAG 或双检索模式。` | 强调不是只有一个 RAG 库，而是把历史研究上下文和当前回答证据分开治理。 | `L3 semantic/hybrid memory recall` 提供历史研究上下文；`Paper RAG` 提供当前回答证据；`Supervisor` 在 `No Retrieval / Memory Recall Only / Paper RAG Only / Memory + Paper` 间路由；双检索默认 `Memory First -> Paper Second`。 |
| `4. 证据边界与可靠性治理` | `构建证据边界、拒答/降级与状态机机制，提升回答可信度与系统可治理性。` | 强调系统不是只追求“能答”，而是优先保证“答得住”。 | `Evidence Boundary` 区分 `SUFFICIENT / WEAK / NONE`，再联动 `LOCAL_EVIDENCE / LOCAL_WEAK_EVIDENCE / WEB_SUPPLEMENT / REFUSAL`；上传链路通过显式状态机实现失败可定位。 |
| `5. 反馈驱动检索优化` | `引入点赞/点踩驱动的反馈优化机制，把用户反馈直接回流到论文证据 chunk 的排序信号中。` | 强调点赞点踩不是普通交互功能，而是直接作用于检索质量优化。 | 用户反馈会更新关联论文证据 chunk 的 `feedbackScore`；后续召回与重排把该分数作为额外排序信号，从而提升被正反馈验证过的高质量证据片段优先级。 |

## 4. 工程亮点总表

| 工程亮点 | 价值 | 最适合怎么讲 |
|---|---|---|
| `文件上传解析状态机` | 把论文接入从“上传文件”升级成可治理链路 | `UPLOADED -> PARSING -> INDEXING -> INDEXED / FAILED`，并保留 `failureStage / parseError`。 |
| `超大文件处理方案` | 解决科研场景下 PDF/资料体积大、处理耗时长的问题 | 强调不是同步阻塞式上传，而是把接入链路工程化处理。 |
| `独立 Python 解析程序` | 把文档解析从主服务职责里拆开，便于独立演进和问题隔离 | 强调这是能力边界清晰的独立解析模块，而不是堆在主服务里。 |
| `RAG 混合检索` | 提升论文检索召回质量 | overlap-aware chunking、intent-aware query planning、hybrid retrieval、lightweight rerank。 |
| `offline benchmark snapshot + retrieval trace` | 让 Paper RAG 优化有证据，不靠感觉 | 小规模固定样本 snapshot 显示 `Top1 hit rate 0.70 -> 0.90`，`Multilingual 0.00 -> 1.00`，重点用于验证优化方向。 |
| `长期记忆增量沉淀机制` | 避免 L3 变成聊天流水账 | 事件触发写入、`lastDepositedTurnId`、轻量 merge。 |

## 5. 表达优先级

### 5.1 简历优先级

简历里最该优先保留：

1. `Supervisor + 多Agent`
2. `三级记忆 + 上下文工程`
3. `双检索体系`
4. `证据边界 / 状态机`
5. `feedback-driven retrieval optimization`

简历里不建议写得太满的内容：

- 过多实现细节
- 太细的枚举值
- 过深的 flush / merge 规则

### 5.2 面试讲述优先级

面试时最该主动打的顺序：

1. 这不是普通聊天机器人，而是长期科研任务系统
2. `Supervisor + 多Agent`
3. `L1/L2/L3 + 双检索`
4. `L3 recall + paper RAG` 的双检索边界
5. 证据边界与拒答/降级
6. feedback 驱动检索优化

### 5.3 深挖优先级

如果面试官开始深挖，最值得展开的是：

1. 为什么简单任务不默认多Agent
2. `plan-execute` 和 `ReAct` 如何分层
3. 长期记忆什么时候写、怎么避免重复
4. 为什么 `L3` 不直接并入论文 `RAG`
5. 为什么双检索默认 `Memory First -> Paper Second`
6. 证据边界为什么必须与 memory recall 解耦

## 6. 最终推荐版本

如果压缩成你这个项目最适合秋招的五句话，我建议固定成下面这版：

1. `我做的不是普通聊天机器人，而是一个面向科研场景的长期任务系统。`
2. `系统采用 Supervisor Agent 主导的多Agent协作架构，简单任务直解，复杂任务进入 plan-execute。`
3. `我设计了 L1/L2/L3 三层记忆，把会话连续性、全局认知和长期研究沉淀拆开治理。`
4. `检索层不是单一 RAG，而是把 L3 的历史研究记忆检索和论文证据检索解耦，再由 Supervisor 做检索路由。`
5. `系统还加入了证据边界、状态机、联网补充和 feedback 驱动检索优化，让它具备可信度、可治理性和长期优化能力。`

## 7. 一句话结论

如果一定要压缩成一句话，这个项目最值钱的亮点可以概括为：

`Supervisor 主导的多Agent协作 + 三级记忆与双检索体系 + 证据边界与 feedback 驱动检索优化，共同把系统从单轮问答工具升级成长期连续、可追溯、可优化的科研助手。`
