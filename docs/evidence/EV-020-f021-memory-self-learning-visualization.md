---
id: EV-020
doc_kind: evidence
status: completed
date: 2026-05-16
feature_ids: [F021]
---
# EV-020 F021 Memory Self-Learning Visualization

## Evidence

F021 makes the existing L1/L2/L3 memory, candidate confirmation, knowledge board, and feedback score loop visible as a verifiable self-learning workflow. It does not add a new storage table, vector store, reranker, user profile system, or parallel Agent runtime.

## Implemented Capability

- L3 `memory.hit` and `memory.completed` are projected as context-only trace state and kept separate from citation evidence counts.
- `feedback.applied` carries rating, feedback score, affected evidence source IDs, updated evidence source count, and updated chunk count through backend events and frontend trace projection.
- Retrieval ranking and retrieval diagnostics expose feedback score influence with focused backend coverage.
- The first-level `认知 / Knowledge` workspace separates global cognition notes, confirmed project knowledge, pending candidates, and recent learning changes.
- The research process UI shows a memory/self-learning loop covering L1 working memory, L3 recall, candidate flow, and feedback application.

## Stitch Artifacts

- Cognition workspace screen: `projects/6874803135901272290/screens/8f3141d323bf48ec9e505b1728fe938d`
- Session memory loop screen: `projects/6874803135901272290/screens/72cdb73e24d74d239e91f48c2587e573`
- Local design exports:
  - `.stitch/designs/f021-cognition-workspace.html`
  - `.stitch/designs/f021-cognition-workspace.png`
  - `.stitch/designs/f021-session-memory-loop.html`
  - `.stitch/designs/f021-session-memory-loop.png`
- Local browser smoke screenshot:
  - `.stitch/designs/f021-local-knowledge-smoke.png`

## Verification

- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 38 tests pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- `mvn -Dtest=ProjectRunEventFlowTest,ProjectAgentToolsTest,ProjectFeedbackServiceTest,PaperRagServiceTest,RetrievalDiagnosticsControllerTest test`: 30 tests pass.
- `mvn -Dtest=KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest test`: 17 tests pass.
- Local visual smoke: static server on `127.0.0.1:52021`, Edge headless CDP clicked `[data-workspace-target='knowledge']`; DOM assertion returned `activeWorkspace=knowledge`, `activeRail=认知 / Knowledge`, and all four expected sections present.

## 2026-05-16 Status Audit

The post-handoff audit checked whether F021 was actually implemented or only marked completed in Harness docs. Result: the implementation exists in the current worktree and focused verification is fresh, but the work still needs commit hygiene before it can be treated as a Git-level completed delivery.

Audit verification:

- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 42 tests pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectRunEventFlowTest,ProjectAgentToolsTest,ProjectFeedbackServiceTest,PaperRagServiceTest,RetrievalDiagnosticsControllerTest,KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest,SystemControllerTest,ProjectAgentRoutingTest' test`: 71 tests pass, BUILD SUCCESS.
- `python .\scripts\knowledge_check.py`: pass.
- `git diff --check`: pass, with LF/CRLF warnings only.
- Docker live HTTP smoke on `http://localhost:8080/`: 200 response, static page contains `global-cognition-panel` and `data-knowledge-action`.
- Docker live HTTP smoke on `http://localhost:8080/api/system/global-knowledge`: 200 response.

Delivery hygiene note:

- `.stitch/browser-profiles/` is browser runtime cache and is ignored so it does not pollute `git status`.
- F021 remains mixed with F020 and F016 follow-up changes in the current worktree; commit splitting or review should treat those boundaries explicitly.

## Follow-Up Fix

After the Knowledge workspace shipped, the global cognition cards still showed the fallback text `后端认知投影尚未加载` in the live UI. Root cause: F021 created the UI slots and read path for `app.globalKnowledge`, but the project workbench bootstrap never loaded a stable L2 global cognition endpoint. The old `globalKnowledge` payload only existed inside legacy `/api/sessions/{sessionKey}` detail responses, which the project-scoped workbench does not call.

Fix:

- Added `GET /api/system/global-knowledge`, backed by `GlobalKnowledgeService.snapshot()`.
- Loaded that snapshot during `workbench-app.js` bootstrap into `app.globalKnowledge`.
- Added focused coverage in `SystemControllerTest` and `f002-workbench-model.test.mjs`.

Follow-up verification:

