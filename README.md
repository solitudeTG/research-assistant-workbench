# 智能研究助手工作台

这是一个面向论文阅读与科研问答的本地工作台。当前版本聚焦 `Phase 1` 主链路：上传论文，解析并切块索引，然后基于本地论文证据进行问答、引用展示、检索追踪和会话记忆沉淀。

项目不是单纯的聊天 Demo。它把论文接入、RAG 检索、证据边界、会话记忆和轻量 Web 工作台放在同一个可运行的 Spring Boot 应用里，方便先验证核心研究助手流程，再逐步演进到多 Agent、长期记忆、反馈优化和报告生成。

最新变更记录见 [CHANGELOG.md](./CHANGELOG.md)。

## 快速启动

推荐使用 Docker Desktop 一键启动。脚本会读取 `.env`，构建应用镜像，启动数据库和应用服务，并等待数据库与健康检查就绪。

### 1. 准备环境

确认本机已安装并启动：

- Docker Desktop
- Docker Compose

### 2. 配置模型参数

复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，至少填写真实的 `AI_API_KEY`：

```properties
COMPOSE_PROJECT_NAME=research-assistant
AI_API_KEY=your-real-key
AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
AI_CHAT_MODEL=glm-4-7-251222
AI_CHAT_COMPLETIONS_PATH=/chat/completions
```

`COMPOSE_PROJECT_NAME` 用来固定 Docker Compose 项目名。当前仓库路径包含中文字符时，建议保留这一项，避免 Compose 无法从目录名推导项目名。

默认模板将外部 embedding 与向量存储开关设为关闭，适合只有聊天模型凭证时先跑通项目：

```properties
AI_EMBEDDING_PROVIDER=none
AI_VECTORSTORE_TYPE=none
```

### 3. 启动工作台

Windows 用户推荐：

```powershell
.\scripts\start-dev.cmd
```

PowerShell 用户也可以直接运行：

```powershell
.\scripts\start-dev.ps1
```

启动完成后打开：

[http://localhost:8080](http://localhost:8080)

### 4. 停止服务

```powershell
.\scripts\stop-dev.cmd
```

或直接使用 Docker Compose：

```powershell
docker compose down
```

## 使用流程

1. 打开工作台首页。
2. 在左侧上传论文文件。
3. 等待文档状态从 `UPLOADED / PARSING / INDEXING` 进入 `INDEXED`。
4. 选择已索引论文。
5. 在对话区提问，例如“这篇论文研究了什么？”、“方法是什么？”、“主要贡献有哪些？”。
6. 在回答区查看模型回答，在右侧查看引用片段、结构化分析和检索轨迹。

## 当前能力

- 论文上传与本地文件存储
- PDF 文本抽取、重叠切块与索引
- 文档状态机：`UPLOADED -> PARSING -> INDEXING -> INDEXED / FAILED`
- 基于关键词、元数据和向量的混合检索
- 本地论文证据问答与引用片段返回
- 证据边界判断：`LOCAL_EVIDENCE`、`LOCAL_WEAK_EVIDENCE`、`REFUSAL`
- SSE 流式回答
- 会话级 `L1 working memory`
- 显式记忆写入与历史记忆召回的基础能力
- 检索 trace、反馈记录和报告导出接口
- 中文优先的轻量 Web 工作台

## 架构概览

项目采用单体应用加模块化边界的方式实现，避免一开始引入微服务、消息队列、多数据库等重型设施。核心目标是先把“论文接入到可信问答”的主链路跑稳。

```text
Web 工作台 / REST API / SSE
        |
        v
SupervisorService
        |
        +-- TaskRouter：判断是否需要论文检索或记忆召回
        +-- PaperRagService：执行混合检索、合并排序和 trace 记录
        +-- EvidenceBoundaryService：判断证据是否足够支撑回答
        +-- WorkingMemoryService：维护会话级工作记忆
        +-- MemoryRecallService：召回历史研究记忆
        +-- PlanExecuteFacade：预留复杂任务规划入口
```

主要模块：

- `chat`：同步问答、SSE 流式问答、系统状态接口
- `ingest`：文档上传、解析、切块、索引和结构化分析
- `rag`：查询改写、关键词检索、元数据检索、向量检索和检索 trace
- `evidence`：证据充分性判断与回答模式映射
- `memory`：会话、消息、工作记忆、长期记忆和 trace 查询
- `feedback`：消息级反馈与 chunk 反馈权重更新
- `report`：按会话导出 Markdown 报告

## 常用命令

启动：

```powershell
docker compose up --build -d
```

查看容器状态：

```powershell
docker compose ps
```

查看应用日志：

```powershell
docker compose logs -f app
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

停止服务：

```powershell
docker compose down
```

停止并清空数据库与应用存储卷：

```powershell
docker compose down -v
```

## 项目结构

```text
.
├── compose.yaml                 # 数据库与应用容器编排
├── Dockerfile                   # Spring Boot 应用镜像构建
├── scripts/                     # 开发启动与停止脚本
├── src/main/java/               # 后端源码
├── src/main/resources/
│   ├── application.yml          # Spring 与 AI 配置
│   ├── db/migration/            # Flyway 数据库迁移
│   └── static/                  # Web 工作台前端
├── src/test/java/               # 单元测试与集成测试
└── docs/superpowers/            # 设计规格与实施计划
```

## Phase 1 边界

当前阶段已经围绕单 `Supervisor`、论文接入、Paper RAG、证据边界和 L1 工作记忆打通主链路。以下方向保留了接口或基础实现，但还不是完整产品形态：

- 多 Agent 协作执行
- 完整 plan-execute 工作流
- 完整 L2/L3 长期记忆系统
- 联网补充检索
- benchmark 面板与完整可观察性后台
- 多文档复杂对比和结构化写作流水线

这个边界是有意为之：先保证可运行、可追踪、可迭代的研究问答主链路，再逐步扩展高级能力。
