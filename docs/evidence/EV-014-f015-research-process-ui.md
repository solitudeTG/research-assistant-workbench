---
id: EV-014
doc_kind: evidence
status: completed
created: 2026-05-12
updated: 2026-05-12
feature_ids: [F015]
---
# EV-014 F015 Research Process UI Rendering

## Evidence

F015 follow-up UI rendering now consumes the existing `agentTraces[runId]` frontend model and displays a Stitch-aligned research process module under assistant answers.

Verified behavior:

- The EventSource listener now subscribes to F015 trace event types including `tool.*`, `retrieval.hit`, `memory.*`, and `evidence.gap.detected`.
- Assistant messages are linked to the active `runId` and `answerId` once the project message endpoint returns.
- A research process module renders under assistant answers, showing step status, tool timeline, evidence hits, memory hits, and evidence boundary notes.
- The module uses the existing restrained workbench visual system: warm paper surface, cool archive panels, compact status chips, and dense research-tool spacing.
- The UI remains honest: it labels logical work as steps and sources, not as true parallel subagents.

Commands:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
node --test src/main/resources/static/tests/workbench-model.test.mjs
git diff --check -- src/main/resources/static/js/workbench-app.js src/main/resources/static/css/workbench.css
```

Results:

```text
f002-workbench-model.test.mjs: 14/14 passing
workbench-model.test.mjs: 3/3 passing
git diff --check: exit code 0, CRLF normalization warnings only
```

Browser smoke:

```powershell
chrome --headless=new --disable-gpu --virtual-time-budget=5000 --dump-dom http://127.0.0.1:4173/
```

Result: static workbench shell rendered without `SyntaxError` or `ReferenceError` in dumped DOM output.

## Notes

This slice does not add a new backend contract. It consumes the F015 event/model contract already recorded in EV-013. Full true parallel Supervisor-Worker runtime remains out of scope.