- `mvn -Dtest=SystemControllerTest test`: 3 tests pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 39 tests pass.
- `python .\scripts\knowledge_check.py`: pass.
- `git diff --check`: pass, with LF/CRLF warnings only.

Second follow-up:

The live UI still appeared wrong for an unconfigured workspace because the frontend treated an empty string from the loaded snapshot as if the backend projection had not loaded. Actual data source analysis showed the intended source order is `global_knowledge_note` table first, then `${APP_STORAGE_ROOT:-./storage}/memory/{USER.md,SOUL.md,Research_state.md}`. In the local workspace, `storage/memory` does not exist, so an unconfigured system legitimately returns empty strings.

Fix:

- `readGlobalCognitionNote` now treats an existing empty snapshot field as loaded-but-empty via `Object.hasOwn(global, camelKey)`.
- Empty loaded notes now render as `尚未记录这类稳定认知。只有显式写入后才会在这里显示。`
- Only a missing/null note renders the backend-not-loaded fallback.

Second follow-up verification:

- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 40 tests pass.
- `mvn -Dtest=SystemControllerTest test`: 3 tests pass.
- `python .\scripts\knowledge_check.py`: pass.
- `git diff --check`: pass, with LF/CRLF warnings only.

Third follow-up:

The user confirmed the underlying issue was not only the fallback copy: the workspace had no configured L2 data yet, and the L2 global cognition cards should be directly editable in the Knowledge page. The source-of-truth model remains the existing `global_knowledge_note` table plus the file mirror under `${APP_STORAGE_ROOT:-./storage}/memory`, instead of creating a new memory store.

Fix:

- Added `PATCH /api/system/global-knowledge` for explicit user edits to one L2 cognition note.
- Added `GlobalKnowledgeService.set(...)`, which upserts `global_knowledge_note` and writes the matching `storage/memory/{USER.md,SOUL.md,Research_state.md}` file mirror.
- Changed the Knowledge workspace global cognition cards into editable textareas with per-card save buttons for `USER`, `SOUL`, and `RESEARCH_STATE`.
- Initialized local sample L2 data in ignored `storage/memory/*.md` files and wrote the same UTF-8 content into the running Docker Postgres `global_knowledge_note` table.
- Rebuilt and restarted the Docker app so `http://localhost:8080` serves the new endpoint and editable UI.

Stitch follow-up artifact:

- Editable L2 cognition screen: `projects/6874803135901272290/screens/ce3f802b0d4147c6bea5f09e7ed6409b`
- Local browser smoke screenshot:
  - `.stitch/designs/f021-l2-editable-local-smoke.png`

Third follow-up verification:

- Red backend check before implementation: `mvn -Dtest=SystemControllerTest#patchGlobalKnowledgeUpdatesOneL2CognitionNoteAndReturnsSnapshot test` failed because `GlobalKnowledgeService.set(...)` did not exist.
- Red frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "F021 global cognition cards"` failed because the editable save path did not exist.
- `mvn -Dtest=SystemControllerTest#patchGlobalKnowledgeUpdatesOneL2CognitionNoteAndReturnsSnapshot test`: 1 test passes.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "F021 global cognition cards"`: pass.
- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 41 tests pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- `mvn -Dtest=SystemControllerTest test`: 4 tests pass.
- Docker Maven fallback after local `mvn` was unavailable: one-off `docker build -f - .` test layer ran `mvn -B -Dtest=SystemControllerTest test`; 4 tests pass, 0 failures, BUILD SUCCESS.
- Docker smoke: `docker compose up -d --build app` rebuilt the app and restarted `research-assistant-workbench-app-1`.
- UTF-8 API smoke with Node `fetch`: `/api/system/global-knowledge` returned Chinese sample text with expected code points, confirming the PowerShell mojibake display was only a terminal decoding artifact.
- Browser smoke with Edge headless CDP on `http://localhost:8080`: Knowledge workspace had `textareaCount=3`, `saveCount=3`, `hasStableBadge=true`, and `hasLoadedSample=true`.
- `python .\scripts\knowledge_check.py`: pass.
- `git diff --check`: pass, with LF/CRLF warnings only.

Fourth follow-up:

The user rejected the three-card editable layout as visually cluttered and pointed out the duplicated content: each L2 note appeared once as prose and again as a textarea. The intended interaction is a tabbed document editor: `USER.md`, `SOUL.md`, and `Research_state.md` are peer tabs; only the active note is visible; the default state is read-only; editing is explicit.

