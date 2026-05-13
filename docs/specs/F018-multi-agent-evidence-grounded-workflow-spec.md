---
id: SPEC-F018
doc_kind: spec
status: accepted
updated: 2026-05-13
feature_ids: [F018]
---
# F018 Multi-Agent Evidence-Grounded Workflow Spec

## 背景

F013 已把项目级聊天从规则优先路由升级为主 Agent tool-calling loop。F015 已建立真实 Agent Trace / live SSE 合同，并明确 UI 不得伪造不存在的并发子 Agent。F016 又暴露出 RAG/Agent 链路需要可观测、可诊断、可控预算。

F018 的问题不是“系统缺少角色名”，而是复杂研究任务中存在三类风险：

- 取证风险：主 Agent 可能 query 太窄、重复查、没有把 paper/web/memory 区分成稳定结构。
- 审证风险：答案可能把 web 补充说成本地论文证据，把 memory 当事实依据，或在弱证据下语气过强。
- 交付风险：用户要报告、综述、表格或论文笔记时，聊天答案和文档产物的格式目标不同。

因此 F018 采用 `Supervisor + on-demand subagents`，让简单任务保持轻量，让复杂任务具备真实可追踪的取证、审证和交付链路。

## Product Boundary

### In Scope

- 在项目消息主路径中增加 execution mode decision：`REACT` 或 `PLAN_EXECUTE`。
- `REACT` 保持当前主 Agent 直接工具调用能力。
- `PLAN_EXECUTE` 生成可追踪计划，并按需调用：
  - `Deep Research Agent`
  - `Evidence Audit Agent`
  - `Document Composer Agent`
- 定义 `ResearchPacket`、`AuditVerdict`、`DocumentDraft` 等结构化中间结果。
- 扩展 trace events，使前端可以展示真实多 Agent 协作过程。
- 增加后端 focused tests 和前端 model tests，证明触发规则、输出合同和 UI projection 可验证。

### Out of Scope

- 不做并发 worker scheduler。
- 不做跨进程子 Agent runtime。
- 不做长任务队列、暂停恢复、人类审批 checkpoint。
- 不做动态 skill marketplace 或用户可配置 agent registry。
- 不全量迁移 Python/LangGraph/AutoGen。
- 不让 `Document Composer Agent` 自行检索新资料。
- 不把 paper、web、memory 合并成同一种证据。

## Architecture

```text
ProjectMessageRequest
  -> SupervisorService
      -> MultiAgentWorkflowDecider
          -> REACT
              -> existing ProjectAgentToolLoop
          -> PLAN_EXECUTE
              -> MultiAgentPlan
              -> DeepResearchAgent.run(...)
                  -> ProjectAgentTools paper_rag/web_search/memory_recall
                  -> ResearchPacket
              -> EvidenceAuditAgent.run(...)
                  -> AuditVerdict
              -> optional DocumentComposerAgent.run(...)
                  -> DocumentDraft
              -> Supervisor final synthesis
      -> persist answer/evidence
      -> publish trace/SSE events
```

`SupervisorService` 继续拥有项目消息生命周期、持久化和事件发布。新增 orchestration 类型必须保持小边界，避免继续膨胀 `SupervisorService`。

## Execution Mode Decision

### REACT

触发信号：

- 闲聊或简单解释。
- 单一事实查询。
- 单一论文局部问题。
- 单次 web search 即可回答。
- 轻量 memory recall。

行为：

- 使用现有 `ProjectAgentToolLoop`。
- 不创建子 Agent plan。
- 可发布 `agent.mode.selected` 或等价 `agent.step.completed` payload，标记 mode=`REACT`。
- UI 只展示主 Agent 和真实工具调用。

### PLAN_EXECUTE

触发信号：

- 多篇论文对比、综述、研究路线、方案判断。
- 用户要求“严谨、可引用、判断是否成立、帮我审查证据”。
- 需要两类以上来源形成明确结论。
- 用户要求报告、综述、对比表、论文笔记、Markdown 等文档产物。
- 主 Agent 判断单轮 ReAct 容易产生取证、审证或交付风险。

行为：

- 生成 `MultiAgentPlan`。
- 按需调用子 Agent，第一期串行执行。
- 每个子 Agent 真实发布 started/completed/failed trace。
- final answer 必须吸收 `AuditVerdict`，不能越过审查结论。

## Subagent Contracts

### Deep Research Agent

输入：

```json
{
  "question": "比较这几篇论文对多 Agent 协作架构的观点",
  "workingMemory": "...",
  "globalKnowledge": "...",
  "evidenceScope": {
    "indexedDocumentIds": [1, 2, 3]
  },
  "allowWebSupplement": true,
  "constraints": {
    "maxToolRounds": 4,
    "maxResultsPerTool": 5
  }
}
```

输出：

