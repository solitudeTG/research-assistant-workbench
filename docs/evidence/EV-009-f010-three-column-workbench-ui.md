---
id: EV-009
feature: F010
status: completed
created: 2026-05-10
---
# EV-009 F010 Three-Column Workbench UI

## Evidence

### Scope

F010 implemented the static three-column research workbench UI and frontend state model only. It did not implement backend APIs, broad source search, web retrieval, account preferences, or multi-tenant UI.

### Red Evidence

Command:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Result before implementation: failed with exit code `1`.

Key output:

```text
SyntaxError: The requested module '../js/workbench-model.js' does not provide an export named 'applyCandidateAction'
```

This confirmed the new F010 model helper contract did not exist before implementation.

### Green Evidence

Command:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Result after implementation: passed with exit code `0`.

Key output:

```text
tests 9
pass 9
fail 0
```

The final test count includes parent-session regression coverage for deterministic fallback IDs and `evidence.evaluated` hydration of evidence rows.

### Additional Checks

Command:

```powershell
node --test src/main/resources/static/tests/workbench-model.test.mjs
```

Result: passed with exit code `0`, confirming the older exported helpers remain compatible.

Command:

```powershell
node --check src/main/resources/static/js/workbench-app.js
```

Result: passed with exit code `0`.

Command:

```powershell
node --check src/main/resources/static/js/workbench-model.js
```

Result: passed with exit code `0`.

### Browser Verification

Parent verification used bundled Node, Playwright, system Chrome, a temporary static HTTP server for `src/main/resources/static`, and mocked F002 API/SSE endpoints. The first `file://` attempt was rejected by Chrome module CORS, so the final browser pass intentionally used HTTP to match how the static UI is served.

Covered interactions:

- desktop viewport `1440x960` renders three non-overlapping columns from `.workbench-layout`.
- typed SSE events update the UI through F004/F006 wire names: `answer.delta`, `source.status.changed`, `evidence.evaluated`, `candidate.created`, and `knowledge.entry.created`.
- `evidence.evaluated` triggers `GET /api/projects/{projectId}/answers/{answerId}/evidence` and renders returned evidence rows.
- candidate edit mode renders inline controls and posts `edit-and-accept` to the F008 endpoint.
- knowledge board refresh shows the edited accepted entry.
- narrow viewport `390x900` has no horizontal document overflow and no buttons outside the viewport.

Result:

```text
F010 browser verification: ok
```

### Review Findings Resolved

The specification and quality reviews found three F010 blockers after the initial implementation:

- Browser verification was missing from the evidence record.
- Pure model helper fallback IDs used nondeterministic `Date.now()`-style values.
- The evidence tab switched views but did not hydrate evidence rows, and candidate edit only toggled state without a submit path.

Parent-session fixes added deterministic fallback IDs, evidence row hydration, inline candidate edit controls, `edit-and-accept` submission, and the browser verification above.

### Final Parent Checks

Command:

```powershell
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
```

Result: passed with exit code `0`.

Command:

```powershell
node --test src/main/resources/static/tests/workbench-model.test.mjs
```

Result: passed with exit code `0`.

Command:

```powershell
node --check src/main/resources/static/js/workbench-model.js
```

Result: passed with exit code `0`.

Command:

```powershell
node --check src/main/resources/static/js/workbench-app.js
```

Result: passed with exit code `0`.

Command:

```powershell
git diff --check
```

Result: passed with exit code `0`; Git reported CRLF normalization warnings only.

Command:

```powershell
python scripts\knowledge_check.py
```

Result: passed with exit code `0`.

Key output:

```text
knowledge_check: ok
```
### Implemented Files

- `src/main/resources/static/tests/f002-workbench-model.test.mjs`
- `src/main/resources/static/js/workbench-model.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/js/workbench-app.js`
- `src/main/resources/static/css/workbench.css`
- `src/main/resources/static/contracts/workbench-api-contract.md`
- `docs/features/F010-three-column-workbench-ui.md`
- `docs/evidence/EV-009-f010-three-column-workbench-ui.md`
- `docs/BACKLOG.md`

### Residual Risks

- The F002 SSE projection is replay-oriented; true live tailing beyond available run events remains outside F010.
- Source status updates are modeled and rendered, but a dedicated source-event subscription is not introduced in this slice.
- F010 browser verification used mocked F002 APIs for UI behavior. F011 remains responsible for full-system validation against the integrated backend.
