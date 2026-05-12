---
id: F014
doc_kind: feature
status: active
owner: codex
created: 2026-05-11
updated: 2026-05-11
---
# Primary Workspace Navigation UI

## 目标

把当前静态 UI 从“左侧图标切换辅助面板、主区永久保持聊天”的结构，重构为一级工作区导航。左侧 rail 表达完整 workspace switcher：会话、资料库、知识、项目等入口切换时，整个主工作区随之替换。资料库和知识必须成为完整工作台，而不是聊天页面旁边的小面板。

## 范围

- 范围内：左侧 rail 作为一级工作区导航。
- 范围内：会话工作区保留研究对话、证据、候选的三栏关系。
- 范围内：资料库工作区替换整个主区域，包含上传、资料管理、索引/沉淀状态、失败阶段、retry、来源详情 inspector。
- 范围内：知识工作区替换整个主区域，包含知识板、候选审核、确认/待验证状态、证据链路、知识详情 inspector。
- 范围内：前端状态模型区分 `activeWorkspace` 与会话内的右侧 view。
- 范围内：保留当前沉稳研究工具视觉语言，使用 Stitch 参考稿和 `PRODUCT.md` / `DESIGN.md` 作为设计锚点。
- 范围外：新后端 API、主动 reindex、单个来源手动生成候选、删除/归档资料、广义后端搜索、账户/权限/多租户 UI。

## 验收标准

- 点击 rail 的“会话”时，用户看到当前 F010 研究会话工作区：对话主区、证据/候选/知识上下文仍服务于当前回答。
- 点击 rail 的“资料库”时，聊天 composer 和旧右侧证据栏不出现；主工作区变为资料库页面。
- 点击 rail 的“知识”时，聊天 composer 和旧右侧证据栏不出现；主工作区变为知识页面。
- 资料库页面可呈现 PDF/web/note 来源、状态、失败阶段和 retry 行动，且不承诺未定义的主动 reindex、删除/归档或单来源候选生成。
- 知识页面可呈现 F008 的知识板分区、候选状态和候选确认操作，且不绕过用户确认写入知识。
- 前端测试覆盖 workspace 切换不会混用会话 sidebar view 状态。
- 桌面和窄屏布局不出现文本遮挡、旧三栏区域残留或 composer 混入非会话工作区。

## 合同

- API：复用 F005 来源 API、F008 候选/知识板 API、F010 已消费的会话/证据 API；本 Feature 不新增后端合同。
- 事件：复用 `source.status.changed`、`candidate.created`、`knowledge.entry.created`、`answer.delta`、`evidence.evaluated`。
- 数据：新增前端 UI 状态边界 `activeWorkspace`，候选实现值为 `session` / `sources` / `knowledge` / `project`；会话内右侧 tab 继续是会话工作区的局部状态。
- UI：见 [F014-primary-workspace-navigation-ui-spec.md](../specs/F014-primary-workspace-navigation-ui-spec.md)。

## 设计锚点

- Product context: [PRODUCT.md](../../PRODUCT.md)
- Design system context: [DESIGN.md](../../DESIGN.md)
- Stitch Project: `Research Workbench UI Optimization`, Project ID `6874803135901272290`
- Stitch screen: `研究助手工作台 - 主界面`, Screen ID `13932a7aff8e41948a73b4830d072e06`
- Stitch screen: `资料库工作台 - 来源管理界面`, Screen ID `5379df1c6dcb4012aadb1420d7117fc7`
- Stitch screen: `知识工作区 - 核心看板`, Screen ID `e220ee0bd7474bb48ef9a3bd507b2138`

## 入口判断

Start Gate: `needs feature`，已通过本 Feature 提供 durable Vision Anchor。

Vision Gate Entry:

- Original intent: 修正信息架构错误，让资料库和知识成为一级工作区，而不是聊天壳里的辅助面板。
- User pain point: 当前左侧 tab 看起来像一级导航，但实际只切换左侧抽屉，导致用户进入资料库/知识时仍被困在聊天主区。
- Alignment: F014 应先改导航和布局模型，再落 CSS/DOM；不应通过在旧三栏里塞更多面板来“兼容”。
- Reviewer policy: 独立 review 推荐，因这是用户可见的信息架构重构。

## 链接

- Spec: [F014-primary-workspace-navigation-ui-spec.md](../specs/F014-primary-workspace-navigation-ui-spec.md)
- Parent context: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Prior UI baseline: [F010-three-column-workbench-ui.md](F010-three-column-workbench-ui.md)

## 下一步

写实现计划，然后按计划先改前端状态/布局模型，再改 DOM/CSS，最后补测试和浏览器证据。