Fix:

- Used Stitch to redesign the L2 global cognition section as a unified tabbed editor region.
- Replaced the three side-by-side `cognition-note` cards with a single `global-cognition-panel`.
- Added `activeGlobalCognitionNote` and `globalCognitionEditingNote` UI state so tab selection and edit mode are independent.
- Default view now renders one `.global-cognition-reader` and one compact `编辑` button.
- Edit view swaps the reader for one textarea and shows `保存` / `取消`; the reader and textarea are never visible at the same time.
- Switching tabs exits edit mode and shows the newly selected note in read-only mode.
- Removed the old `cognition-note-grid` host class and the duplicate section-level stable badge, leaving one stable badge in the active note toolbar.

Stitch follow-up artifact:

- Tabbed L2 cognition screen: `projects/6874803135901272290/screens/6f82c0786206493bbe2eb83049da87d6`
- Local browser smoke screenshots:
  - `.stitch/designs/f021-l2-tabs-final-local-smoke.png`
  - `.stitch/designs/f021-l2-tabs-focused-local-smoke.png`

Fourth follow-up verification:

- Red frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "F021 global cognition notes"` failed because `activeGlobalCognitionNote` and the tabbed/read-only state did not exist.
- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 41 tests pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- Docker smoke: `docker compose up -d --build app` rebuilt the app and restarted `research-assistant-workbench-app-1`.
- Browser smoke with Edge headless CDP on `http://localhost:8080`: read state had `tabCount=3`, `activeTabs=1`, `textareaCount=0`, `readerCount=1`, `editCount=1`, `saveCount=0`, `oldCards=0`, `stableBadgeCount=1`; edit state had `textareaCount=1`, `readerCount=0`, `saveCount=1`, `cancelCount=1`; tab switch to `SOUL.md` returned to read state.

Fifth follow-up:

The user found two remaining visual issues in the tabbed L2 editor: the active note title was repeated inside the content panel, and the selected tab looked like the inactive state because active was white while inactive was blue-gray. This was a pure UI semantics correction; the data source and save API stayed unchanged.

Fix:

- Used Stitch to refine the L2 editor: remove the duplicated active-note title block and reverse the active/inactive tab surface emphasis.
- Removed `.global-cognition-title` from the active content panel.
- Kept only toolbar metadata plus the stable cognition badge and edit/save/cancel action controls.
- Changed inactive tabs to use the paper surface and active tab to use the stronger cool blue-gray surface.

Stitch follow-up artifact:

- L2 editor visual polish screen: `projects/6874803135901272290/screens/b98549bc27854256ae26f2b7a5d20170`
- Local browser smoke screenshot:
  - `.stitch/designs/f021-l2-tabs-polished-local-smoke.png`

Fifth follow-up verification:

- Red frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "F021 global cognition notes"` failed because `.global-cognition-title` still existed and active tab background still used the paper surface.
- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 41 tests pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- Docker smoke: `docker compose up -d --build app` rebuilt the app and restarted `research-assistant-workbench-app-1`.
- Browser smoke with Edge headless CDP on `http://localhost:8080`: `titleClassCount=0`, `toolbarRepeatsTitle=false`, `activePanelUserMdCount=0`, `textareaCount=0`, `readerCount=1`, and active/inactive tab computed backgrounds differed (`oklch(0.95 0.008 245)` vs `oklch(0.99 0.004 80)`).

Sixth follow-up:

The user found that both L2 confirmed project knowledge and this-round candidates stayed empty, and main chat never triggered candidate or knowledge behavior. Root cause: the frontend already sent `extractKnowledgeCandidates`, and the F008 candidate/knowledge-board repositories existed, but `SupervisorService` never consumed the flag after answer/evidence persistence. The Knowledge page also rendered `data-knowledge-action="new"` controls without a JavaScript handler. This was a code wiring gap, not a usage mistake.

Fix:

- Added `KnowledgeCandidateExtractionService` to create one pending candidate from a project answer only when candidate extraction is enabled, the answer is non-empty, the persisted evidence assessment is `SUFFICIENT`, and persisted `evidence_source` rows exist.
- Wired `SupervisorService` to run extraction immediately after answer/evidence persistence for both ReAct project chat and Plan-Execute answers.
- Kept the boundary explicit: extraction creates `knowledge_candidate` only; it never writes `knowledge_entry`. L2 confirmed project knowledge still requires user accept/edit-and-accept or manual creation.
- Preserved weak evidence safety: web supplement or other `WEAK` answers do not become candidates even when source rows exist.
- Bound the Knowledge workspace `新建知识` action to the existing `POST /api/projects/{projectId}/knowledge-board/entries` API for explicit manual confirmed knowledge creation.

