---
id: ARCHIVE-PLAN-2026-04-17-DOCKER-COMPOSE-STARTUP
doc_kind: plan
status: archived
archived: 2026-05-10
feature_ids: []
---
# Docker Compose Startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让项目支持通过 `docker compose up --build -d` 一条命令启动数据库和 Spring Boot 应用。

**Architecture:** 采用 `Dockerfile + compose.yaml` 的双层方案。`Dockerfile` 负责多阶段构建 Spring Boot 镜像，`compose.yaml` 负责编排 `db` 与 `app`，启动脚本只做环境检查与命令封装。

**Tech Stack:** Docker, Docker Compose, Maven, Eclipse Temurin JDK/JRE 17, PostgreSQL with pgvector, Spring Boot

---

### Task 1: Add container build artifacts

**Files:**
- Create: `D:\智能研究助手项目讲解V2_update\Dockerfile`
- Create: `D:\智能研究助手项目讲解V2_update\.dockerignore`

- [ ] Define a multi-stage image build that packages the Spring Boot jar with `mvn -DskipTests package`.
- [ ] Exclude local caches, docs, and generated artifacts from the Docker build context.

### Task 2: Extend Compose to run the application

**Files:**
- Modify: `D:\智能研究助手项目讲解V2_update\compose.yaml`

- [ ] Add an `app` service built from `Dockerfile`.
- [ ] Wire `app` to the `db` healthcheck with `depends_on`.
- [ ] Expose port `8080` and persist `/app/storage` with a named volume.

### Task 3: Update startup scripts and docs

**Files:**
- Modify: `D:\智能研究助手项目讲解V2_update\start.ps1`
- Modify: `D:\智能研究助手项目讲解V2_update\start.cmd`
- Modify: `D:\智能研究助手项目讲解V2_update\README.md`
- Modify: `D:\智能研究助手项目讲解V2_update\.env.example`

- [ ] Make the scripts call `docker compose up --build -d` after validating Docker and `AI_DASHSCOPE_API_KEY`.
- [ ] Document start, stop, log, and cleanup commands.
- [ ] Document required `.env` variables for Docker startup.

### Task 4: Verify the Docker startup path

**Files:**
- Verify only

- [ ] Run `docker --version` and `docker compose version`.
- [ ] Run `docker compose up --build -d`.
- [ ] Run `docker compose ps` and a health probe against `http://localhost:8080/actuator/health`.
- [ ] If startup fails, inspect `docker compose logs app` and fix the configuration before completion.
