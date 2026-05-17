---
id: F022-SPEC
doc_kind: spec
status: completed
created: 2026-05-17
updated: 2026-05-17
feature_ids: [F022]
---
# F022 Memory Recall Observability Spec

## Problem

The code already publishes memory trace events for L1 working memory, L2 confirmed project knowledge, and L3 long-term memory recall. The UI can show memory hits, but the behavior is still hard to explain as one coherent memory system:

- L2 confirmed knowledge is preloaded into the prompt.
- L3 memory is usually recalled by the `memory_recall` tool.
- L1 working memory is always local session context.
- Paper/web evidence is citation material, while memory is only context.

F022 must make those distinctions visible and testable.

## Event Contract

Each memory item shown to the user should normalize to:

- `memoryLayer`: `L1`, `L2`, or `L3`
- `sourceType`: `working_memory`, `global_knowledge`, `project_knowledge`, or `long_term_memory`
- `sourceId`: stable id when available
- `title`: concise display title
- `summary`: bounded display content
- `score`: numeric score when available
- `rank`: rank within the layer or recall result when available
- `contextOnly`: always `true`
- `injectionMode`: `preloaded_prompt`, `tool_recall`, or `summary_only`
- `reason`: short explanation such as `recent_confirmed_project_knowledge`, `working_memory_window`, or `memory_recall_result`

`memory.completed` should expose aggregate counts:

- `workingMemoryHitCount`
- `projectKnowledgeHitCount`
- `longTermMemoryHitCount`
- `toolCalled`
- `contextOnly`

## UI Contract

The research process module should group memory hits by layer:

- L1: current session working memory.
- L2: confirmed project knowledge or global cognition context.
- L3: long-term memory recall results.

The UI must not render memory hits inside the final citation/evidence list. Memory rows should use language such as "context only" or "not citation evidence".

## Non-Goals

- No semantic search for `knowledge_entry`.
- No vector storage for L2.
- No promotion from `memory_entry` to `knowledge_candidate`.
- No project schema migration for `memory_entry`.
- No changes to paper/web retrieval scoring.

## Verification

- Backend focused tests should assert event payloads for L2 and L3.
- Backend tests should assert memory hits do not create evidence source rows or inflate paper/web counts.
- Frontend model tests should assert grouped memory summary projection.
- Frontend syntax check should pass.
- `scripts/knowledge_check.py` should pass.
