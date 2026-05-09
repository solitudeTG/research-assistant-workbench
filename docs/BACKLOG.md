# 工作看板

本文件只记录后续会话必须能恢复的活跃工程状态，不作为无限愿望清单。

## 活跃工作

### F002 下一代研究工作台

- 状态：Epic 已建立，已拆分 F003-F011 子 Feature
- Feature 页：[F002-next-generation-research-workbench.md](features/F002-next-generation-research-workbench.md)
- 当前意图：先对齐简历表述、UI 方向和功能架构，再进入下一轮实现。
- 下一步：父会话继续调度 F004-F011 后续子 Feature；不要在 F003 worker 中顺手实现 Redis Stream、SSE、Agent 编排、资料状态机、检索、知识板、反馈或 UI。

## 最近完成

### F003 项目级数据模型与基础 API

- 状态：已完成
- Feature 页：[F003-project-workbench-model-api.md](features/F003-project-workbench-model-api.md)
- Evidence：[EV-002-f003-project-workbench-model-api.md](evidence/EV-002-f003-project-workbench-model-api.md)
- 结果：已建立项目级 schema 骨架、`ProjectRepository`、`ProjectController`、仓储测试和控制器测试；`mvn -Dtest=ProjectRepositoryTest,ProjectControllerTest test` 已通过。

### F001 Harness 工程闭环

- 状态：已完成
- Feature 页：[F001-harness-engineering-loop.md](features/F001-harness-engineering-loop.md)
- Evidence：[EV-001-harness-bootstrap.md](evidence/EV-001-harness-bootstrap.md)
- 结果：已建立 BACKLOG、Feature、ADR、Evidence、模板和知识校验脚本。

## 暂存判断

- 只有当旧代码能加速重构或提供可验证参考行为时才保留。
- 对低价值兼容性不做保护，优先替换不稳定表面。
- Markdown Harness 文档是事实源；未来任何索引或摘要都只是编译产物。
- 开发前交接：[2026-05-09-f002-to-f003.md](handoffs/2026-05-09-f002-to-f003.md)
