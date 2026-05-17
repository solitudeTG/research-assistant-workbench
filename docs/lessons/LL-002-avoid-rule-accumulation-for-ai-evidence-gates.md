---
id: LL-002
doc_kind: lesson
status: active
feature_ids: [F018]
created: 2026-05-15
evidence:
  - ../evidence/EV-017-f018-multi-agent-evidence-grounded-workflow.md
---
# Avoid Rule Accumulation for AI Evidence Gates

## Lesson

When a workflow needs semantic evidence relevance, deterministic rules may reject obvious garbage but must not become the mechanism that promotes candidates into trusted evidence.

## Trigger

F018 manual validation repeatedly exposed unrelated or low-quality evidence in the generated report. The first fixes moved filtering earlier and added topic rules, but the next validation still produced irrelevant biomedical or news snippets for a satellite-interference research question. The user correctly identified that the implementation had become a rule-patching loop instead of real multi-agent evidence work.

## Root Cause

The system blurred two responsibilities:

- Hygiene filtering: deterministic cleanup for empty pages, navigation text, code/markup, administrative metadata, references, and fragments.
- Semantic promotion: deciding whether a hygienic candidate actually supports the user's research question.

Putting both inside `EvidenceCurator` made every bad example look like it needed another keyword rule. That approach can pass narrow fixtures while making the AI workflow less trustworthy, because the code is pretending to understand relevance through brittle topic lists.

## Protection

- `EvidenceCurator` is now hygiene-only and has a regression test proving it does not reject off-topic-looking but hygienic text by topic keyword.
- `EvidenceGateAgent` owns semantic promotion before audit/composition. Production uses a model-backed implementation; tests use a deterministic fake to make the boundary reproducible.
- `MultiAgentPlanExecuteLoop` sends only gate-accepted evidence to `Evidence Audit Agent` and `Document Composer Agent`.
- `Document Composer Agent` translates internal gate accounting into user-facing evidence-limit language instead of exposing pipeline internals in reports.
- `SPEC-F018.7` is superseded by `SPEC-F018.8` so future work does not reuse deterministic topic-keyword promotion as the current design.

## Recurrence Check

This failure can recur whenever a future agent responds to a bad AI output by adding another domain keyword, blacklist, or regex without first asking which boundary owns the semantic decision. The warning sign is a growing list of examples such as biomedical, CAR-T, news, code, or unrelated papers inside a component that should only clean or format data.

Before adding a new rule, future agents should classify it:

- If the rule removes structurally invalid content, keep it as hygiene.
- If the rule decides whether content is relevant to the user's claim or question, put it behind an evidence gate or another semantic agent boundary.

## Source

- Evidence: [EV-017-f018-multi-agent-evidence-grounded-workflow.md](../evidence/EV-017-f018-multi-agent-evidence-grounded-workflow.md)
- Current spec: [F018.8-evidence-gate-reset-spec.md](../specs/F018.8-evidence-gate-reset-spec.md)
