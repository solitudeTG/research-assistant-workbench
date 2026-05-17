# Design System: Research Assistant Workbench

## 1. Visual Theme & Atmosphere

沉稳、密度适中、偏研究工具而非展示页面。整体是浅色工作台：暖纸感主背景承载阅读和输入，冷灰蓝侧栏承载结构化导航和审计信息，深蓝灰 rail 提供稳定的一级工作区入口。

物理场景：用户在白天或夜间的桌面显示器前，长时间整理资料、追问证据、确认知识，需要低干扰、高信任、可连续工作的界面。

## 2. Color Palette & Roles

- Scholar Ember (`oklch(62% 0.18 42)`): 当前主操作、选中状态和关键行动。
- Deep Slate Rail (`oklch(24% 0.035 255)`): 一级工作区 rail 背景。
- Paper Warm Background (`oklch(98% 0.004 35)`): 页面基础背景。
- Clean Paper Surface (`oklch(99% 0.003 35)`): 主内容、表格和输入表面。
- Cool Archive Surface (`oklch(96% 0.008 250)`): 次级面板、筛选区、资料管理辅助层。
- Inspector Mist (`oklch(95% 0.01 250)`): 证据/详情 inspector 的冷静背景。
- Research Ink (`oklch(22% 0.035 255)`): 主要文本。
- Muted Blue Gray (`oklch(47% 0.035 255)`): 次级文本、说明和元信息。
- Success Green (`oklch(53% 0.13 150)`): 已索引、已沉淀、成功状态。
- Evidence Blue (`oklch(55% 0.13 245)`): 外部来源、证据链接、来源类型提示。
- Warning Amber (`oklch(67% 0.14 75)`): 待验证、证据偏弱、处理中。
- Failure Red (`oklch(55% 0.16 28)`): 失败阶段和错误状态。

## 3. Typography Rules

使用产品型无衬线字体栈：`Public Sans`, `Inter`, `Microsoft YaHei`, `PingFang SC`, `sans-serif`。标题短促有力，表格和列表使用紧凑字号，正文段落保持舒适行高。不要使用展示字体，不使用流式缩放标题。

## 4. Component Stylings

* **Workspace Rail:** 深色窄 rail，图标按钮表达一级工作区。选中态使用左侧细指示和主色，不用长文字解释。
* **Buttons:** 4-8px 小圆角。主按钮使用 Scholar Ember，次级按钮保持纸面底和细边框。图标按钮用于明确工具动作。
* **Panels:** 页面级区域不套卡片。卡片仅用于列表项、重复实体、详情 inspector 或局部工具。
* **Tables and Lists:** 高密度、可扫描，行内展示状态、类型、时间和失败阶段。悬停态只轻微强化，不打断阅读。
* **Inputs and Filters:** 低对比纸面或冷灰表面，聚焦态使用主色描边。
* **Status Chips:** 成功、警告、失败、外部证据分别使用语义色的低饱和背景。

## 5. Layout Principles

左侧 rail 是一级 workspace switcher。会话工作区可以保留聊天/证据/候选三栏；资料库和知识工作区必须替换整个主工作区，提供上传、管理、状态、详情、筛选、确认和编辑等完整页面能力。桌面端优先使用可扫描的工具型布局：顶部上下文栏、主列表或主画布、右侧详情 inspector。窄屏时工作区纵向堆叠，保持当前工作区完整，不把旧聊天 composer 强行混入资料库或知识页面。
