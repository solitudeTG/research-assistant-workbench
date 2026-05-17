---
id: F022
doc_kind: feature
status: completed
owner: solitudeTG
created: 2026-05-17
updated: 2026-05-17
parent_feature: F002
---
# Memory Recall Observability

## Goal

Make the memory context used by a project answer explainable and auditable. Users should be able to see which L1, L2, and L3 memory signals were available to the answer, which ones entered the prompt or tool result, and why they remain context-only rather than citation evidence.

The goal is not to make memory retrieval smarter yet. F022 first makes the current memory behavior visible enough that future L2 semantic retrieval and L3-to-L2 promotion can be implemented without blurring memory, knowledge, and evidence boundaries.

## Scope

- In scope: expose a unified memory recall summary for each project answer/run.
- In scope: distinguish L1 working memory, L2 global/project knowledge, and L3 long-term memory.
- In scope: mark every memory item as `contextOnly=true` and keep it separate from paper/web citation evidence.
- In scope: show whether memory was preloaded into the main prompt or actively recalled through `memory_recall`.
- In scope: include useful metadata such as source type, source id, score, title/summary, rank, and reason.
- In scope: render the memory summary in the existing research process UI or answer diagnostics surface.
- In scope: backend and frontend tests that prove L2/L3 memory visibility without citation evidence pollution.
- Out of scope: L2 semantic retrieval.
- Out of scope: L3-to-L2 promotion, archive, supersede, or stale lifecycle.
- Out of scope: adding `project_id` to `memory_entry`.
- Out of scope: treating memory as final evidence or increasing evidence strength.

## Acceptance Criteria

- A project answer with L2 confirmed knowledge emits and renders L2 memory context as `project_knowledge`, `contextOnly=true`.
- A project answer with L3 recall emits and renders L3 memory context as `long_term_memory`, `contextOnly=true`.
- The UI shows memory context separately from paper/web evidence and labels it as non-citation context.
- The memory summary distinguishes preloaded context from tool-recalled context.
- `retrieval.completed` or a memory-specific event exposes bounded aggregate counts for L1/L2/L3.
- Existing paper/web evidence counts and citation rendering do not include memory items.
- Focused backend tests cover L2/L3 memory trace payloads and evidence boundary separation.
- Frontend model/UI tests cover grouped memory summary projection.
- Harness validation passes.

## Vision Anchor

The user needs a truthful interview-demo capability: the system should not merely claim that three-layer memory exists. It should show when memory participated in an answer, what layer it came from, and why it did not become paper evidence. F022 should illuminate the current memory loop before F023 changes recall ranking or F024 adds promotion/lifecycle behavior.

## Contracts

- Events: reuse or extend existing `memory.hit` and `memory.completed` run events.
- Data: reuse existing `WorkingMemory`, `global_knowledge_note`, `knowledge_entry`, and `memory_entry` data sources.
- UI: reuse the existing research process projection where possible; do not introduce a new large workspace.
- Boundary: memory context is always `contextOnly=true` and never creates `evidence_source` rows.

## Links

- Spec: [F022-memory-recall-observability-spec.md](../specs/F022-memory-recall-observability-spec.md)
- Plan: [F022-memory-recall-observability-plan.md](../plans/F022-memory-recall-observability-plan.md)
- Evidence: [EV-021-f022-memory-recall-observability.md](../evidence/EV-021-f022-memory-recall-observability.md)
- Related Feature: [F015-agent-trace-live-sse.md](F015-agent-trace-live-sse.md)
- Related Feature: [F016-retrieval-observability.md](F016-retrieval-observability.md)
- Related Feature: [F021-memory-self-learning-visualization.md](F021-memory-self-learning-visualization.md)

## Result

Completed on 2026-05-17. F022 keeps memory context separate from citation evidence while making the L1/L2/L3 memory path visible in backend events and the research process UI, including both L2 global cognition and L2 confirmed project knowledge. Verification is recorded in [EV-021](../evidence/EV-021-f022-memory-recall-observability.md).
