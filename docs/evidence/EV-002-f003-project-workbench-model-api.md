---
id: EV-002
status: captured
date: 2026-05-09
feature_ids: [F003]
---
# F003 项目级数据模型与基础 API 证据

## 证据

- 命令：`mvn -Dtest=ProjectRepositoryTest test`
- 结果：红灯，测试编译失败于缺少 `ProjectRepository`、`ProjectRecord`、`ResearchSessionRecord`，确认仓储测试先于实现约束新接口。
- 覆盖范围：TDD 红灯；使用父会话已创建的 `ProjectRepositoryTest`。

- 命令：`mvn -Dtest=ProjectRepositoryTest test`
- 结果：第一次绿灯尝试失败于 Testcontainers 找不到 Docker daemon；启动 Docker Desktop 后重跑通过，`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖范围：Flyway V5 迁移可应用；`ProjectRepository` 可创建项目和项目下会话；项目 A 的会话列表不包含项目 B 的会话。

- 命令：`mvn -Dtest=ProjectControllerTest test`
- 结果：红灯，`POST /api/projects` 返回 404，确认控制器尚未实现。
- 覆盖范围：TDD 红灯；API 合同测试覆盖项目与会话端点。

- 命令：`mvn '-Dtest=ProjectRepositoryTest,ProjectControllerTest' test`
- 结果：通过，`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖范围：`POST /api/projects`、`GET /api/projects`、`GET /api/projects/{projectId}`、`POST /api/projects/{projectId}/sessions`、`GET /api/projects/{projectId}/sessions`；JSON 字段包含 F002 规格要求的 Project 与 ResearchSession 字段。

- 命令：`python scripts\knowledge_check.py`
- 结果：通过，`knowledge_check: ok`。
- 覆盖范围：Harness Feature、ADR、Evidence 结构校验。

### 质量审查后补强项目边界约束

- 命令：`C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd "-Dtest=ProjectRepositoryTest" test`
- 结果：红灯，新增 `rejectsCrossProjectRelationshipsAtDatabaseBoundary` 后失败于 `Expecting code to raise a throwable`，确认当前 V5 schema 允许 `assistant_answer(project_id=A, session_id=B项目session)` 跨项目错挂。
- 覆盖范围：TDD 红灯；证明项目级 API 过滤前，数据库层缺少复合项目边界约束。

- 命令：`C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd "-Dtest=ProjectRepositoryTest" test`
- 结果：通过，`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖范围：补强后数据库拒绝 `assistant_answer(project_id=A, session_id=B项目session)`、`evidence_source(project_id=A, answer_id=A项目answer, source_id=B项目source)`、`answer_feedback(project_id=B, answer_id=A项目answer)`。

- 命令：`C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd "-Dtest=ProjectRepositoryTest,ProjectControllerTest" test`
- 结果：通过，`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖范围：F003 仓储与控制器测试仍通过；新增项目边界复合约束未破坏项目/会话基础 API。

- 命令：`python scripts\knowledge_check.py`
- 结果：通过，`knowledge_check: ok`。
- 覆盖范围：Harness Feature、ADR、Evidence 结构校验。

### 复审反馈后补齐候选与知识条目边界覆盖

- 命令：`C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd "-Dtest=ProjectRepositoryTest,ProjectControllerTest" test`
- 结果：通过，`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。补覆盖测试直接通过，因为上一轮 schema 修复已关闭该路径。
- 覆盖范围：补齐数据库层拒绝 `knowledge_candidate(project_id=A, session_id=B项目session)`、`knowledge_candidate(project_id=A, answer_id=B项目answer)`、`knowledge_candidate(project_id=A, source_id=B项目source)`、`knowledge_entry(project_id=A, candidate_id=B项目candidate)` 跨项目错挂。

- 命令：`python scripts\knowledge_check.py`
- 结果：通过，`knowledge_check: ok`。
- 覆盖范围：复审反馈后的 Evidence 与 Feature 链接结构仍通过 Harness 校验。

## 备注

- PATH 中没有 `mvn`，本次使用本机已缓存 Maven：`C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd`。
- 质量审查修复 worker 使用父会话提供的缓存 Maven：`C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd`。
- 未实现 Redis Stream、SSE、Agent 编排、资料状态机、检索、知识板业务逻辑、反馈逻辑或 UI。
- 未围绕旧 `sessionKey + documentId` 扩张；旧表仅作为既有系统保留，F003 新增项目级表与 API。
