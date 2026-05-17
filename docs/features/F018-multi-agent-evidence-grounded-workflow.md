---
id: F018
doc_kind: feature
status: active
owner: codex
created: 2026-05-13
updated: 2026-05-13
parent_feature: F002
---
# Multi-Agent Evidence-Grounded Workflow

## 目标

F018 将当前项目级主 Agent tool-calling loop 升级为可解释、可审计、按需触发的多 Agent 工作流。

核心目标不是“凑出多个 Agent 名字”，而是在复杂研究任务中把高风险职责拆开：

- 主 Agent 继续作为 `Supervisor`，负责理解用户目标、选择执行模式、分派子 Agent、合成最终回答。
- 简单任务继续走轻量 `ReAct`，由主 Agent 直接调用 `paper_rag`、`web_search`、`memory_recall` 并回答。
- 复杂研究任务升级到 `Plan-Execute`，按需调用 `Deep Research Agent`、`Evidence Audit Agent` 和 `Document Composer Agent`。
- 前端研究过程 UI 必须展示真实后端 trace events，不能展示后端没有真实发出的 Agent、步骤或并行关系。

## 范围

- 范围内：主 Agent 支持 `ReAct` 与 `Plan-Execute` 两种执行模式。
- 范围内：主 Agent 保留基础 research 工具调用能力，简单问题不强制委派子 Agent。
- 范围内：新增按需触发的 `Deep Research Agent`，负责复杂、多来源、多轮检索，输出结构化 `research packet`。
- 范围内：新增按需触发的 `Evidence Audit Agent`，负责审查 claim-source 对齐、证据等级、来源混用和越界结论，输出 `audit verdict`。
- 范围内：新增按需触发的 `Document Composer Agent`，仅在用户明确要求报告、综述、表格、论文笔记、Markdown 等文档型输出时，将已审查内容转成指定格式。
- 范围内：扩展 Agent Trace / SSE 合同，让 UI 能展示模式选择、计划、子 Agent lifecycle、工具调用、审查结论和文档生成阶段。
- 范围外：第一期不做真正并行调度、跨进程 worker、长任务队列、人类审批 checkpoint、动态 skill marketplace 或完整 agent registry。
- 范围外：`Document Composer Agent` 默认不能自行查新资料，也不能绕过 `Evidence Audit Agent` 改变证据结论。

## Agent 边界

| Agent | 触发 | 工具权限 | 输出 |
| --- | --- | --- | --- |
| `Supervisor` | 所有项目消息 | 轻量 `paper_rag`、`web_search`、`memory_recall`；子 Agent 调用边界 | mode decision、plan、final answer |
| `Deep Research Agent` | 多资料、多子问题、综述、对比、研究路线、需要多轮 query rewrite | `paper_rag`、`web_search`、`memory_recall` | `research packet` |
| `Evidence Audit Agent` | 高风险结论、证据混用、弱证据但用户期待判断、Deep Research 后 | 默认只读 `research packet` 和 draft；可选极少量 verification | `audit verdict` |
| `Document Composer Agent` | 用户明确要求文档型产物 | approved `research packet`、`audit verdict`、formatter/template | document draft |

## 执行模式

### ReAct

默认轻量路径。适用于闲聊、简单解释、单一论文局部问题、单次联网查询、轻量记忆召回。

主 Agent 可直接调用当前已有工具并回答。UI 只展示真实主 Agent 和真实工具调用，不伪造子 Agent。

### Plan-Execute

复杂路径。适用于多篇论文对比、综述、研究路线、方案判断、高可信可引用问题、或文档型产物。

主 Agent 先生成计划，再串行调用必要子 Agent。第一期采用串行流水线，保证 trace 真实、边界清楚、验收可控；并行 worker runtime 留给后续 Feature。

## 验收标准

- 简单闲聊、简单解释、单一资料问题可以只走 `ReAct`，不触发子 Agent。
- 多篇论文对比、综述、研究路线或复杂多来源问题进入 `Plan-Execute`，并触发 `Deep Research Agent`。
- 高可信、可引用、判断是否成立、paper/web/memory 混合来源或证据弱但有明确结论的场景触发 `Evidence Audit Agent`。
- 用户要求报告、综述、对比表、论文笔记、Markdown 文档等格式化产物时，在 research + audit 后触发 `Document Composer Agent`。
- `Deep Research Agent` 输出结构化 `research packet`，至少区分 paper evidence、web evidence、memory context、claims、conflicts 和 evidence gaps。
- `Evidence Audit Agent` 输出结构化 `audit verdict`，至少包含 verdict、unsupported claims、source policy issues、recommended answer mode。
- `Document Composer Agent` 不查新资料，不改变证据结论，只基于 approved packet 和 verdict 生成文档。
- 前端研究过程 UI 能基于真实 trace events 展示 `Supervisor` 模式选择、计划步骤、子 Agent started/completed/failed、工具调用、audit verdict、document composition 阶段。
- 简单 `ReAct` 问题不展示虚假的多 Agent 协作；串行执行不画成并行。
- 现有 F013/F015/F016/F017 行为不能回退：工具调用仍记录真实 `toolsUsed`，证据边界仍区分 paper/web/memory，研究过程 UI 仍只消费 SSE。

## 入口判断

Start Gate: `needs retrieval -> needs feature/spec/plan`。

Vision Gate Entry:

- Original intent: 用户希望做 2-3 个多 Agent，但经过讨论明确不是为了堆角色，而是偏向答案质量稳定性，并兼容前端展示真实多 Agent 协作过程。
- User pain point: 当前主 Agent 对简单问题足够，但复杂研究任务中取证、审证、交付混在一个 loop 里，难以解释为什么答案可信，也难以在 UI 中展示真实协作链路。
- Alignment: F018 只做按需串行多 Agent 工作流，不提前建设完整并行平台；主 Agent 保留基础 research 能力，避免简单问题被架构拖慢。
- Non-goal: 不新增 `Planner Agent`，因为规划与分派属于 `Supervisor`；不新增常驻 `Writer Agent`，文档生成只作为按需 `Document Composer Agent`。
- Exit Gate source: 本 Feature 与 linked spec。

## 链接

- Spec: [F018-multi-agent-evidence-grounded-workflow-spec.md](../specs/F018-multi-agent-evidence-grounded-workflow-spec.md)
- Plan: [F018-multi-agent-evidence-grounded-workflow-plan.md](../plans/F018-multi-agent-evidence-grounded-workflow-plan.md)
- ADR: [ADR-005-supervisor-led-serial-multi-agent-workflow.md](../decisions/ADR-005-supervisor-led-serial-multi-agent-workflow.md)
- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Related Feature: [F013-main-agent-tool-calling-loop.md](F013-main-agent-tool-calling-loop.md)
- Related Feature: [F015-agent-trace-live-sse.md](F015-agent-trace-live-sse.md)
- Related Feature: [F016-retrieval-observability.md](F016-retrieval-observability.md)

## 下一步

按 linked plan 先实现后端最小多 Agent contract 和 focused tests，再扩展 trace projection 与前端研究过程 UI。第一期实现完成前，不得在 UI 中声称并发子 Agent。
