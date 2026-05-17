---
id: EV-015
doc_kind: evidence
status: active
created: 2026-05-12
updated: 2026-05-15
feature_ids: [F016]
---
# EV-015 F016 Retrieval Observability Slice

## Scope Verified

This evidence records the first F016 backend slice. It does not close the full Feature.

Implemented capability:

- Added `QueryRewritePlan.strategy` and `fallbackReason` so query rewrite behavior is observable instead of implicit.
- Added `RetrievalObservation`, `RetrievalBackendStats`, and `ZeroHitReason` as the first structured observation model for `paper_rag`.
- Extended `RagResult` with an optional observation while preserving the existing 3-argument constructor.
- `PaperRagService.retrieve(...)` now records per-backend query count, hit count, duration, rewrite strategy, merged/reranked/returned counts, and `NO_BACKEND_HITS` classification for empty backend results.
- `RetrievalTraceRepository.save(...)` now receives observation details in the rerank JSON payload without adding a new table.
- `ProjectAgentTools.paperRag(...)` publishes `retrieval.query.rewritten` and `retrieval.completed` trace events when an observation exists.
- `tool.completed.data` now carries `retrievalObservationSummary` so consumers do not need to parse `resultSummary`.
- Added startup warmup for `LocalVectorSearchPort` so already-indexed document chunks are restored into the in-memory vector index after backend restart.

## Evidence

Live diagnosis after a user rerun showed the observation data was useful enough to distinguish the actual failure mode:

- Recent `retrieval_trace.rerank_result_json.observation` rows for session `8` showed `NO_BACKEND_HITS` on many calls.
- Aggregate backend stats showed keyword retrieval returned hits, but vector retrieval returned `0` hits across the inspected calls.
- The local runtime was configured with `AI_VECTORSTORE_TYPE=none` and `AI_EMBEDDING_PROVIDER=none`.
- The application therefore used `LocalVectorSearchPort`, an in-memory vector index, while the database still contained indexed documents and chunks.
- Root cause: after backend restart, existing `document_chunk` rows remained in the database, but the local in-memory vector index was empty until a document was reprocessed.
- Fix slice: `LocalVectorIndexWarmup` rehydrates indexed documents into `LocalVectorSearchPort` on startup.

Follow-up live diagnosis after warmup showed the retrieval layer had recovered:

- Latest inspected session `9` had `8` retrieval calls, `0` zero-hit calls, `90` vector hits, and `70` returned chunks.
- Previous inspected session `8` had `25` retrieval calls, `16` zero-hit calls, `0` vector hits, and `29` returned chunks.
- Remaining mismatch was no longer retrieval: the latest answer had persisted paper evidence but was stored as `REFUSAL/NONE` because `EvidenceBoundaryService` treated local chunks below score `0.35` as no evidence.
- Fix slice: evidence boundary now treats empty scoped paper results as `NONE`, but any non-empty scoped paper result below the sufficient threshold as `WEAK`.
- Startup warmup was also hardened to skip safely when no `LocalVectorSearchPort` bean exists, which keeps pgvector/test contexts from failing application startup.

Agent tool-call control follow-up:

- Live session `10` showed retrieval quality had recovered, but future runs still needed a guardrail against repeated or overly broad `paper_rag` calling.
- `ProjectAgentTools.paperRag(...)` now deduplicates identical normalized queries within one answer run.
- `ProjectAgentTools.paperRag(...)` now enforces a conservative budget of 3 backend `paper_rag` calls per answer run and returns structured `skipped=true, reason=paper_rag_budget_exhausted` metadata after the budget is exhausted.
- This is intentionally enforced at the tool boundary rather than in the prompt so the constraint is deterministic and testable.

Diagnostics workspace follow-up:

- Added a project/session scoped read-only diagnostics endpoint:
  `GET /api/projects/{projectId}/sessions/{sessionId}/retrieval-diagnostics`.
- The endpoint reuses existing `retrieval_trace.rerank_result_json.observation` payloads instead of introducing a new aggregate table.
- The response exposes retrieval-call counts, zero-hit counts, returned scoped chunk totals, rewrite strategy distribution, zero-hit reason distribution, per-backend stats, bounded retrieval queries, and bounded top chunks.
- Added an Observability first-level workspace in the static workbench UI, aligned with the Stitch diagnostic design direction while keeping the existing compact research-workspace style.
- The UI shows summary metrics, taxonomy chips, a retrieval-event table, and a detail inspector. It calls the real diagnostics endpoint in normal mode and uses static sample data only in sample mode.

UI regression follow-up:

- Manual testing showed the left primary workspace rail no longer switched workspaces.
- Browser console evidence showed `Uncaught SyntaxError: Identifier 'sampleRetrievalDiagnostics' has already been declared` in `workbench-app.js`.
- Root cause: the diagnostics UI slice introduced a second top-level `sampleRetrievalDiagnostics()` definition. `node --check` did not catch this browser-module strict-mode failure.
- Fix: removed the duplicate definition and added a frontend regression test that rejects duplicate top-level function declarations in `workbench-app.js`.

Evidence diagnostics UI follow-up:

