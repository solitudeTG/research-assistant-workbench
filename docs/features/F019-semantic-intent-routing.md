---
id: F019
doc_kind: feature
status: completed
owner: codex
created: 2026-05-15
updated: 2026-05-15
parent_feature: F018
---
# F019 Semantic Intent Routing

## 目标

F019 将 F018 的 `REACT` / `PLAN_EXECUTE` 模式选择从规则主导升级为语义意图主导，避免继续通过关键词列表修补“什么时候触发多 Agent”。

核心目标不是让模型随意接管所有路由，而是在复杂、模糊、自然语言表达多样的项目消息中，让 `Supervisor` 可以基于语义判断是否需要多 Agent 工作流，同时保留可测试、可恢复的确定性兜底。

## 范围

- 范围内：新增语义意图识别边界，用于推荐 `REACT` 或 `PLAN_EXECUTE`。
- 范围内：保留 deterministic decider 作为 fallback 和安全网，而不是继续扩写关键词规则。
- 范围内：语义识别输出结构化原因和子 Agent 需求：Deep Research、Evidence Audit、Document Composer。
- 范围内：模型失败、JSON 非法、低置信度或结构不一致时回退到原 deterministic decision。
- 范围内：trace 展示 semantic routing/fallback reason，方便前端研究过程说明为什么触发多 Agent。
- 范围外：不改变 F018 的三个可见子 Agent，不引入并行 runtime，不重写 RAG/web/memory 工具选择，不做动态 skill marketplace。

## Vision Anchor

F018 多轮验证暴露出一个原始痛点：过度依赖规则会导致 AI 项目越修越脆，尤其是“是否触发多 Agent”这种语义边界。F019 的验收重点是把触发判断还给语义识别，同时用 Harness、测试、trace 和 fallback 控制失控风险。

## 验收标准

- 复杂研究/报告请求即使没有命中旧关键词，也能通过语义识别进入 `PLAN_EXECUTE`。
- 简单说明、问候、局部论文问题仍可保持 `REACT`，不制造假子 Agent。
- 模型不可用或返回非法 JSON 时，系统回退到 deterministic decision。
- trace 的 `mode-selection` payload 能说明 `semantic` / `fallback` 决策来源、fallback reason 和 confidence。
- 前端研究过程能展示模式决策，并保留 F018 既有 Plan-Execute 子 Agent 展示。

## 链接

- Spec: [F019-semantic-intent-routing-spec.md](../specs/F019-semantic-intent-routing-spec.md)
- Plan: [F019-semantic-intent-routing-plan.md](../plans/F019-semantic-intent-routing-plan.md)
- Evidence: [EV-018-f019-semantic-intent-routing.md](../evidence/EV-018-f019-semantic-intent-routing.md)
- Parent Feature: [F018-multi-agent-evidence-grounded-workflow.md](F018-multi-agent-evidence-grounded-workflow.md)
- Related ADR: [ADR-005-supervisor-led-serial-multi-agent-workflow.md](../decisions/ADR-005-supervisor-led-serial-multi-agent-workflow.md)
- Related Lesson: [LL-002-avoid-rule-accumulation-for-ai-evidence-gates.md](../lessons/LL-002-avoid-rule-accumulation-for-ai-evidence-gates.md)

## 当前状态

2026-05-15: F019 completed. Start Gate classified this as high-risk behavior-boundary work because it changes Supervisor routing. Delegation Gate authorized two read-only exploration subagents for backend and frontend/document discovery; implementation remains Supervisor-led and serial at runtime. Semantic routing now chooses `REACT` vs `PLAN_EXECUTE` through a model-backed structured advisor, while deterministic routing remains the fallback guardrail.
