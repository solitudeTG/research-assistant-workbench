---
id: SPEC-F002-UI
doc_kind: spec
status: accepted
updated: 2026-05-10
feature_ids: [F002, F010]
---
# Research Workbench UI Interaction Spec

## Purpose

This document records the agreed product and UI interaction model for the research assistant workbench. It is intended to keep frontend, backend, data model, and API work aligned.

The product is not a single-paper Q&A tool. It is a project-space-based long-term research knowledge workbench.

The core loop is:

```text
Import sources -> Deposit project knowledge -> Multi-turn research dialogue -> Answer from local knowledge first -> Supplement from web when evidence is weak -> Extract knowledge candidates -> User confirms -> Write to the topic knowledge board
```

## Product Model

The highest-level object is a research project.

A project contains:

- A stable research topic.
- Multiple research sessions.
- Source materials such as papers, web pages, and personal notes.
- A structured topic knowledge board.
- Knowledge candidates extracted from conversations.
- Evidence sources used by assistant answers.
- Long-term project memory that can be reused by future answers.

Research sessions are branches of the research process. They are not the top-level product object.

Source materials are not temporary attachments. Imported sources become part of the project knowledge base after parsing, indexing, and deposition.

The topic knowledge board is the long-term structured memory of the project. It stores user-confirmed research understanding, not every raw assistant response.

## Layout Model

The screen uses a three-column desktop workspace.

### Left Column: Project, Sessions, Sources

The left column manages the research space.

It includes:

- Current project summary.
- Research session list.
- Source library.
- Import source action.

The project summary should show the current research topic and compact project statistics, such as paper count, knowledge entry count, and session count.

The session list supports multiple sessions under one project. Each session item should show:

- Session title.
- Last updated time.
- Status, such as `continue`, `deposited`, or `drafting`.
- Active selection state.

Selecting a session updates the center conversation and right research sidebar context.

The source library should show concrete sources, not only categories. Supported source types include:

- PDF papers.
- Web pages.
- Personal notes.

Each source item should show:

- Source name.
- Source type.
- Processing status.
- Related deposited knowledge count when available.

Suggested source statuses:

- `uploaded`
- `parsing`
- `indexing`
- `extracting`
- `indexed`
- `depositing`
- `deposited`
- `web_supplement`
- `failed`

Failures should preserve the failed stage, such as parsing failure, indexing failure, extraction failure, or web fetch failure.

### Center Column: Multi-Turn Research Dialogue

The center column is the primary working area.

It should feel like an ongoing multi-turn conversation, similar in usage rhythm to Codex or messaging tools, but adapted for academic research.

The conversation contains:

- User messages.
- Assistant messages.
- Source and evidence chips.
- Lightweight answer actions.
- A persistent composer.

User messages should be visually distinct and aligned as user-authored turns.

Assistant messages should read like research note blocks. They should support structured paragraphs, citations, and status chips.

Assistant answer chips may include:

- `local_evidence_strong`
- `local_evidence_weak`
- `web_supplemented`
- `citation_count`
- `deposited`
- `draft_suggestion`
- `can_write_to_board`

User-facing labels should stay concise, for example:

- `本地证据强`
- `证据偏弱`
- `联网补充`
- `引用 6`
- `已沉淀`
- `草稿建议`
- `可写入知识板`

Assistant answer actions include:

- `查看引用`
- `查看候选`
- `提炼候选`
- `写入知识板` when applicable

Clicking `查看引用` switches the right sidebar to the evidence source view.

Clicking `查看候选` switches or scrolls the right sidebar to the candidate confirmation area.

The persistent composer should support:

- Multi-line input.
- Sending a question.
- Dragging in papers, web pages, or notes.
- Current answer mode indicators.

Composer mode indicators:

- `本地知识优先`
- `证据不足时联网补充`
- `回答后可沉淀`

Conversation requests should carry at least:

- `projectId`
- `sessionId`
- `question`
- optional source filters
- whether web supplementation is allowed
- whether knowledge candidate extraction is enabled

### Right Column: Research Sidebar

The right column is the research sidebar.

It is not only a topic knowledge board. It is a contextual workspace that connects the current dialogue to long-term research assets.

The research sidebar has three lightweight views:

- `知识板`
- `证据来源`
- `候选确认`

The default view is `知识板`.

The sidebar should show a subtle context line for the current answer, for example:

```text
当前回答关联 6 条引用，2 条候选知识
```

## Knowledge Board View

The knowledge board view shows project-level long-term knowledge.

It is organized by research cognition, not by time.

Suggested sections:

- `本轮候选`
- `核心概念`
- `方法路线`
- `已确认结论`
- `待验证问题`

Each section supports expand and collapse.

Section headers should show a count:

- `本轮候选 (2)`
- `核心概念 (8)`
- `方法路线 (12)`
- `已确认结论 (16)`
- `待验证问题 (5)`

A collapsed section shows only:

- Section title.
- Count.
- Disclosure control.

An expanded section shows compact knowledge rows.

A knowledge entry should contain:

- Title.
- Short content.
- Category.
- Source tags, such as paper, conversation, or web.
- Evidence status.
- Updated time or source reference when useful.

The knowledge board is a confirmed, reusable project memory source. Future assistant answers should be able to retrieve from it.

## Candidate Confirmation View

Knowledge candidates are AI-extracted drafts from the current answer or research session.

Candidates must not automatically enter the long-term knowledge board. User confirmation is required to protect knowledge quality and avoid polluting project memory.

Each candidate should show:

- Candidate title.
- Extracted statement.
- Evidence or source chips.
- Suggested target category.
- Available actions.

Example chips:

- `本地论文 4`
- `引用 6`
- `证据强`
- `待验证`

Example suggested categories:

- `建议归入：核心概念`
- `建议归入：方法路线`
- `建议归入：已确认结论`
- `建议归入：待验证问题`

Candidate actions:

- `写入知识板`
- `编辑`
- `标为待验证`
- `忽略`

Editing should be inline. Avoid large modal dialogs for normal candidate confirmation.

Inline editing should support:

- Editable title.
- Editable summary or statement.
- Category selection:
  - `核心概念`
  - `方法路线`
  - `已确认结论`
  - `待验证问题`
- Final actions:
  - `确认写入`
  - `取消`

Candidate statuses:

- `pending`
- `accepted`
- `edited_accepted`
- `marked_unverified`
- `ignored`

Candidate source types:

- assistant answer
- paper evidence
- web supplement
- conversation memory
- user note

## Evidence Source View

The evidence source view explains why the assistant answered the way it did.

It is opened by:

- Clicking `查看引用` in an assistant answer.
- Selecting the `证据来源` tab in the research sidebar.

Evidence should be grouped by source type:

- `本地论文`
- `项目知识`
- `联网补充`
- `会话记忆`

Each evidence item should show:

- Source title.
- Snippet or summary.
- Source type.
- Evidence strength or relevance.
- Citation metadata.
- Optional jump target to the original source.

The evidence source view should help users audit answers without exposing raw technical jargon as primary UI copy.

Avoid using `RAG` as a visible section heading. Prefer user-facing labels such as `证据来源`, `本地论文`, and `联网补充`.

## Source Import and Deposition

Source import is a knowledge-base action, not an attachment-only action.

Recommended processing flow:

```text
uploaded -> parsing -> indexing -> extracting -> deposited
```

For web sources:

```text
submitted -> fetching -> extracting -> indexed -> deposited
```

The system should expose stage-aware failures:

- parse failure
- index failure
- extraction failure
- fetch failure
- deposition failure

After successful processing, imported sources may produce:

- Searchable chunks.
- Structured document analysis.
- Knowledge candidates.
- Confirmed knowledge entries after user review.

## Interaction Flow

### Ask and Answer

