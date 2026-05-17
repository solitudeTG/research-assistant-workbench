---
id: F012
doc_kind: feature
status: completed
owner: codex
updated: 2026-05-11
parent_feature: F002
evidence:
  - ../evidence/EV-011-f012-agentic-routing-web-search.md
---
# Agentic Routing and Tavily Web Search

## Goal

Make project chat behave like a mainstream agentic assistant instead of sending every user input through local RAG. The main Agent loads L1/L2 context by default, decides which tools are needed, and can call Paper RAG, L3 memory recall, Tavily web search, or planning only when the request warrants it.

The first product pain fixed by this Feature is the awkward response to simple input such as `hello`/`ni hao`: these messages should be answered conversationally, not with a missing-RAG-evidence refusal.

## Scope

- In scope: main-Agent intent routing for simple chat, project/local research, web search, local+web mixed research, memory recall, and planning.
- In scope: L1 working memory and L2 stable memory summary remain default context; L3 memory recall is a conditional tool.
- In scope: Paper RAG, Tavily Web Search, and Memory Recall are represented as peer tools selected by the main Agent flow.
- In scope: Tavily search integration with provider status, title, URL, snippet, score, and degraded behavior when configuration or the upstream request fails.
- In scope: evidence persistence distinguishes `paper` and `web` sources instead of fabricating paper evidence for external search results.
- In scope: run events expose actual tool calls through `intent`, `toolsUsed`, `webEvidenceCount`, `webSearchStatus`, and `sourceTypes`.
- Out of scope: autonomous multi-subagent runtime, long-running task queues, account authorization, multi-tenant behavior, full web crawling, full-page extraction, and automatic long-term ingestion of web results.

## Acceptance Criteria

- Simple interaction such as `hello`, `ni hao`, or `who are you` does not call Paper RAG or Tavily and does not return a missing-paper-evidence refusal.
- Explicit user requests such as `search the web`, `联网查询`, or `latest` can trigger Tavily directly; web search is not merely a fallback after RAG failure.
- Local research questions continue to call Paper RAG when project evidence is needed.
- Mixed local+freshness questions can call both Paper RAG and Tavily, and the answer/evidence records distinguish paper evidence from web supplements.
- L1/L2 context is loaded by default; L3 memory recall is called only for history/previous-discussion style requests.
- Missing Tavily API key or failed Tavily request returns recoverable degraded behavior and does not invent web evidence.
- Focused F012 backend tests pass.
- Harness validation passes.

## Result

Completed in this slice:

- Added `AgentIntentRouter` and routing decisions for simple chat, memory recall, project RAG, web search, mixed project+web, and planning.
- Added Tavily-backed `WebSearchPort` plus a no-op/degraded fallback when `TAVILY_API_KEY` is absent.
- Updated `SupervisorService` so tool calls are conditional and telemetry records the tools actually used.
- Persisted Tavily results as `source_type=web` evidence with URL/title/provider metadata.
- Added coverage for routing, Tavily response mapping/degradation, tool-call telemetry, web evidence persistence, and mixed paper+web source typing.

## Contracts

- API: continues to use `POST /api/projects/{projectId}/sessions/{sessionId}/messages`; `allowWebSupplement` remains the switch for implicit web supplementation, while explicit web requests can still call web search.
- Configuration: `TAVILY_API_KEY`; optional `TAVILY_BASE_URL`, `TAVILY_SEARCH_DEPTH`, and `TAVILY_MAX_RESULTS`.
- Events: `retrieval.completed` includes `intent`, `toolsUsed`, `webEvidenceCount`, and `webSearchStatus`; `evidence.evaluated` includes `sourceTypes`.
- Data: web search results are stored as evidence sources with `source_type=web` and citation metadata containing title, URL, provider, snippet, query, and rank.
- UI: this slice does not redesign the workbench. Existing event/evidence surfaces can distinguish web supplements; a richer tool timeline should be a later Feature.

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F012-agentic-routing-and-tavily-web-search-spec.md](../specs/F012-agentic-routing-and-tavily-web-search-spec.md)
- Plan: [F012-agentic-routing-and-tavily-web-search-plan.md](../plans/F012-agentic-routing-and-tavily-web-search-plan.md)
- ADR: [ADR-003-agentic-tool-routing-and-tavily.md](../decisions/ADR-003-agentic-tool-routing-and-tavily.md)
- Vision Gate: [VG-002-f012-before-development.md](../reviews/VG-002-f012-before-development.md)
- Evidence: [EV-011-f012-agentic-routing-web-search.md](../evidence/EV-011-f012-agentic-routing-web-search.md)

## Next Step

F012 is closed. Future work should open separate Features for an LLM-based router, richer UI tool timelines, durable web ingestion, or actual multi-agent task delegation.
