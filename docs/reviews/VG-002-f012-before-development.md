---
id: VG-002
doc_kind: review
status: completed
date: 2026-05-10
review_type: vision_gate
mode: entry
feature_ids: [F012]
---
# F012 开发前 Vision Gate

## 结论

Vision Gate: ready to implement

## 原始意图

- 用户希望联网查询使用 Tavily。
- 用户希望聊天体验接近主流 Agent 产品：主 Agent 默认接收用户输入和 L1/L2 记忆，再判断是否需要 RAG、联网查询、记忆召回或子 Agent 委派。
- 用户明确指出“你好”被回答成 RAG 失败话术很呆，核心痛点是路由错误，而不是单纯补一个搜索 API。

## 对齐情况

- F012 将“简单对话不进 RAG”列为首个验收标准。
- F012 将 Tavily 设计为主 Agent 可主动调用的工具，而不是 Paper RAG 失败后的固定兜底。
- F012 保持 F002/F007 的证据边界：记忆、Paper RAG、联网补充分离。

## 漂移风险

- 如果直接实现 Tavily 调用而不先做 Intent Router，会保留“所有问题先 RAG”的根因。
- 如果过早实现多子 Agent，会把一个可闭环的路由和工具接入切片膨胀成平台工程。
- 如果把联网结果写入长期记忆或知识板，会破坏“用户确认后才沉淀”的边界。

## 用户痛点

- 用户要的是自然的 Agent 聊天体验，而不是每轮都被研究证据系统审问。
- 用户需要在显式要求或主 Agent 判断需要时联网，而不是只能在 RAG 失败后联网。

## AC 漂移检查

- 当前验收标准覆盖简单对话、显式联网、本地研究、混合查询、Tavily 降级、证据分离和 Harness 检查。
- 未把多 Agent 委派作为本切片验收项，符合“先稳定主 Agent 工具调用”的最小路径。

## UI/视觉对齐

- 本切片不重做三栏 UI。
- 必要 UI 变化只限于现有回答和证据区域能表达“联网补充”和来源差异。

## 必需下一步

1. 先按 TDD 实现 `AgentIntentRouter`。
2. 再接入 Tavily `WebSearchPort`。
3. 最后改造 `SupervisorService.answerProject(...)` 的主 Agent 编排路径。

## 相关链接

- Feature: [F012-agentic-routing-and-tavily-web-search.md](../features/F012-agentic-routing-and-tavily-web-search.md)
- Spec: [F012-agentic-routing-and-tavily-web-search-spec.md](../specs/F012-agentic-routing-and-tavily-web-search-spec.md)
- Plan: [F012-agentic-routing-and-tavily-web-search-plan.md](../plans/F012-agentic-routing-and-tavily-web-search-plan.md)
- ADR: [ADR-003-agentic-tool-routing-and-tavily.md](../decisions/ADR-003-agentic-tool-routing-and-tavily.md)