1. User selects a project and session.
2. User asks a question in the center composer.
3. Backend retrieves from project knowledge, source documents, and conversation memory.
4. If local evidence is weak and web supplementation is enabled, backend may perform web supplementation.
5. Assistant streams or returns an answer.
6. Answer includes user-facing evidence state and citation metadata.
7. Assistant may propose knowledge candidates.
8. User can inspect citations or confirm candidates in the research sidebar.

### Candidate Deposition

1. Assistant answer produces candidate knowledge entries.
2. Candidates appear in the right sidebar.
3. User accepts, edits, marks as unverified, or ignores each candidate.
4. Accepted candidates become knowledge entries.
5. Knowledge entries appear in the relevant knowledge board section.
6. Future answers can retrieve from confirmed knowledge entries.

### Evidence Inspection

1. User clicks `查看引用`.
2. Right sidebar switches to `证据来源`.
3. Evidence items are grouped by source type.
4. User can inspect snippets and source metadata.
5. User can return to `知识板` or `候选确认`.

## Recommended Frontend State

The frontend should track:

- active project
- active session
- source library list
- selected right sidebar view
- collapsed or expanded right sidebar sections
- active assistant answer context
- pending candidates
- currently edited candidate
- source import status
- streaming answer state

The collapse state of right sidebar sections may be stored locally at first. If user preference sync matters later, it can move to backend user preferences.

## Recommended Backend Entities

Suggested entities:

- `Project`
- `ResearchSession`
- `SourceDocument`
- `ChatMessage`
- `AssistantAnswer`
- `EvidenceSource`
- `KnowledgeCandidate`
- `KnowledgeEntry`
- `KnowledgeBoardSection`
- `WebSupplementResult`

## Recommended API Capabilities

Project and session:

- List projects.
- Get project summary.
- List sessions for a project.
- Create session.
- Get session messages.
- Rename or archive session.

Sources:

- Import source.
- List sources for a project.
- Get source status.
- Get source analysis.
- Retry failed source processing.

Chat:

- Send message.
- Stream assistant answer.
- Get answer citations.
- Get answer-generated candidates.

Evidence:

- Get evidence sources for an answer.
- Group evidence by source type.

Candidates:

- List candidates for an answer or session.
- Accept candidate.
- Edit and accept candidate.
- Mark candidate as unverified.
- Ignore candidate.

Knowledge board:

- List board sections.
- List entries by section.
- Create entry.
- Update entry.
- Move entry between sections.
- Delete or archive entry.

Preferences:

- Save sidebar selected view, optional.
- Save collapsed sections, optional.

## UI Copy Principles

Use concise, operational Chinese labels.

Prefer:

- `研究侧栏`
- `知识板`
- `证据来源`
- `候选确认`
- `本地证据强`
- `联网补充`
- `待验证`
- `已沉淀`

Avoid:

- Marketing claims.
- Long explanatory paragraphs in the main UI.
- Raw technical labels such as `RAG` as top-level headings.
- Ambiguous verbs such as `处理` when a more specific verb exists.

## Visual Interaction Principles

The visual language should support long-session research work.

Principles:

- Calm academic tone.
- Warm paper-like working surface.
- Slightly cooler research sidebar for structured knowledge.
- Archive-like left source library.
- Scholarly teal for primary actions and selected states.
- Blue-gray for web or external sources.
- Amber only for weak evidence or pending verification.
- Minimal hard boxes.
- No decorative gradients.
- No thick side accent bars.
- No glassmorphism.
- No pure black or pure white.

The interface should feel like a mature research product, not a demo dashboard.

## Development Alignment Notes

The most important product rule is:

```text
Dialogue is process. The knowledge board is the result.
```

Papers and web pages are sources.

Assistant answers are intermediate reasoning and writing support.

Knowledge candidates are reviewable drafts.

Only user-confirmed entries become long-term project knowledge.

The research sidebar connects the current answer to durable research assets through three views: knowledge, evidence, and candidate confirmation.
