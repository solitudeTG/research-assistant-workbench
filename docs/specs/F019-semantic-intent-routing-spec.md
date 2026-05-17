---
id: SPEC-F019
doc_kind: spec
status: accepted
updated: 2026-05-15
feature_ids: [F019]
---
# F019 Semantic Intent Routing Spec

## Background

F018 proved the value of a visible, serial multi-Agent workflow, but its trigger path still started as deterministic keyword matching. Manual validation then showed a broader AI-product lesson: rules are useful for hygiene and fallback, but brittle as the primary way to infer user intent.

F019 upgrades the `Supervisor` mode decision so natural-language intent can select between:

- `REACT`: lightweight main-Agent tool loop.
- `PLAN_EXECUTE`: serial multi-Agent workflow with Deep Research, Evidence Audit, and optional Document Composer.

## Design Principle

```text
semantic intent chooses the workflow; deterministic rules provide fallback and guardrails
```

The implementation must not replace one keyword list with another larger keyword list.

## Contract

`MultiAgentWorkflowDecider` remains the public mode-decision entry point.

It composes two decisions:

- Deterministic fallback decision: current stable behavior and safety net.
- Semantic advisor decision: model-backed structured recommendation.

The semantic advisor returns:

- `mode`: `REACT` or `PLAN_EXECUTE`.
- `reason`: concise machine-facing reason.
- `requiresDeepResearch`.
- `requiresEvidenceAudit`.
- `requiresDocumentComposer`.
- `confidence`: numeric `0.0` to `1.0`.

The decider accepts the semantic advisor only when:

- JSON is valid.
- mode is known.
- confidence meets the configured threshold.
- `PLAN_EXECUTE` recommendations include at least one required subagent boundary.

Otherwise, it returns the deterministic fallback.

## Trace Contract

`agent.step.completed` for `mode-selection` must expose:

- `mode`
- `reason`
- `decisionSource`: `semantic` or `fallback`
- `fallbackReason`
- `semanticConfidence` when available

This keeps the front-end process panel honest: it can explain why multi-Agent collaboration appeared without inventing hidden agents.

## Non-Goals

- No new visible Planner Agent.
- No parallel execution runtime.
- No change to F018 child-agent names.
- No rewrite of `AgentIntentRouter` tool selection in this slice.
- No durable citation carry-through.

## Acceptance Criteria

- A RED/GREEN test proves a complex natural-language report request that avoids existing trigger phrases can route to `PLAN_EXECUTE` via semantic decision.
- A test proves simple chat can remain `REACT` when the semantic advisor says so.
- A test proves invalid advisor output falls back to deterministic behavior.
- A trace test proves mode-selection includes `decisionSource`, `fallbackReason`, and confidence metadata.
- Existing F018 regression tests remain green.
