# 从 0 scoped chunk 到长期可观测：一次 RAG 检索问题的工程化定位复盘

## 1. 背景：问题不是“检索不到”，而是“我们不知道为什么检索不到”

在智能研究助手的开发过程中，前端研究过程面板开始展示 Agent 的中间工具调用。最初这只是一个 UI 能力：把 `paper_rag`、`web_search`、`memory_recall` 等工具调用过程展示出来，让用户看到系统不是一次性生成答案，而是在检索、召回、判断证据后再回答。

但这个 UI 很快暴露了一个更深的问题：对话中出现了大量类似下面的事件：

```text
paper_rag returned 0 scoped chunk(s)
```

从产品表面看，这意味着“资料库没有找到内容”。但从工程角度看，这句话几乎没有诊断价值。因为 `0 scoped chunk(s)` 背后可能有很多完全不同的原因：

- Agent 生成的 query 太窄或重复。
- query rewrite 失败。
- keyword、vector、metadata 三路检索都没有命中。
- 全局有命中，但被当前项目 scope 过滤掉了。
- rerank 后没有剩余结果。
- 本地向量索引为空。
- 检索有结果，但证据评估阈值太严格，最终被判成无证据。

如果只看 UI，所有问题都长得像“资料库没命中”。这会导致错误优化：可能去改 UI、改 prompt、改 query，但真正的问题可能在后端索引生命周期或证据边界策略上。

这就是本次工作的起点：**不是立刻修某一个 0 命中，而是先把 RAG 检索链路变成可解释、可复盘、可长期演进的系统。**

## 2. 当前技术架构：先看懂系统地图

如果第一次接触这个项目，直接看“0 scoped chunk”或“三次调优”会很难理解。因为这个问题不是单独发生在某一个检索函数里，而是发生在一个 Agent + RAG + Evidence Boundary 的完整链路中。

当前系统可以简化成四层：

```text
前端工作台
-> 会话/运行事件层
-> Agent 工具编排层
-> RAG 检索与证据层
```

### 2.1 前端工作台：问题最先被看见的地方

前端不是直接展示一段最终答案，而是展示三类信息：

- 中间对话区域：用户问题、研究回答、研究过程。
- 右侧证据区域：当前回答最终关联的引用来源。
- 研究过程模块：展示 Agent 调用了哪些工具、每个工具返回了多少资料、有没有边界提示。

这次问题最早就是在研究过程模块里暴露的：

```text
paper_rag returned 0 scoped chunk(s)
```

所以 UI 的作用不是根因定位，而是把原来藏在后端里的异常显性化。

### 2.2 Agent 工具编排层：谁决定要不要检索

用户提问后，系统不是固定走 RAG，而是由主 Agent 决定是否调用工具：

```text
用户问题
-> 主 Agent
-> 可选工具：
   - paper_rag：查当前项目资料
   - web_search：查外部实时信息
   - memory_recall：召回历史会话记忆
-> 汇总工具结果
-> 生成最终回答
```

其中 `paper_rag` 是本次复盘的核心工具。它的职责不是生成答案，而是返回当前项目资料库中可以支撑答案的 paper chunks。

一个重要边界是：Agent 可能会多次调用 `paper_rag`，也可能生成多个不同 query。因此，检索问题不一定是 RAG 算法问题，也可能是 Agent 工具调用策略问题。

### 2.3 Paper RAG 检索链路：一次资料检索到底发生了什么

一次 `paper_rag` 调用内部大致是：

```text
paper_rag(query, maxResults)
-> QueryRewriteService
-> keyword search
-> vector search
-> metadata search
-> merge candidates
-> project scope filter
-> rerank
-> return scoped chunks
```

这里有几个关键概念。

**query rewrite**

用户问题可能是中文、长句、带公式、带上下文。系统会把它改写成更适合检索的 query 或关键词组合。例如：

