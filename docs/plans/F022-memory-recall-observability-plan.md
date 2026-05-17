---
id: F022-PLAN
doc_kind: plan
status: completed
created: 2026-05-17
updated: 2026-05-17
feature_ids: [F022]
---
# F022 Memory Recall Observability Plan

## Start Gate

Start Gate: needs feature

Task class:
- non-trivial

Risk triggers:
- User-facing behavior and trace contracts change.
- Memory/evidence boundaries must remain strict.
- Future F023/F024 work depends on a recoverable baseline.

Delegation decision:
- authorized. The user explicitly authorized subagents for complex tasks and an independent vision guardian.

Required pre-work:
- Create Feature, spec, and plan anchors before code.

Allowed next action:
- Explore backend and frontend trace surfaces, then implement the smallest F022 slice.

## Vision Gate Entry

Original goal:
- Make L2/L3 memory usage explainable and visible before making retrieval smarter or adding promotion.

Smallest coherent path:
- Normalize and render current memory trace data; do not change retrieval ranking.

Non-goals:
- No L2 semantic retrieval.
- No L3-to-L2 promotion.
- No memory-as-citation behavior.
- No schema migration unless existing event payloads cannot support the display.

Exit Gate source:
- Feature F022 plus this plan.

## Work Split

- Main agent owns branch management, Harness docs, integration, final verification, commit, and push.
- Explorer A checks backend event payload and tests needed for L1/L2/L3 trace normalization.
- Explorer B checks frontend model/UI projection and focused tests.
- Independent vision guardian reviews final behavior against the F022 Vision Anchor before closeout.

Workers are not alone in the codebase. They must not revert unrelated edits and must keep recommendations scoped to their ownership.

## Implementation Steps

1. Inspect current memory event payloads and frontend projection.
2. Add or normalize backend fields only where needed for `injectionMode`, `reason`, layer counts, and context-only semantics.
3. Update frontend model to group memory hits by layer and expose aggregate summary.
4. Update research process UI labels/details if the current rendering is too ambiguous.
5. Add focused backend and frontend tests.
6. Run independent vision review.
7. Capture Evidence, run validation, commit, and push.

## Rollback

If F022 destabilizes the answer flow or evidence rendering, revert only the memory observability wiring and keep existing F015/F016/F021 trace behavior intact.

## Closeout

2026-05-17: Completed. The implementation followed the smallest coherent path: normalized existing memory trace events, grouped frontend projection by layer, and separated memory context from citation evidence rendering. Evidence: [EV-021-f022-memory-recall-observability.md](../evidence/EV-021-f022-memory-recall-observability.md).