- The Observability workspace now presents the same retrieval-backed data through a researcher-facing `证据诊断` lens instead of a raw `检索诊断` lens.
- The drawer navigation separates `证据诊断`, `检索调用`, `查询改写`, `引用链路`, and `异常`, while keeping the underlying session-scoped diagnostics endpoint unchanged.
- The top strip now explains coverage, retrieval efficiency, zero-hit count, and evidence-chain state. `引用链路` intentionally remains `待核验` when returned chunks exist because final citation-to-observation linkage is not yet implemented.
- The main pane adds `本轮判断` and explicit table headers: `调用`, `策略`, `查询`, `返回`, `命中来源`, `状态`, and `时间`.
- The inspector keeps engineering fields available but translates them into `检索细分`, `改写查询`, `返回片段`, and `证据缺口`.
- `最近运行` is visible but disabled with `跨运行聚合待后续补齐`, because the current API is still session-scoped and does not support cross-run aggregation.

Answer evidence diagnostics clarity follow-up:

- User review showed the `证据诊断` page was still understandable mainly as an engineering log, not as a researcher's answer to "can I trust this response?".
- Stitch was used as a visual intent anchor for a clearer `回答取证诊断` page: first explain what the page audits, then show what was found, what is still unproven, and where to inspect the next step.
- A parallel read-only subagent review independently recommended replacing internal retrieval language with researcher-facing labels.
- The page title is now `回答取证诊断`, with the purpose statement `检查本轮回答背后的论文证据：哪些结论已有材料支持，哪些还不能使用。`
- The four summary cards now use task-language labels: `找到多少证据`, `问了几种查法`, `没有找到的次数`, and `是否进入引用`.
- The main conclusion now starts with `一句话结论`, classifying the current answer as no evidence, weak evidence, or candidate evidence before showing counts.
- The table is now a `检索步骤` explanation rather than an event log: `步骤`, `系统动作`, `查法数`, `候选材料`, `主要来源`, `这意味着`, and `时间`.
- The inspector now starts from `这一步代表什么` and surfaces `原始问题`, `找到的材料`, `为什么没找到`, `对结论的影响`, and `下一步建议` before lower-level retrieval stats.
- Zero-hit reasons now map to user actions such as importing papers, changing keywords, checking project scope, or relaxing filters.

## Verification Commands

Focused backend verification:

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=LocalVectorIndexWarmupTest,QueryRewriteServiceTest,PaperRagServiceTest,ProjectAgentToolsTest' test
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,LocalVectorIndexWarmupTest' test
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=ProjectAgentToolsTest' test
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=RetrievalDiagnosticsControllerTest,TraceControllerTest' test
node --test src/main/resources/static/tests/f002-workbench-model.test.mjs
node --test src/main/resources/static/tests/workbench-model.test.mjs
node --check src/main/resources/static/js/workbench-app.js
node --check src/main/resources/static/js/workbench-model.js
git diff --check
.\scripts\rebuild-dev.cmd
python scripts/knowledge_check.py
```

Result:

```text
BUILD SUCCESS
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Node frontend tests: 15/15 and 3/3 passed.
JavaScript syntax checks: passed.
git diff --check: passed with CRLF warnings only.
Docker rebuild: passed, app container restarted.
Headless Chrome console smoke after rebuild: no `Uncaught`, `TypeError`, `ReferenceError`, or `SyntaxError` from `workbench-app.js`.
Evidence diagnostics UI follow-up: `node --test src/main/resources/static/tests/f002-workbench-model.test.mjs` passed with 35/35 tests; `node --test src/main/resources/static/tests/workbench-model.test.mjs` passed with 6/6 tests; both JavaScript syntax checks passed; `git diff --check` passed with CRLF warnings only; `.\scripts\rebuild-dev.cmd` completed with `BUILD SUCCESS`; HTTP smoke after rebuild confirmed `/` contains `证据诊断`, `本轮判断`, and `最近运行`, and does not contain `检索诊断`.
Answer evidence diagnostics clarity follow-up: `node --test src/main/resources/static/tests/f002-workbench-model.test.mjs` passed with 35/35 tests; `node --test src/main/resources/static/tests/workbench-model.test.mjs` passed with 6/6 tests; both JavaScript syntax checks passed; `python scripts/knowledge_check.py` passed; `git diff --check` passed with CRLF warnings only; `.\scripts\rebuild-dev.cmd` completed with `BUILD SUCCESS`; HTTP smoke after rebuild confirmed `/` contains `回答取证诊断`, `检查本轮回答背后的论文证据`, `查法数`, and `这意味着`, and does not contain `检索诊断`.
```

Harness validation:

```powershell
python scripts/knowledge_check.py
```

Result:

```text
ok
```

## Residual Risk

- This slice does not yet distinguish vector pre-scope and post-scope hits. Current stats use the existing search results, so `SCOPE_FILTERED_EMPTY` still needs a stronger test and likely vector search contract work.
- This slice does not yet link final persisted citations back to a retrieval observation id or tool-call index.
- No dedicated `retrieval_observation` table was added. This intentionally avoids an ADR-triggering storage decision until aggregation needs are proven.
- The diagnostics endpoint is session scoped, not cross-session trend analytics. A dedicated table or time-series dashboard remains a future decision if aggregate analysis becomes necessary.
- The current Docker app image must be rebuilt before the new backend endpoint and static UI are visible in container-based manual testing.
