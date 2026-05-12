---
id: VG-001
doc_kind: review
status: completed
date: 2026-05-09
review_type: vision_gate
mode: entry
feature_ids: [F002]
---
# F002 开发前 Vision Gate

## 结论

Vision Gate: needs follow-up

## 原始意图

- 项目下一阶段目标是“项目级长期研究工作台”，不是继续修补当前单文档问答 Demo。
- 核心能力必须真实落地：多 Agent 协作、Redis Stream 到 SSE 的过程渲染、资料接入状态机、三层记忆、混合检索、证据边界、feedback 自学习闭环、候选确认与知识板沉淀。
- 当前主要风险不是代码不足，而是需求边界、写入边界、验收证据和恢复上下文失控。

## 对齐情况

- F002 Feature 已定义总目标、范围、验收标准和合同入口。
- F002 正式规格已覆盖产品边界、数据模型、API 合同、Redis Stream 事件合同、SSE 前端事件合同、Agent 写入边界、前端状态模型和验收证据。
- F002 实施计划已把总目标拆成数据模型、事件骨干、资料状态机、Agent 过程事件、检索与证据边界、候选与知识板、feedbackScore、三栏前端和 Evidence 收尾等实现切片。

## 漂移风险

- 如果直接把 F002 当作唯一实现 Feature 开写，9 个切片会共享同一个验收边界，后续 Evidence、回滚和并行协作都会变重。
- 如果不先提交 Harness 基线，后续业务代码 diff 会和规格、计划、ADR、Evidence、校验脚本混在一起，影响评审和恢复。
- 如果实现继续沿用旧 `sessionKey + documentId` 作为中心，产品方向会回到单文档 Demo，而不是项目级研究工作台。

## 用户痛点

- 用户需要的是可追问、可展示、可验证的研究工作台项目，不是看起来功能很多但无法证明边界和可靠性的生成代码。
- 用户明确要求 Harness 优先于代码扩张；因此进入开发前必须把大 Feature 拆成更小的可验收 Feature。

## AC 漂移检查

- 未发现 F002 规格偏离原始目标。
- 发现实施粒度风险：F002 作为总 Feature 合理，但不适合作为唯一代码交付边界。
- F002 后续实现应拆成 F003+ 子 Feature，每个子 Feature 有独立验收标准、写入边界和 Evidence。

## UI/视觉对齐

- F002 继续采用三栏研究工作台方向：左侧项目/会话/资料，中间多轮研究对话，右侧知识板/证据来源/候选确认。
- 开发阶段不得退回单文档上传 + 单回答页面。

## 必需下一步

1. 将 F002 定位为 Epic / 总规格 / 总路线图。
2. 创建 F003+ 子 Feature，用于承载可独立实现和验收的代码切片。
3. 在进入业务代码前，先提交当前 Harness 基线。
4. 首个实现切片建议从 F003 项目级数据模型与 API 开始。

## 相关链接

- Feature：[F002-next-generation-research-workbench.md](../features/F002-next-generation-research-workbench.md)
- 规格：[F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- 计划：[F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- ADR：[ADR-002-rebuild-around-project-workbench.md](../decisions/ADR-002-rebuild-around-project-workbench.md)
