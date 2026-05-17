---
id: F016
doc_kind: feature
status: completed
owner: codex
created: 2026-05-12
updated: 2026-05-17
parent_feature: F002
---
# Retrieval Observability

## 2026-05-17 Closeout

F016 is closed as an interview-demo-ready retrieval observability capability.

Completed capability:

- `paper_rag` now records structured retrieval observations for query rewrite strategy, retrieval queries, per-backend stats, returned scoped chunks, and zero-hit reason classification.
- Vector retrieval exposes pre-scope and post-scope counts, including `SCOPE_FILTERED_EMPTY` when global vector candidates are filtered out by project scope.
- The Project Agent tool boundary deduplicates repeated `paper_rag` calls and enforces a bounded per-answer backend call budget.
- Retrieval diagnostics are queryable through `GET /api/projects/{projectId}/sessions/{sessionId}/retrieval-diagnostics`.
- The workbench has an Observability / answer-evidence diagnostics workspace for summary metrics, zero-hit taxonomy, retrieval steps, answer-run grouping, and drill-down details.
- Final paper evidence metadata carries `answerId`, `runId`, `origin`, `tool`, `documentId`, `chunkId`, and `chunkIndex`, while `evidence_source.source_id` remains the normalized source identifier.

Known follow-ups that do not reopen F016:

- No dedicated `retrieval_observation` table or observation id was added; exact citation-to-retrieval-call linkage remains a future storage/analytics decision.
- Keyword and metadata retrieval still apply scope internally, so only vector retrieval currently exposes true pre/post scope diagnostics.
- Diagnostics are scoped to the current project session and answer runs, not cross-session trend analytics.

Primary evidence: [EV-015-f016-retrieval-observability-slice.md](../evidence/EV-015-f016-retrieval-observability-slice.md).

## 目标

F016 将 RAG 检索从“回答链路里的黑盒工具调用”升级为长期可观察的工程能力。它要让系统能够解释一次回答中为什么出现大量 `0 scoped chunk(s)`、query rewrite 是否有效、keyword/vector/metadata 三路召回是否失衡、scope 过滤是否吞掉了候选、最终 citation 与中间检索命中之间是什么关系。

这个 Feature 的核心价值不是多打一批日志，而是为 RAG 质量诊断、Agent 工具调度约束、后续 query 策略优化和面试展示建立可复用的诊断底座。

## 范围

- 范围内：记录每次 `paper_rag` 工具调用的原始 query、bounded `maxResults`、scope 状态和最终返回 chunk 数。
- 范围内：记录 `QueryRewriteService` 产出的 retrieval queries 与 keywords，并能区分原始 query、英文改写、关键词组合。
- 范围内：记录 keyword、vector、metadata 三路检索的命中数、候选数、耗时和 top hit 摘要。
- 范围内：记录 scope 过滤前后数量，显式分类 zero-hit 原因。
- 范围内：记录 rerank 后返回的 chunk 与最终 answer citation 的对应关系。
- 范围内：通过结构化 run event 或 retrieval trace 暴露可聚合摘要，支持前端展示“摘要 + 异常 + 可展开完整 trace”。
- 范围内：为后续 query 去重、空结果熔断、scope-aware vector search 和检索策略调优提供数据基础。
- 范围外：本 Feature 不直接重写 RAG 算法，不直接优化 query prompt，不直接改 UI 展示层。
- 范围外：不把 Redis Stream 当作长期审计数据库。
- 范围外：不建设通用 APM、日志平台或全链路观测系统。
- 范围外：不把所有低层 trace 默认塞进对话主界面。

## 验收标准

