---
id: ADR-005
doc_kind: adr
status: accepted
date: 2026-05-13
feature_ids: [F018]
---
# Supervisor-Led Serial Multi-Agent Workflow

## 决策

F018 采用 `Supervisor + on-demand subagents` 的串行多 Agent 工作流，而不是新增独立 `Planner Agent` 或直接建设完整并发 `Supervisor-Worker` runtime。

主 Agent 继续作为 `Supervisor`：负责理解用户目标、选择 `REACT` 或 `PLAN_EXECUTE`、分派子 Agent、合成最终回答。第一期子 Agent 是：

- `Deep Research Agent`
- `Evidence Audit Agent`
- `Document Composer Agent`

第一期串行执行这些子 Agent，并通过真实 trace events 暴露给前端。并发调度、跨进程 worker、长任务队列、人类审批 checkpoint 和动态 skill marketplace 不进入 F018 第一阶段。

## 背景

用户最初希望“做 2-3 个子 Agent”，但并未想好具体角色。讨论后目标收敛为：偏向提升答案质量稳定性，同时兼容前端展示真实多 Agent 协作过程。

现有系统已经有 F013 主 Agent tool-calling loop。主 Agent 能自己判断是否调用 `paper_rag`、`web_search`、`memory_recall`。因此，如果再新增 `Planner Agent`，会把主 Agent 的核心职责重复拆出去，导致“谁负责规划、谁说了算”变得模糊。

现有 F015 trace 合同也明确约束：UI 不得展示后端没有真实发出的 Agent、步骤或并行关系。因此 F018 不能只改 UI 标签，必须让后端产生真实子 Agent lifecycle。

## 备选方案

- 只增强当前主 Agent：成本最低，但不形成真实多 Agent 工作流，复杂任务的取证、审证、交付仍混在一个 loop 中，前端也无法诚实展示协作链路。
- 新增 `Planner Agent`：看起来符合多 Agent 形式，但和 `Supervisor` 职责重叠。规划和分派本来就是主 Agent 的核心职责，重复一层会增加复杂度而不增加不可替代能力。
- 直接建设并发 `Supervisor-Worker` runtime：长期方向成立，但第一期会引入任务队列、并发状态、失败重试、合并策略和 UI 并行语义，成本高于当前目标。
- 采用串行 `Supervisor + on-demand subagents`：保留主 Agent 简单任务能力，只在复杂任务和高风险结论上升级到专业子 Agent，能以最低复杂度得到真实可验收的多 Agent 能力。

## 影响

- 简单问题继续走 `REACT`，不被多 Agent 架构拖慢。
- 复杂研究问题进入 `PLAN_EXECUTE`，让取证、审证、交付成为可测试、可追踪的独立阶段。
- `Document Composer Agent` 只处理文档型输出，不默认参与普通问答。
- `Evidence Audit Agent` 是质量闸门，不是润色器，也不是第二个 Research Agent。
- 前端研究过程 UI 必须从真实 trace events 渲染协作过程，不能把串行步骤画成并行 worker。
- 如果未来要做并行调度，应打开新的 Feature，并复用 F018 建立的子 Agent contract 与 trace contract。