```text
原始问题：论文是如何利用多普勒频移多样性区分同位置用户的呢
可能的检索 query：
- 多普勒频移多样性 区分同位置用户
- Doppler diversity co-located users
- Doppler frequency difference user separation temporal signature
```

**三路混合检索**

系统不是只靠一种检索方式，而是混合三路：

| 检索方式 | 作用 | 典型问题 |
|---|---|---|
| keyword | 精确词面匹配 | 术语不一致时容易漏召回 |
| vector | 语义相似度召回 | embedding 或索引异常时会整体失效 |
| metadata | 文档标题、来源等结构化信息 | 只能补充，不能替代正文检索 |

**project scope**

系统里可能有多个项目、多个资料来源。即使全局检索命中了 chunk，也必须确认它属于当前项目允许使用的资料。

所以“命中”和“当前回答可引用”不是一回事：

```text
全局命中 chunk
-> 是否属于当前项目 indexed document？
-> 是否能映射到 source_document？
-> 是否允许进入当前 answer evidence？
```

这就是为什么我们关注 `preScopeHits` 和 `postScopeHits`。

**rerank**

多路检索结果合并后，还要排序、截断，最终只返回有限数量的 scoped chunks。

因此，一个 `0 scoped chunk(s)` 可能发生在很多位置：

```text
query rewrite 前
三路检索阶段
scope 过滤阶段
rerank 阶段
最终引用阶段
```

### 2.4 Evidence Boundary：检索到资料不等于答案有强证据

RAG 返回 chunks 后，系统还会判断证据强度，并决定回答模式：

```text
RagResult
-> EvidenceBoundaryService
-> EvidenceLevel:
   - SUFFICIENT
   - WEAK
   - NONE
-> AnswerMode:
   - LOCAL_EVIDENCE
   - LOCAL_WEAK_EVIDENCE
   - WEB_SUPPLEMENT
   - REFUSAL
```

这层非常重要，因为它解释了为什么“检索有结果”之后，最终回答仍可能被标成弱证据或拒答。

本次第二次调优就发生在这里：系统已经检索到了 paper evidence，但因为本地 embedding 分数偏低，旧规则仍把它判成 `NONE/REFUSAL`。

### 2.5 数据持久化：最终答案与中间过程不是一张表

为了理解后续分析，还需要知道几张关键数据表或数据对象：

| 对象 | 作用 |
|---|---|
| `retrieval_trace` | 记录每次 RAG 检索过程 |
| `retrieval_trace.rerank_result_json.observation` | 存结构化检索观测数据 |
| `assistant_answer` | 存最终回答、answer_mode、evidence_state |
| `evidence_source` | 存最终答案引用了哪些 paper/web evidence |
| `stream_event_record` | 存运行过程事件，供前端研究过程展示 |

这意味着分析问题时不能只看一张表。我们需要把中间检索过程和最终回答状态串起来：

```text
retrieval_trace
-> evidence_source
-> assistant_answer
-> UI 研究过程与右侧证据来源
```

### 2.6 一张完整链路图

把上面的内容合起来，当前 RAG 相关链路可以画成：

```text
用户问题
  |
  v
主 Agent 判断是否需要工具
  |
  +--> memory_recall：历史上下文
  |
  +--> web_search：外部实时信息
  |
  +--> paper_rag：当前项目资料
          |
          v
      query rewrite
          |
          v
      keyword / vector / metadata
          |
          v
      merge candidates
          |
          v
      project scope filter
          |
          v
      rerank
          |
          v
      RagResult scoped chunks
          |
          +--> retrieval_trace observation
          |
          v
      evidence_source 落库
          |
          v
      EvidenceBoundaryService
          |
          v
      answer_mode / evidence_state
          |
          v
      前端展示研究回答、研究过程、证据来源
```

有了这张图，后面三次优化的逻辑就清楚了：

