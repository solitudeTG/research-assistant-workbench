---
id: SPEC-F014
doc_kind: spec
status: draft
updated: 2026-05-11
feature_ids: [F014]
---
# F014 Primary Workspace Navigation UI Spec

## Purpose

F014 fixes the workbench information architecture. The left rail is a first-level workspace switcher. It must not behave like a drawer tab that only changes a supporting panel while the chat workspace remains permanently mounted.

The product model is:

```text
Research Project
-> Session workspace: dialogue process, evidence, candidates for current answer
-> Sources workspace: project source library, ingestion status, source details
-> Knowledge workspace: confirmed knowledge, candidates, evidence lineage
-> Project workspace: project metadata and settings
```

## Non-goals

- Do not add backend APIs in this Feature.
- Do not implement broad backend source search.
- Do not add active reindex for already deposited sources unless a later Feature defines the contract.
- Do not add source deletion/archive unless a later Feature defines source lifecycle.
- Do not generate candidates from a single source unless a later Feature defines the API and event contract.
- Do not redesign the visual system; preserve the scholarly product UI language.

## Workspace Model

Frontend state must separate global workspace selection from session-local panel selection.

Recommended state shape:

```js
{
  activeWorkspace: "session" | "sources" | "knowledge" | "project",
  sessionWorkspace: {
    selectedSidebarView: "knowledge-board" | "evidence-sources" | "candidate-confirmation"
  },
  sourceWorkspace: {
    selectedSourceId,
    filter,
    statusFilter,
    selectedType
  },
  knowledgeWorkspace: {
    selectedSection,
    selectedEntryId,
    selectedCandidateId,
    candidateFilter
  }
}
```

`selectedSidebarView` must not decide which primary workspace is visible. It belongs only to the session workspace.

## Session Workspace

The session workspace is the existing F010 research dialogue surface.

It keeps:

- Active session header.
- Conversation.
- Composer.
- Answer state.
- Evidence/candidate/knowledge contextual sidebar for the current answer.

It must not own the source library and knowledge board as full product surfaces.

## Sources Workspace

The sources workspace replaces the entire main workspace.

Required regions:

- Workspace header: project topic, source counts, indexed/deposited/failed summary.
- Import zone: PDF, web, note entry points where existing API support exists.
- Source table or dense list: title, type, status, failure stage, deposited knowledge count, updated time.
- Processing pipeline strip: uploaded/submitted, parsing/fetching, indexing, extracting, depositing, deposited/failed.
- Failure handling: retry is visible only when the source is failed.
- Source inspector: selected source metadata, status history summary, extracted knowledge/candidate hints when available.

Allowed actions in F014:

- Import supported source types through existing import behavior.
- Refresh/list sources.
- Retry failed sources through F005.
- Select a source to inspect details.

Actions that must be visually absent or disabled with no false promise:

- Reindex a successful source.
- Delete/archive a source.
- Generate candidates from a single source.
- Browse raw chunks as a full feature.

Local UI filtering is allowed when it filters already loaded rows. Backend search must not be implied.

## Knowledge Workspace

The knowledge workspace replaces the entire main workspace.

Required regions:

- Workspace header: confirmed entries, pending candidates, open questions, last updated.
- Knowledge board: sections from F008: `current_candidates`, `core_concept`, `method_route`, `confirmed_finding`, `open_question`.
- Candidate review lane: pending, accepted, edited accepted, marked unverified, ignored.
- Knowledge inspector: selected entry or candidate detail, source lineage, evidence links, related answer/session when available.

Allowed actions in F014:

- View knowledge board sections.
- View project candidates.
- Accept candidate.
- Edit and accept candidate.
- Mark candidate unverified.
- Ignore candidate.
- Create, patch/move, and archive knowledge entries using F008 contracts where already available.

The UI must preserve the F008 write boundary: candidates are drafts; only explicit user confirmation writes long-term knowledge.

## Project Workspace

Project workspace can remain minimal in F014 if implementation risk needs containment.

Required minimum:

- Project topic and summary.
- Project statistics.
- Links or compact summaries for sessions, sources, knowledge.

It must not block the delivery of session/sources/knowledge workspace separation.

## Navigation Rules

- Rail buttons update `activeWorkspace`.
- The visible primary workspace is derived from `activeWorkspace`.
- Non-active workspaces are hidden from keyboard tab order and screen-reader primary navigation where practical.
- Workspace switch should not erase loaded project data.
- Workspace switch should not reset session-local sidebar state unless the user explicitly changes session context.
- Non-session workspaces must not render the chat composer.

## Responsive Rules

Desktop:

- Rail stays fixed.
- Session workspace may use the existing three-column shell.
- Sources and knowledge use a header + main list/board + inspector layout.

Narrow screens:

- Rail becomes compact top or sticky navigation if needed.
- Workspace content stacks within the selected workspace.
- No old chat/right-sidebar regions should appear under sources or knowledge just because of CSS stacking.

## Design References

- `PRODUCT.md` and `DESIGN.md` are project-level design context.
- Stitch Project ID: `6874803135901272290`.
- Session baseline: `13932a7aff8e41948a73b4830d072e06`.
- Sources workspace: `5379df1c6dcb4012aadb1420d7117fc7`.
- Knowledge workspace: `e220ee0bd7474bb48ef9a3bd507b2138`.

## Acceptance Tests

Frontend model tests should cover:

- `activeWorkspace` defaults to `session`.
- Rail workspace switch changes visible workspace.
- Switching to `sources` hides composer and session right sidebar state.
- Switching to `knowledge` hides composer and session right sidebar state.
- `source.status.changed` still updates source rows while sources workspace is inactive.
- `candidate.created` and `knowledge.entry.created` still update knowledge state while knowledge workspace is inactive.
- Session `selectedSidebarView` remains session-local and does not select primary workspaces.

Browser/manual verification should cover:

- Desktop session workspace.
- Desktop sources workspace.
- Desktop knowledge workspace.
- Narrow viewport switching between all three workspaces.
- No text overlap, no stale composer, no stale right evidence sidebar in non-session workspaces.
