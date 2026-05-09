# Harness 工作流

项目 Harness 用来让大规模 Agent 写入保持可评审、可恢复、可验证。

## 事实源

- `docs/BACKLOG.md`：活跃工作和恢复上下文。
- `docs/features/`：交付边界和验收标准。
- `docs/decisions/`：长期决策 ADR。
- `docs/evidence/`：完成或验证证据。
- `docs/harness/templates/`：可复制的文档骨架。
- `scripts/knowledge_check.py`：轻量结构校验门。

## 必经闭环

1. 重大实现切片开始前，创建或更新 Feature 页。
2. 当未来 Agent 很可能追问“为什么这样选”时，写 ADR。
3. 派发 Agent 前，先写清实现切片和写入边界。
4. 每个切片完成后，用测试、API 调用、SSE 轨迹、截图或数据库检查验证。
5. 声称完成前，把验证结果写入 Evidence。
6. 交接前运行 `python scripts/knowledge_check.py`。

## Agent 写入边界

下一轮重构建议按互不重叠的边界派发：

- 项目与知识数据模型。
- Redis Stream 事件总线与 SSE 投影。
- Agent 编排与 planning 合同。
- 检索、证据边界与反馈评分。
- 工作台 UI 与浏览器验证。
- 测试、夹具与 Evidence 加固。

Agent 应通过合同文件和 Feature 页汇合，不通过隐含假设汇合。
