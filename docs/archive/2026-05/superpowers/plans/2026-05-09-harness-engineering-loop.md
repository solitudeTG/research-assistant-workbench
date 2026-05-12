---
id: ARCHIVE-PLAN-2026-05-09-HARNESS-ENGINEERING-LOOP
doc_kind: plan
status: archived
archived: 2026-05-10
feature_ids: [F001]
---
# Harness 工程闭环实施计划

> **给 Agent 工作者：**执行本计划时必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，逐项执行复选框步骤。

**目标：**建立一套轻量 Harness 层，用来控制下一轮研究工作台大规模重构。

**架构：**Markdown 文件作为事实源。一个小型 Python 校验器检查必要结构，让未来 Agent 工作拥有可恢复的 Feature、ADR 和 Evidence 上下文，同时不引入新基础设施。

**技术栈：**Markdown、Python 标准库、PowerShell 兼容命令。

---

### 任务 1：添加知识校验器红灯测试

**文件：**
- 新建：`scripts/test_knowledge_check.py`
- 后续新建：`scripts/knowledge_check.py`

- [x] **步骤 1：编写失败的 unittest 覆盖**

测试会导入 `scripts/knowledge_check.py`，校验一个最小合格 Harness 工作区，并拒绝缺少 `## 验收标准` 的 Feature。

- [x] **步骤 2：运行测试确认红灯**

运行：`python -m unittest scripts/test_knowledge_check.py`

预期：失败，因为 `scripts/knowledge_check.py` 不存在。

### 任务 2：添加 Harness 校验器

**文件：**
- 新建：`scripts/knowledge_check.py`

- [x] **步骤 1：实现校验器**

校验器检查：

- `docs/BACKLOG.md`
- `docs/features`
- `docs/decisions`
- `docs/evidence`
- Feature frontmatter，以及 `目标 / 验收标准 / 链接`
- ADR frontmatter，以及 `决策 / 备选方案 / 影响`
- Evidence frontmatter，以及 `证据`

- [x] **步骤 2：运行校验器测试**

运行：`python -m unittest scripts/test_knowledge_check.py`

预期：通过。

### 任务 3：添加初始 Harness 文档

**文件：**
- 新建：`docs/BACKLOG.md`
- 新建：`docs/harness/README.md`
- 新建：`docs/harness/templates/feature.md`
- 新建：`docs/harness/templates/adr.md`
- 新建：`docs/harness/templates/evidence.md`
- 新建：`docs/features/F001-harness-engineering-loop.md`
- 新建：`docs/features/F002-next-generation-research-workbench.md`
- 新建：`docs/decisions/ADR-001-markdown-harness-source-of-truth.md`
- 新建：`docs/decisions/ADR-002-rebuild-around-project-workbench.md`
- 新建：`docs/evidence/EV-001-harness-bootstrap.md`

- [x] **步骤 1：创建文档目录和文件**

采用聚焦的 Markdown 文档，而不是数据库或自定义 Harness 服务。

- [x] **步骤 2：运行结构门**

运行：`python scripts/knowledge_check.py`

预期：输出 `knowledge_check: ok`。

### 任务 4：记录验证证据

**文件：**
- 修改：`docs/evidence/EV-001-harness-bootstrap.md`

- [x] **步骤 1：用精确验证输出替换待填结果**

记录本会话中的测试和结构门输出。

- [x] **步骤 2：重新运行两个验证命令**

运行：

```powershell
python -m unittest scripts/test_knowledge_check.py
python scripts/knowledge_check.py
```

预期：两个命令都以退出码 0 结束。

### 自检

- 规格覆盖：F001 覆盖 Harness 启动；F002 捕获下一轮重构边界。
- 占位符扫描：未发现未填占位内容。
- 类型一致性：测试引用的校验器函数与实现保持一致。