- 第一次优化发生在 `vector search` 的索引生命周期。
- 第二次优化发生在 `EvidenceBoundaryService` 的证据强度判断。
- 第三次优化发生在 `主 Agent -> paper_rag` 的工具调用边界。

## 3. 第一性原理：为什么要先做可观测，而不是直接调 query

RAG 问题很容易被误判，因为它跨越多个层次：

```text
用户问题
-> Agent 工具选择
-> paper_rag 工具调用
-> query rewrite
-> keyword / vector / metadata 混合检索
-> project scope 过滤
-> merge + rerank
-> evidence_source 落库
-> evidence boundary 判断
-> answer_mode 决策
-> UI 展示
```

如果没有结构化观测，开发者只能从最终答案倒推中间过程。这种倒推很脆弱，因为最终答案可能已经混合了 LLM 生成、检索片段、记忆上下文和降级策略。

因此这次我们没有先问“怎么让 query 更准”，而是先问：

**系统能否回答：这一次为什么没有拿到可用证据？**

这个问题决定了实现顺序：

1. 先构建长期可复用的检索观测模型。
2. 再基于真实运行数据定位问题。
3. 最后才进行有针对性的功能优化。

## 4. 可观测系统的设计目标

我们把 `paper_rag` 的一次调用抽象成一个 `RetrievalObservation`。它不是日志文本，而是结构化诊断数据。

一次 observation 至少要回答这些问题：

- 原始 query 是什么？
- query rewrite 采用了什么策略？
- 实际执行了几个 retrieval query？
- keyword、vector、metadata 各自命中多少？
- scope 过滤前后分别剩多少？
- merge 后多少候选？
- rerank 后多少候选？
- 最终返回多少 scoped chunk？
- 如果返回 0，属于哪类 zero-hit reason？

核心字段类似：

```json
{
  "originalQuery": "多普勒频移多样性 区分同位置用户",
  "rewriteStrategy": "cjk_llm_rewrite",
  "retrievalQueryCount": 3,
  "allowedDocumentCount": 2,
  "backendStats": {
    "vector": {
      "queryCount": 3,
      "preScopeHits": 30,
      "postScopeHits": 30,
      "durationMs": 9
    },
    "keyword": {
      "queryCount": 3,
      "preScopeHits": 0,
      "postScopeHits": 0,
      "durationMs": 175
    },
    "metadata": {
      "queryCount": 3,
      "preScopeHits": 0,
      "postScopeHits": 0,
      "durationMs": 12
    }
  },
  "mergedCandidateCount": 22,
  "rerankedChunkCount": 10,
  "returnedScopedChunkCount": 10,
  "zeroHitReason": null
}
```

这类数据被挂到 `retrieval_trace.rerank_result_json.observation` 中，同时通过 run event / trace event 提供给前端研究过程模块。

这样做有两个好处：

- 后端可以用 SQL 聚合分析，不依赖肉眼看 UI。
- 前端可以展示摘要，不需要解析自然语言日志。

## 5. Zero-hit 不应该是字符串，而应该是分类

原来的 `0 scoped chunk(s)` 是一种描述，不是一种诊断。

我们把它拆成更可行动的分类：

```text
NO_SCOPED_EVIDENCE
QUERY_EMPTY_OR_INVALID
NO_BACKEND_HITS
SCOPE_FILTERED_EMPTY
RERANK_EMPTY
TOOL_ERROR
```

分类的价值在于：不同原因对应完全不同的修复路径。

| zero-hit 类型 | 真实含义 | 优先修复方向 |
|---|---|---|
| `NO_SCOPED_EVIDENCE` | 当前项目没有可用 indexed paper | 资料上传/索引状态 |
| `NO_BACKEND_HITS` | 三路检索都没命中 | query rewrite、embedding、索引 |
| `SCOPE_FILTERED_EMPTY` | 全局有命中但项目 scope 过滤为空 | scope 映射、项目资料边界 |
| `RERANK_EMPTY` | 检索有候选但 rerank 后为空 | rerank 策略 |
| `QUERY_EMPTY_OR_INVALID` | Agent 传入 query 不可用 | 工具调用约束 |
| `TOOL_ERROR` | 工具异常 | 稳定性和降级 |