Sixth follow-up verification:

- Red backend check before implementation: `mvn -Dtest=ProjectAgentRoutingTest#extractKnowledgeCandidatesCreatesPendingCandidateFromGroundedAnswer+extractKnowledgeCandidatesFlagDisabledDoesNotCreateCandidate test` failed because no candidate rows were created.
- Red frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "manual knowledge action"` failed because `workbench-app.js` had no `data-knowledge-action` handler.
- `mvn -Dtest=ProjectAgentRoutingTest#planExecutePersistsAcceptedCitationSources+extractKnowledgeCandidatesCreatesPendingCandidateFromGroundedAnswer+extractKnowledgeCandidatesFlagDisabledDoesNotCreateCandidate+extractKnowledgeCandidatesSkipsWeakWebSupplementAnswer test`: 4 tests pass.
- `mvn -Dtest=ProjectAgentRoutingTest,KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest test`: 36 tests pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "manual knowledge action"`: 42 tests pass.

Independent review hardening:

- Review finding: Plan-Execute with only web citations could still be mislabeled as `LOCAL_EVIDENCE/SUFFICIENT` if the audit recommended local evidence; that would allow web-only answers to become candidates.
- Fix: Plan-Execute `LOCAL_EVIDENCE` now requires at least one persisted paper citation. Web-only citations are persisted as answer evidence but remain `WEB_SUPPLEMENT/WEAK`, so candidate extraction skips them.
- Review finding: `candidate.created` events did not include `statement`, `sourceTypes`, or `evidenceSourceIds`, so the live UI projection could briefly render an empty-looking candidate.
- Fix: `candidate.created` now publishes the full candidate payload needed by the frontend projection.
- Review-hardening red checks before implementation: `ProjectAgentRoutingTest#planExecuteWebOnlyCitationsStayWeakAndDoNotGenerateCandidates+extractKnowledgeCandidatesCreatesPendingCandidateFromGroundedAnswer` failed with `LOCAL_EVIDENCE/SUFFICIENT` for web-only Plan-Execute and missing `statement` in the candidate event payload.
- Review-hardening green check: `mvn -Dtest=ProjectAgentRoutingTest#planExecuteWebOnlyCitationsStayWeakAndDoNotGenerateCandidates+extractKnowledgeCandidatesCreatesPendingCandidateFromGroundedAnswer test`: 2 tests pass.
- Final focused rerun after review fixes: `mvn -Dtest=ProjectAgentRoutingTest,KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest test`: 37 tests pass.
- Final frontend reruns after review fixes: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 42 tests pass; `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.

Seventh follow-up:

The user started the service and found that answers still had no thumbs up/down controls. Root cause: F021 had the backend feedback endpoint and `feedback.applied` projection, but the frontend never rendered an answer-level feedback control or called `POST /api/projects/{projectId}/answers/{answerId}/feedback`. A second restart-specific gap also existed: historical session messages did not expose `answerId`, so reloaded assistant answers could not attach feedback controls even after adding the UI.

Fix:

- Added answer-level `点赞` / `点踩` controls to assistant messages with persisted `answerId`.
- Added checked evidence-source selectors so users can choose `evidenceSourceIds` for answer feedback.
- Wired feedback submission to `POST /api/projects/{projectId}/answers/{answerId}/feedback`, then applied the returned feedback event to the trace and refreshed evidence sources so updated scores can become visible in later observability.
- Preserved feedback as a separate answer/evidence signal; memory context remains historical context, not paper citation evidence.
- Added `answerId` to project session message responses and mapped historical assistant messages back to persisted `assistant_answer` rows so feedback controls survive service restart and session reload.

Seventh follow-up verification:

- Red frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "answer feedback controls"` failed because no `data-feedback-action`, `submitAnswerFeedback`, or feedback endpoint call existed.
- Red backend check before implementation: `mvn -Dtest=ProjectControllerTest#listsAssistantMessagesWithAnswerIdForFeedbackAfterRestart test` failed because reloaded assistant messages did not include `answerId`.
- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 43 tests pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- `mvn -Dtest=ProjectControllerTest#listsAssistantMessagesWithAnswerIdForFeedbackAfterRestart test`: 1 test passes.
- `mvn -Dtest=ProjectControllerTest,ProjectFeedbackServiceTest test`: 15 tests pass.
- Docker smoke after rebuild: `docker compose up -d --build app` rebuilt and restarted `research-assistant-workbench-app-1`.
- Live HTTP smoke after restart: `/` returned 200; served `/js/workbench-app.js` contains `data-feedback-action`, `submitAnswerFeedback`, and `/feedback`; served `/css/workbench.css` contains `.answer-feedback` and `.answer-feedback__button`.
- `git diff --check`: pass, with LF/CRLF warnings only.