- 对一次项目回答，后端能够查询或回放本轮每次 `paper_rag` 的结构化 retrieval observation。
- observation 能回答：Agent 调用了几次 `paper_rag`、每次内部生成了几个 rewritten query、三路检索各自命中多少、scope 前后损失多少、最终返回多少 chunk。
- `0 scoped chunk(s)` 不再只是字符串摘要，必须能分类为至少一种原因：`NO_SCOPED_EVIDENCE`、`QUERY_EMPTY_OR_INVALID`、`NO_BACKEND_HITS`、`SCOPE_FILTERED_EMPTY`、`RERANK_EMPTY`、`TOOL_ERROR`。
- 向量检索必须暴露 pre-scope 和 post-scope 数量，避免把“全局有命中但当前项目 scope 为空”误判成“资料库完全无命中”。
- 每个 observation 必须关联 `projectId`、`sessionId`、`runId`、`messageId`、`answerId` 和工具调用序号。
- 每个 observation 必须能关联到最终 `evidence_source` 或 citation，至少能判断“过程命中但未引用”和“最终引用来自哪次检索”。
- SSE/前端消费层可以拿到聚合摘要，但主 UI 默认只展示摘要和关键异常，不平铺全部低层事件。
- focused backend tests 覆盖 zero-hit 分类、scope 过滤统计、rewrite query 统计和 observation 持久化/事件输出。
- Harness validation 通过。

## 合同

- API：优先复用现有项目 run/message 查询边界；如需要诊断端点，必须是项目内受限的 read-only endpoint，例如 `GET /api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/retrieval-observations`。
- 事件：扩展 F015 trace 合同，新增或增强 `retrieval.query.rewritten`、`retrieval.completed`、`tool.completed` 的结构化 data，不再只依赖 `resultSummary` 文案。
- 数据：可以先扩展 `retrieval_trace` 的 JSON 内容；若需要可聚合查询，再在实现计划中引入专用 `retrieval_observation` 表。实现前若选择新表，应补 ADR。
- UI：F016 只定义展示原则。默认对话 UI 展示阶段摘要、有效命中、异常和 zero-hit 统计；完整工具明细进入展开层或诊断视图。

## 设计原则

- 观测数据要面向诊断问题，而不是面向日志堆积。
- 每个 zero-hit 都必须可解释；解释不出来的 zero-hit 说明观测模型还不够。
- 长期持久化只保存有诊断价值的结构化字段和 bounded 摘要，不保存无限制全文。
- 运行期事件服务 UI，持久化 trace 服务复盘、统计和策略优化。
- 主回答链路不能因为观测写入失败而失败；观测失败应降级为 warning，并保留核心回答能力。
- 不因为要面试展示而夸大系统能力：没有真实并发 subAgent 时，诊断里仍称为工具调用或逻辑步骤。

## 面试表达点

- “我为 RAG 链路设计了长期可观察性，能定位低召回、scope 过滤、query rewrite 失效和 Agent 过度调用。”
- “系统不只记录最终答案，还记录 query -> rewrite -> hybrid retrieval -> scope filter -> rerank -> citation 的证据链。”
- “大量 0 命中不会停留在 UI 噪音，而会被归因到可行动的后端原因分类。”

## 入口判断

Start Gate: `needs feature`。

- Task class: high-risk。
- Risk trigger: 该能力会影响事件契约、检索诊断模型、可能的数据结构和未来 UI 展示边界。
- Required pre-work: 本 Feature、linked spec、linked plan 和 Backlog 状态。
- ADR trigger: 若实现时新增长期聚合表、改变事件合同兼容性或调整 vector scope 检索策略，应补 ADR。

## 链接

- Spec: [F016-retrieval-observability-spec.md](../specs/F016-retrieval-observability-spec.md)
- Plan: [F016-retrieval-observability-plan.md](../plans/F016-retrieval-observability-plan.md)
- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Related Feature: [F015-agent-trace-live-sse.md](F015-agent-trace-live-sse.md)

## 下一步

继续按计划补 scope-aware vector 统计和最终 citation 关联。当前第一切片已落地最小 observation 模型、rewrite strategy、`NO_BACKEND_HITS` 分类和结构化 trace events；证据见 [EV-015-f016-retrieval-observability-slice.md](../evidence/EV-015-f016-retrieval-observability-slice.md)。