这一步非常关键：它把“现象”变成了“可定位的工程对象”。

## 6. 第一次真实定位：本地向量索引重启后丢失

有了 observation 后，我们不再猜，而是直接查询最新运行数据。

一开始看到的旧数据大致是：

```text
session 8:
retrieval calls: 25
zero-hit calls: 16
vector hits: 0
keyword hits: 29
metadata hits: 1
returned chunks: 29
avg returned chunks: 1.16
```

这组数据说明了一件事：问题不只是 query 差，因为 keyword 仍然有少量命中；真正异常的是 vector hits 一直为 0。

进一步查运行环境：

```text
AI_VECTORSTORE_TYPE=none
AI_EMBEDDING_PROVIDER=none
```

这意味着系统使用的是本地内存版 `LocalVectorSearchPort`。数据库中的 `document_chunk` 还在，文档状态也是 `INDEXED`，但后端重启后，内存向量索引会变空。

这就是根因：

**数据库保留了 chunk，但本地内存向量索引没有在启动时恢复。**

修复方式不是调 query，而是增加启动 warmup：

```text
应用启动
-> 找到 status = INDEXED 的研究文档
-> 读取 document_chunk
-> 重建 LocalVectorSearchPort 内存索引
```

修复后的启动日志显示：

```text
Warmed local vector index from 2 indexed document(s), 160 chunk(s)
```

再次测试后，数据变成：

```text
session 9:
retrieval calls: 8
zero-hit calls: 0
vector hits: 90
keyword hits: 10
metadata hits: 1
returned chunks: 70
avg returned chunks: 8.75
```

这说明最初的大量 `0 scoped chunk(s)` 已经明显收敛。

## 7. 第二次定位：检索恢复了，但证据边界误判

向量索引恢复后，检索层已经有稳定命中。但新的数据暴露了第二层问题：

```text
answer_mode = REFUSAL
evidence_state = NONE
evidence_source = 10 条 paper evidence
```

这明显不一致：系统明明落了 paper evidence，却把回答标成无证据拒答。

继续追查发现，`EvidenceBoundaryService` 当时使用绝对分数阈值：

```text
topScore >= 0.75 -> SUFFICIENT
topScore >= 0.35 -> WEAK
else -> NONE
```

但本地 deterministic embedding 的分数普遍偏低，大约在 `0.17 - 0.23`。所以即使有多个相关 chunk，也会因为 top score 不到 0.35 被判成 `NONE`。

这次修复的原则是：

**空结果才是无证据；有 scoped paper chunk 但分数较低，应该是弱证据，而不是无证据。**

修复后规则变为：

```text
chunks.isEmpty() -> NONE
topScore >= 0.75 -> SUFFICIENT
otherwise -> WEAK
```

再次测试后，最新回答状态变成：

```text
answer_mode = WEB_SUPPLEMENT
evidence_state = WEAK
paper evidence count = 10
```

这说明证据链闭环了：检索有命中，证据有落库，回答状态也不再误判为拒答。

## 8. 第三次优化：限制 Agent 过度调用 paper_rag

检索和证据边界稳定后，新的优化点变成了 Agent 行为：

- 有时同一轮回答会多次调用 `paper_rag`。
- 有些 query 只是轻微改写，实际检索价值相近。
- 大量工具事件会污染 UI，也增加后端成本。

这里没有选择只靠 prompt 约束，因为 prompt 是软约束，LLM 仍可能重复调用工具。

我们把约束下沉到工具边界 `ProjectAgentTools.paperRag(...)`：

```text
同一轮回答内：
- 相同 normalized query 只调用一次后端，后续复用 payload
- 最多允许 3 次后端 paper_rag 调用
- 超出后返回 skipped=true, reason=paper_rag_budget_exhausted
```

