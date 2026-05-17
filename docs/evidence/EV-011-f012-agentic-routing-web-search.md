---
id: EV-011
doc_kind: evidence
status: completed
scope: feature
feature_ids: [F012]
created: 2026-05-11
---
# F012 Agentic Routing and Tavily Web Search Evidence

## Summary

F012 implemented a main-Agent routing layer and Tavily web search tool integration. Simple chat now bypasses Paper RAG and web search, explicit or inferred web needs can call Tavily, local research continues to use Paper RAG, and mixed requests can combine paper and web evidence without blurring source boundaries.

## Evidence

### Focused F012 Regression Suite

Command:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=AgentIntentRouterTest,TavilyWebSearchClientTest,ProjectAgentRoutingTest,ProjectEvidenceBoundaryTest' test
```

Result:

- BUILD SUCCESS
- Tests run: 39
- Failures: 0
- Errors: 0
- Covered intent routing, Tavily degraded/success mapping, main-Agent tool selection, web evidence persistence, and source-type telemetry.

### Full Backend Suite

Initial command:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' test
```

Initial result:

- Failed while loading `ProjectRunEventFlowTest` Spring context.
- Root cause: Testcontainers PostgreSQL returned `FATAL: sorry, too many clients already`.
- Follow-up isolation command `-Dtest=ProjectRunEventFlowTest` passed with 4 tests, 0 failures, 0 errors.

Stabilized full-suite command:

```powershell
& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dspring.datasource.hikari.maximum-pool-size=2' test
```

Result:

- BUILD SUCCESS
- Tests run: 161
- Failures: 0
- Errors: 0

## Implemented Behavior

- `SIMPLE_CHAT`: answers conversationally using default context; does not call Paper RAG, L3 memory recall, or Tavily.
- `MEMORY_RECALL`: calls L3 memory recall for previous-discussion/history style prompts.
- `PROJECT_RAG`: calls Paper RAG for project/local research questions.
- `WEB_SEARCH`: calls Tavily for explicit web/freshness/search requests, including explicit requests when `allowWebSupplement=false`.
- `PROJECT_RAG_WITH_WEB`: calls Paper RAG and Tavily for mixed local evidence plus current external information.
- `PLANNING`: preserves the existing plan/execute path.

## Source Boundary

- Paper evidence remains `source_type=paper`.
- Tavily evidence is persisted as `source_type=web`.
- Degraded Tavily results are not persisted as fake evidence.
- `retrieval.completed.toolsUsed` reports actual tool calls, not merely intended tools.
- `evidence.evaluated.sourceTypes` reports the evidence source families available to the answer.

## Known Limitations

- Routing is currently deterministic/rule-first, not an LLM classifier. This is intentional for the first slice because it is easier to test and prevents a new opaque dependency from deciding tool calls.
- Tavily requires `TAVILY_API_KEY`; missing configuration degrades gracefully.
- This Feature does not add a full multi-agent delegation runtime.
- This Feature does not add a rich UI timeline for tool calls.
- Web results are answer evidence, not durable ingested project sources.

## Rollback

Rollback can remove the F012 router/web-search classes and restore `SupervisorService` to the previous Paper-RAG-first path. Data rollback is not required because web evidence uses the existing evidence-source table and source-type contract.
