---
id: ARCHIVE-SPEC-2026-04-17-DOCKER-COMPOSE-STARTUP-DESIGN
doc_kind: spec
status: archived
archived: 2026-05-10
feature_ids: []
---
# Docker Compose Startup Design

**Goal**

把当前“Docker 只负责 PostgreSQL、本机 Maven 负责 Spring Boot”的混合启动方式，改成可通过 `docker compose up --build -d` 一条命令拉起 `db + app` 的纯 Docker 方案。

**Design**

- 使用 `compose.yaml` 同时编排 `db` 与 `app` 两个服务。
- `db` 继续使用 `pgvector/pgvector:pg17`，保留现有 healthcheck。
- 新增 `app` 服务，基于项目根目录 `Dockerfile` 构建，启动前依赖 `db` 健康。
- `app` 运行时通过环境变量连接 `db:5432`，并把上传文件落到容器内 `/app/storage`，再通过命名卷持久化。
- 新增 `.dockerignore`，避免把 `target/.m2/docs/storage` 之类无关内容送进 Docker build context。
- `start.ps1/start.cmd` 保留，但职责改成“检查 Docker 与关键环境变量，然后调用 `docker compose up --build -d`”。

**Operational Notes**

- 首次启动需要联网拉取基础镜像并在构建阶段下载 Maven 依赖。
- Docker 镜像构建阶段跳过测试，避免在容器内执行依赖 Docker/Testcontainers 的测试。
- 应用停止命令统一为 `docker compose down`，若需要一并清理数据库卷则使用 `docker compose down -v`。

**Verification**

- 验证 Docker CLI 与 Docker Compose 可用。
- 验证 `docker compose up --build -d` 可以成功拉起 `db` 与 `app`。
- 验证 `docker compose ps` 中 `db` 和 `app` 处于运行状态，且 `http://localhost:8080/actuator/health` 可返回健康结果。