Eighth follow-up / F021.1 reset:

The user requested a zero-base review because the completed label no longer matched the live behavior. The reset found three closed-loop gaps: confirmed project knowledge was visible in the Knowledge workspace but was not consumed as an L2 signal in the next answer; backend `feedback.applied` events had no `sessionId` / `runId`, so real run SSE projection could not see them; reloaded session messages had `answerId` but still lacked `runId`, so research-process trace recovery after restart was incomplete.

Fix:

- Reopened F021 as active and created `docs/plans/F021.1-self-learning-closed-loop-reset-plan.md`.
- Added `assistant_answer.run_id` via `V8__f021_assistant_answer_run_id.sql`.
- Persisted `runId` with project assistant answers and returned `answerId` + `runId` in project session message history.
- Resolved feedback event context from the persisted answer before publishing `feedback.applied`, so the event now carries `projectId`, `sessionId`, `runId`, and `answerId`.
- Added confirmed project knowledge lookup from `knowledge_entry` and passed it into `ProjectAgentRequest` as project knowledge context.
- Added confirmed project knowledge to the main project agent prompt as context explicitly marked not citation evidence.
- Published confirmed project knowledge as `memory.hit` with `memoryLayer=L2`, `sourceType=project_knowledge`, and `contextOnly=true`; it contributes to memory context counts but does not create evidence sources or citation counts.

F021.1 reset verification:

- Subagent attempt: two implementation workers were dispatched for L2 recall and feedback/history trace, but both disconnected before completion. Their partial patches were reviewed and integrated manually.
- RED backend check before L2 implementation: `mvn -Dtest=ProjectAgentRoutingTest#confirmedKnowledgeEntryPublishesL2ProjectKnowledgeMemoryTraceWithoutCitationEvidence,ProjectFeedbackServiceTest#answerFeedbackUpdatesLinkedEvidenceAndChunksAndPublishesEvent,ProjectControllerTest#listsAssistantMessagesWithAnswerIdForFeedbackAfterRestart test` first failed at compile on missing `runId`, then failed because no L2/project_knowledge memory hit existed.
- Focused green checks:
  - `mvn -Dtest=ProjectAgentRoutingTest#confirmedKnowledgeEntryPublishesL2ProjectKnowledgeMemoryTraceWithoutCitationEvidence,ProjectFeedbackServiceTest#answerFeedbackUpdatesLinkedEvidenceAndChunksAndPublishesEvent,ProjectControllerTest#listsAssistantMessagesWithAnswerIdForFeedbackAfterRestart test`: L2 and history checks pass; the feedback method name in that combined command did not match and was rerun separately.
  - `mvn -Dtest=ProjectFeedbackServiceTest#upFeedbackIncreasesOnlySameProjectLinkedEvidenceAndChunkScoresAndPublishesEvent test`: 1 test passes, including `sessionId` and `runId` assertions on `feedback.applied`.
  - `mvn -Dtest=ProjectAgentRoutingTest,ProjectRunEventFlowTest,ProjectFeedbackServiceTest,ProjectControllerTest,KnowledgeCandidateControllerTest,KnowledgeBoardControllerTest,DefaultProjectAgentToolLoopTest test`: 62 tests pass.
  - `node --check src\main\resources\static\js\workbench-app.js`: pass.
  - `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 43 tests pass.
  - `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- Docker smoke after rebuild:
  - `docker compose up -d --build app` rebuilt and restarted `research-assistant-workbench-app-1`.
  - Flyway applied `V8__f021_assistant_answer_run_id.sql` on the running Docker database, moving schema version from 7 to 8.
  - Live HTTP smoke: `/` returned 200; served `/js/workbench-app.js` contains `data-feedback-action`, `submitAnswerFeedback`, and `/feedback`; served `/css/workbench.css` contains `.answer-feedback` and `.workspace--knowledge`.

