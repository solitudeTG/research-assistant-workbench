---
id: EV-012
doc_kind: evidence
status: completed
updated: 2026-05-11
feature_ids: [F013]
---
# F013 Main Agent Tool-Calling Loop Evidence

## Summary

F013 replaces the project-message rule-first routing path with a main Agent tool-calling loop. The project answer flow now calls `ProjectAgentToolLoop`, exposes `web_search`, `paper_rag`, and `memory_recall` as Spring AI tools, and persists evidence/telemetry from the tools the main Agent actually used.

The original weather failure is addressed at the architecture boundary: the application no longer has to pre-classify "weather" by keyword for project chat. The main Agent receives available tools and decides whether to call web, paper RAG, or memory. If no relevant tool was used, the system must not synthesize web evidence or report web-search telemetry.

## Implementation

- Added `ProjectAgentRequest`, `ProjectAgentRun`, and `ProjectAgentToolLoop`.
- Added `DefaultProjectAgentToolLoop`, which calls `ChatClient.prompt().system(...).user(...).tools(projectAgentTools).call().content()`.
- Added `ProjectAgentTools` with Spring AI `@Tool` methods:
  - `web_search` delegates to `WebSearchPort` and records `tavily_web_search`.
  - `paper_rag` delegates to `PaperRagService`, filters chunks to the current `ProjectEvidenceScope`, and records `paper_rag`.
  - `memory_recall` delegates to `MemoryRecallPort` and records `memory_recall`.
- Updated `SupervisorService.answerProject(...)` to use `ProjectAgentToolLoop` for project messages, derive answer mode and retrieval mode from actual tool results, and publish `retrieval.completed.toolsUsed`.
- Preserved evidence boundaries: web evidence remains `source_type=web`; paper evidence remains `source_type=paper`; memory recall is context only.

## Provider Decision

The project currently uses Spring AI `1.1.2` with the OpenAI chat starter. The native Spring AI `ChatClient.tools(...)` path compiles and is covered by focused tests. No JSON action-loop fallback was added in F013 because adding a second Agent protocol would increase complexity before evidence shows the configured provider cannot use native tools.

Fallback trigger for a future feature: if a real configured provider fails to emit native tool calls for tool-requiring prompts, implement a bounded JSON action loop behind the same `ProjectAgentToolLoop` interface with validated action names and max tool rounds.

## Evidence

Commands run from `E:\Self-Project\research-assistant-workbench`:

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' -Dtest=DefaultProjectAgentToolLoopTest test
```

Result: passed.

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' -Dtest=ProjectAgentToolsTest test
```

Result: passed.

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' -Dtest=ProjectAgentRoutingTest test
```

Result: passed.

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' -Dtest=ProjectEvidenceBoundaryTest test
```

Result: passed.

```powershell
& 'C:\Users\HUAWEI\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd' '-Dtest=DefaultProjectAgentToolLoopTest,ProjectAgentToolsTest,ProjectAgentRoutingTest,ProjectEvidenceBoundaryTest' test
```

Result: passed, 29 tests, 0 failures, 0 errors.

## Known Limitations

- F013 validates the Spring AI native tool exposure path with tests; it does not perform a live external LLM/Tavily weather smoke test because that depends on local API keys and provider behavior.
- The old `AgentIntentRouter` remains available for legacy non-project chat paths and as historical reference; project messages now use `ProjectAgentToolLoop`.
- Python agent runtime, sub-agent orchestration, human checkpoints, and long-running task queues remain future features.

## Rollback

Rollback path is localized: restore `SupervisorService.answerProject(...)` to the previous `AgentIntentRouter`-based routing path and remove the `ProjectAgentToolLoop` injection. The database schema and evidence tables were not changed by F013.
