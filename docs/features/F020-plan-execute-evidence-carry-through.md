---
id: F020
doc_kind: feature
status: completed
owner: codex
created: 2026-05-16
updated: 2026-05-16
parent_feature: F018
---
# Plan-Execute Evidence Carry-Through

## Goal

F020 closes the main interview-demo gap left by F018/F019: Plan-Execute can already route complex requests through `Deep Research Agent`, `Evidence Audit Agent`, and `Document Composer Agent`, but accepted evidence is still mostly carried as process text. The final answer therefore cannot reliably show durable `evidence_source` rows or non-zero citation telemetry.

The goal is to carry structured paper/web source identity through the Plan-Execute path so a generated report can be traced from final answer back to accepted evidence.

## Scope

- In scope: preserve structured citation metadata for paper and web evidence collected by `Deep Research Agent`.
- In scope: keep existing text evidence for audit/composition, but pair it with source identity used only for persistence and telemetry.
- In scope: persist accepted Plan-Execute paper/web evidence into the existing `evidence_source` table.
- In scope: make Plan-Execute `citationCount`, `evidence.evaluated`, and retrieval-completed telemetry reflect persisted accepted evidence.
- Out of scope: true parallel Agent runtime, dynamic Skills marketplace, new storage tables, broad UI redesign, or LLM report-writing changes.

## Acceptance Criteria

- A Plan-Execute run with accepted paper evidence creates `evidence_source` rows with `source_type=paper`, scoped `source_id`, snippet, score, and citation metadata.
- A Plan-Execute run with accepted web evidence creates `evidence_source` rows with `source_type=web`, URL/title/provider metadata, snippet, score, and rank.
- Rejected evidence candidates do not become final answer citations.
- Plan-Execute `citationCount` is non-zero when accepted evidence is persisted.
- Existing ReAct/project tool-loop citation behavior does not regress.
- Focused backend tests and Harness knowledge validation pass.

## Vision Anchor

The user wants one strong interview-demo capability, not more architecture expansion. This feature deliberately strengthens the existing serial Plan-Execute workflow instead of building parallel workers or a new tool marketplace. The demo value is: upload/index sources, ask for a research report, watch real subagents run, and inspect durable citations behind the final report.

## Links

- Spec: [F020-plan-execute-evidence-carry-through-spec.md](../specs/F020-plan-execute-evidence-carry-through-spec.md)
- Plan: [F020-plan-execute-evidence-carry-through-plan.md](../plans/F020-plan-execute-evidence-carry-through-plan.md)
- Evidence: [EV-019-f020-plan-execute-evidence-carry-through.md](../evidence/EV-019-f020-plan-execute-evidence-carry-through.md)
- Parent Feature: [F018-multi-agent-evidence-grounded-workflow.md](F018-multi-agent-evidence-grounded-workflow.md)
- Related Feature: [F019-semantic-intent-routing.md](F019-semantic-intent-routing.md)

## Current State

2026-05-16: Active. Start Gate selected this as the smallest high-value P0 interview-demo slice after the user asked Codex to choose one major feature to implement while they were away.

2026-05-16: Completed. Plan-Execute evidence now carries structured citation references through curation/gating and persists accepted paper/web sources into `evidence_source`; citation telemetry is non-zero when accepted citations exist.