Ninth follow-up:

The user observed that the answer feedback evidence checkboxes all displayed only `paper`, so the user could not tell which evidence source was being liked or disliked. Root cause: the backend answer-evidence endpoint returns concrete `snippet` text, but the feedback checkbox label fell back to `sourceType` whenever no title was present. That made multiple paper evidence rows visually indistinguishable even though the evidence IDs were correct.

Fix:

- Added a frontend model helper that builds feedback evidence labels from `sourceType` plus the best concrete text available: `sourceTitle`, title, citation metadata, `snippet`, summary, quote, source ID, or evidence ID.
- Changed answer feedback checkbox rendering to use the helper instead of raw `sourceType`.
- Added a bounded text span and tooltip so long evidence excerpts remain readable without breaking the chat layout.

Ninth follow-up verification:

- RED frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs` failed because `evidenceFeedbackLabel` was not exported yet.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 44 tests pass.
- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- `python .\scripts\knowledge_check.py`: ok.
- `git diff --check`: pass, with LF/CRLF warnings only.
- Docker smoke after rebuild: `docker compose up -d --build app` rebuilt and restarted `research-assistant-workbench-app-1`.
- Live HTTP smoke after restart: `/` returned 200; served `/js/workbench-app.js` contains `evidenceFeedbackLabel` and `answer-feedback__evidence-text`; served `/js/workbench-model.js` contains `function evidenceFeedbackLabel`.

Tenth follow-up:

The user observed that clicking "write to knowledge base" created a confirmed knowledge entry, but the same item still appeared in the candidate review queue. Root cause: candidate acceptance correctly transitions the backend candidate from `pending` to `accepted` and creates a `knowledge_entry`, but the frontend `renderCandidates()` view rendered all `app.candidates` regardless of status. That made historical accepted candidates appear as if they were still reviewable.

Fix:

- Added `pendingKnowledgeCandidates()` as the shared frontend model boundary for candidate review queues.
- Reused that boundary for the cognition workspace pending-candidate projection.
- Changed candidate review rendering to iterate only pending candidates, while leaving accepted candidates in state for trace/history consistency.

Tenth follow-up verification:

- RED frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs` failed because `pendingKnowledgeCandidates` was not exported yet.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 45 tests pass.
- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
- `python .\scripts\knowledge_check.py`: ok.
- `git diff --check`: pass, with LF/CRLF warnings only.
- Docker smoke after rebuild: `docker compose up -d --build app` rebuilt and restarted `research-assistant-workbench-app-1`.
- Live HTTP smoke after restart: `/` returned 200; served `/js/workbench-app.js` contains `pendingKnowledgeCandidates` and iterates `for (const candidate of candidates)`; served `/js/workbench-model.js` contains `function pendingKnowledgeCandidates`.

Eleventh follow-up:

The user challenged the answer-feedback UX: asking a research user to inspect and select every evidence chunk is a false product requirement. The zero-base design conclusion is that users should judge answers, claims, and evidence semantics; the system should internally attribute those signals to evidence/chunk/ranking. The visible checkbox list was removed from the main feedback path.

Fix:

- Replaced visible evidence/chunk checkboxes in answer feedback with answer-level thumbs and reason chips.
- Added `feedbackPayloadForAnswer()` as the frontend model boundary for internal attribution. Helpful feedback and citation-related negative feedback can still carry same-answer evidence IDs internally; `missing_evidence` stays answer-level and does not punish existing evidence.
- Added backend fallback attribution: when `evidenceSourceIds` is omitted, helpful feedback attributes to same-answer evidence, while `missing_evidence` records only answer-level feedback.
- Added `reason` to `feedback.applied` payload so observability can distinguish why feedback changed evidence/chunk scores.
- Kept chunk score propagation and affected count visibility; removed only the user-facing chunk selection task.

Eleventh follow-up verification:

- Harness gates: Start Gate required retrieval; F021 Feature/spec/F021.1 plan/EV-020 were read; Vision Gate approved the scope because it better matches the original self-learning goal without removing feedback score propagation.
- Delegation: independent read-only subagent review confirmed the UI/contract split and recommended answer-level feedback plus backend fallback attribution.
- RED frontend check before implementation: `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs` failed because `feedbackPayloadForAnswer` was not exported and the app still contained `data-feedback-evidence`.
- Frontend green checks:
  - `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs`: 46 tests pass.
  - `node --test src\main\resources\static\tests\workbench-model.test.mjs`: 6 tests pass.
  - `node --check src\main\resources\static\js\workbench-app.js`: pass.
- Backend test intent added:
  - `ProjectFeedbackServiceTest#helpfulFeedbackWithoutEvidenceSourceIdsInternallyAttributesAnswerEvidence`
  - `ProjectFeedbackServiceTest#missingEvidenceFeedbackWithoutEvidenceSourceIdsRecordsAnswerLevelOnly`
  - `ProjectFeedbackServiceTest#projectAnswerFeedbackEndpointCanAttributeHelpfulFeedbackWithoutEvidenceIds`
- Backend verification caveat: the local shell did not have `mvn`; running Maven inside a container could not execute these Testcontainers tests because the nested container had no Docker socket. `docker compose build app` compiled the Java application successfully with tests skipped.
- `python .\scripts\knowledge_check.py`: ok.
- `git diff --check`: pass, with LF/CRLF warnings only.
- Docker smoke after rebuild: `docker compose up -d --build app` rebuilt and restarted `research-assistant-workbench-app-1`.
- Live HTTP smoke after restart: `/` returned 200; served `/js/workbench-app.js` contains `data-feedback-reason` and `feedbackPayloadForAnswer`, and does not contain `data-feedback-evidence`.

## Browser Tool Note

The Codex in-app Browser runtime setup timed out during local verification. I recorded that as a fallback rather than treating it as a pass. Edge headless was used as the browser rendering fallback and produced the local smoke screenshot listed above.

## Residual Risk

- Focused tests and live HTTP smoke validate the backend/frontend contracts and running page shell. A full manual live provider demo remains useful before an external presentation because provider behavior, real uploaded sources, and end-to-end candidate generation quality depend on local runtime data and model/tool behavior.
- `USER.md`, `SOUL.md`, and `Research_state.md` are represented as global cognition slots in the UI; if the backend later exposes a richer global cognition endpoint, the existing UI projection should consume it through `cognitionWorkspace.globalCognition`.

## 2026-05-17 F021.1 Closeout Audit

The F021.1 reset is closed from the code path rather than only from Harness labels.

Evidence checked:

- Current branch `codex/f021-self-learning-visualization` is synced to origin and contains `8fb74d4 Implement F021 self-learning visualization`.
- The later F016 commit `3dfc440 feat: add answer-run retrieval diagnostics` is already on top of the branch; this closeout changes only F021 Harness documents and does not alter F016 code.
- L2 confirmed knowledge path exists in code: confirmed project knowledge is loaded from `knowledge_entry`, passed through `ProjectAgentRequest`, included in the main agent prompt, and emitted as L2 `project_knowledge` memory trace with `contextOnly=true`.
- Feedback path exists in code: answer feedback resolves persisted assistant answer context before publishing `feedback.applied`, so the event includes `sessionId`, `runId`, and `answerId`.
- History recovery path exists in code: project session message history returns `answerId` and `runId` for assistant messages when the persisted answer can be matched.

Verification run on 2026-05-17:

- `node --check src\main\resources\static\js\workbench-app.js`: pass.
- `node --test src\main\resources\static\tests\f002-workbench-model.test.mjs --test-name-pattern "F021|answer feedback controls|project session history"`: 46 tests pass.
- `mvn -Dtest=ProjectAgentRoutingTest#confirmedKnowledgeEntryPublishesL2ProjectKnowledgeMemoryTraceWithoutCitationEvidence,ProjectFeedbackServiceTest#upFeedbackIncreasesOnlySameProjectLinkedEvidenceAndChunkScoresAndPublishesEvent,ProjectControllerTest#listsAssistantMessagesWithAnswerIdForFeedbackAfterRestart test`: 3 tests pass, BUILD SUCCESS.

Closeout verdict:

- Feature status: completed.
- Evidence level: standard.
- Completion claim allowed: yes.
- Remaining caution: strict replay of feedback events that are published after `run.completed` is not reopened here. The backend now carries the correct event identity, and the UI applies the feedback API result immediately for the normal interaction path. Any change to post-terminal run replay semantics should be scoped as a later event-stream hardening item.