```json
{
  "claims": [
    {
      "id": "claim_1",
      "text": "Supervisor-Worker 可以提升复杂研究任务的可追踪性",
      "supportingSourceIds": ["paper:1#3", "web:https://example.com"],
      "confidence": "medium"
    }
  ],
  "paperEvidence": [],
  "webEvidence": [],
  "memoryContext": [],
  "conflicts": [],
  "evidenceGaps": [
    "当前资料不足以证明并发调度收益"
  ],
  "recommendedAnswerMode": "LOCAL_WEAK_EVIDENCE"
}
```

规则：

- 可以调用 `paper_rag`、`web_search`、`memory_recall`。
- 必须区分 paper evidence、web evidence、memory context。
- 不直接生成最终用户答案。
- 不写入知识库或长期记忆。
- 必须记录工具使用和 query rewrite / retrieval observation。

### Evidence Audit Agent

输入：

```json
{
  "question": "...",
  "draftAnswer": "...",
  "researchPacket": {},
  "sourcePolicy": {
    "memoryIsContextOnly": true,
    "webIsSupplementOnly": true,
    "paperEvidenceRequiredForLocalClaims": true
  }
}
```

输出：

```json
{
  "verdict": "pass_with_cautions",
  "recommendedAnswerMode": "LOCAL_WEAK_EVIDENCE",
  "unsupportedClaims": [],
  "sourcePolicyIssues": [
    "web evidence may supplement but must not be described as local paper evidence"
  ],
  "requiredRevisions": [
    "把确定性语气降级为弱证据表述"
  ]
}
```

规则：

- 默认只读 `ResearchPacket` 和 draft answer。
- 不负责润色。
- 不做新检索；如实现受控复核工具，第一期最多 1-2 次，并必须在 trace 中标记为 audit verification。
- `Supervisor` final answer 必须遵守 audit verdict。

### Document Composer Agent

输入：

```json
{
  "requestedFormat": "markdown_report",
  "question": "...",
  "approvedResearchPacket": {},
  "auditVerdict": {},
  "formatConstraints": {
    "includeCitationTable": true,
    "language": "zh-CN"
  }
}
```

输出：

```json
{
  "format": "markdown_report",
  "title": "多 Agent 研究流程综述",
  "body": "...",
  "sections": ["摘要", "证据", "风险", "结论"]
}
```

规则：

- 只处理文档型输出。
- 默认不能调用 `paper_rag`、`web_search`、`memory_recall`。
- 不能新增 claim。
- 不能把 audit 标记为 unsupported 的内容写成结论。

## Trace Contract

F018 可复用 `WorkbenchEvent` envelope。新增事件类型可以少量增加，也可以先复用 `agent.step.*` 并扩展 payload。第一期推荐至少稳定以下语义：

```json
{
  "eventType": "agent.mode.selected",
  "payload": {
    "actor": {
      "agentRole": "supervisor",
      "displayName": "Supervisor"
    },
    "data": {
      "mode": "PLAN_EXECUTE",
      "reason": "multi-source research request"
    }
  }
}
```

```json
{
  "eventType": "agent.step.started",
  "payload": {
    "actor": {
      "agentRole": "deep_research_agent",
      "displayName": "Deep Research Agent"
    },
    "step": {
      "stepId": "step_deep_research",
      "parentStepId": "step_plan",
      "label": "深度研究",
      "status": "running"
    }
  }
}
```

```json
{
  "eventType": "agent.step.completed",
  "payload": {
    "actor": {
      "agentRole": "evidence_audit_agent",
      "displayName": "Evidence Audit Agent"
    },
    "data": {
      "verdict": "pass_with_cautions",
      "recommendedAnswerMode": "LOCAL_WEAK_EVIDENCE"
    }
  }
}
```

UI rules:

- `REACT` shows only Supervisor and actual tools.
- `PLAN_EXECUTE` shows plan and actual subagent lifecycle.
- Serial execution must be rendered as ordered steps, not parallel lanes.
- Failed/degraded audit or composer steps must be visible.
- UI must not infer subagents from old logical labels such as `retrieval_worker`.

## Persistence and Evidence

- Final answer persistence remains owned by `SupervisorService`.
- Existing `evidence_source` remains the source of final citation rows.
- `ResearchPacket` and `AuditVerdict` may initially be transient and trace-visible only.
- If persistence of intermediate packets becomes necessary, open a follow-up Feature or later F018 slice; do not silently add long-term audit storage in the first backend slice.

## Acceptance Evidence

- Backend tests prove `REACT` path does not call subagents for simple questions.
- Backend tests prove complex multi-source/document requests select `PLAN_EXECUTE`.
- Backend tests prove `Deep Research Agent` returns structured packets and separates paper/web/memory.
- Backend tests prove `Evidence Audit Agent` can force downgrade or revision when claims are unsupported.
- Backend tests prove `Document Composer Agent` triggers only for document-format requests and cannot call retrieval tools.
- Event tests prove mode selection, plan, subagent lifecycle, audit verdict, and composer completion are emitted as trace events.
- Frontend model tests prove research process UI can render real multi-agent traces and does not show fake subagents for `REACT`.
- `python scripts/knowledge_check.py` passes for updated Harness docs.