这让限制变成确定性行为，而不是模型建议。

返回结果示例：

```json
{
  "query": "query four",
  "skipped": true,
  "reason": "paper_rag_budget_exhausted",
  "message": "paper_rag call budget for this answer has already been used.",
  "chunks": []
}
```

这个优化的本质不是“少查一点”，而是把 Agent 的探索行为纳入可控预算，让系统具备长期运行的稳定性。

## 9. 为什么这套过程适合作为长期工程能力

这次问题如果只当成一次 bug 修复，可能会停在：

```text
重启后向量索引没恢复，补个 warmup。
```

但真正有价值的是形成了一套可复用方法：

```text
异常暴露
-> 建观测模型
-> 聚合真实数据
-> 定位根因
-> 最小修复
-> 回归验证
-> 继续暴露下一层问题
-> 继续优化
```

这套方法适合所有复杂 RAG / Agent 系统，因为这类系统的问题通常不是单点错误，而是链路里多个弱点叠加：

- 检索链路是否真的命中？
- 命中是否属于当前项目 scope？
- 命中是否进入最终引用？
- 引用是否影响回答模式？
- Agent 是否过度调用工具？
- UI 展示的是最终摘要，还是完整诊断？

如果没有长期可观测能力，每次都要重新猜。

## 10. 面试或分享时可以这样表达

可以把这段经历总结为：

> 我们在构建智能研究助手时，前端研究过程面板暴露出大量 `0 scoped chunk(s)`。我没有直接调 prompt 或 query，而是先把 Paper RAG 链路做成长期可观测能力，记录 query rewrite、keyword/vector/metadata 命中、scope 过滤、rerank 和最终 citation 的结构化数据。随后基于真实 retrieval observation 定位到两个问题：本地内存向量索引重启后没有 warmup，以及证据边界只看绝对 top score 导致低分但有效的 paper evidence 被误判为无证据。修复后，zero-hit 从 25 次调用中的 16 次下降到 0，vector hits 从 0 恢复到 90，并进一步在工具边界加入 query 去重和调用预算，防止 Agent 过度检索。

这段表达的重点是：

- 不是“我修了一个 bug”。
- 而是“我把不可解释的 Agent/RAG 行为转化成可观测、可诊断、可持续优化的工程系统”。

## 11. 关键经验

### 10.1 不要把 UI 暴露的问题误认为 UI 问题

UI 显示 `0 scoped chunk(s)`，但根因在后端索引生命周期和 evidence boundary。前端只是把系统内部问题照出来了。

### 10.2 RAG 的 zero-hit 必须分类

没有分类的 zero-hit 只能制造焦虑，不能指导修复。

### 10.3 检索分数不能脱离 embedding 实现讨论

本地 deterministic embedding、真实 embedding、pgvector rerank 的分数分布可能完全不同。证据边界不能机械套一个绝对阈值。

### 10.4 Agent 约束应该尽量落到工具边界

Prompt 可以指导模型，但预算、去重、scope、安全边界应该由代码保证。

### 10.5 长期观测比一次性日志更有价值

一次性日志只能解决当前问题；结构化 observation 可以支持后续策略优化、UI 摘要、面试展示和团队协作。

## 12. 后续可以继续演进的方向

当前系统已经能解释大部分检索问题，但还可以继续增强：

- 让 final `evidence_source` 反向关联具体 retrieval observation。
- 明确区分 vector pre-scope hits 和 post-scope hits。
- 对多条弱证据做聚合判断，支持从 `WEAK` 晋升到更强的本地证据状态。
- 增加只读诊断 endpoint，按 run/session 查看 retrieval observation。
- 在 UI 中默认展示摘要，展开后再看完整检索细节。
- 基于历史 observation 自动发现低效 query 模式。

这些优化都应该建立在同一个原则上：

**先让系统解释自己，再让系统变得更聪明。**
