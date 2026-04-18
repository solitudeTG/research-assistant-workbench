# Changelog

## 2026-04-18

### Answer quality

- Fixed the bug where overview questions like "这篇论文研究了什么？" could fall back to the uploaded PDF filename.
- Upgraded overview and abstract questions so they first use structured analysis as a draft, then validate against local paper evidence.
- When local evidence is strong enough, overview answers now return as grounded answers with citations instead of always falling back to conservative weak-evidence wording.
- Expanded structured-answer handling for method and contribution questions so these question types prefer the extracted document analysis instead of generic fallback replies.

### Retrieval

- Removed the previous no-op vector retrieval fallback that returned empty vector results.
- Added a deterministic local embedding model and a local in-memory vector search fallback so vector retrieval remains available even without an external embedding provider.
- Enabled `pgvector` as the default vector store configuration path, while still keeping the local fallback available when the store bean is unavailable.

### Workbench UI

- Reworked the workbench into a cleaner chat-first Chinese interface.
- Removed visible "capability boundary", "compatibility", and future-facing fallback hints from the user-facing panels.
- Kept the active workflow focused on upload, document selection, questioning, structured analysis, and citation/trace inspection.

### Key commits

- `00b19b8 feat: improve grounded answers and stable vector retrieval`
- `b74ff0a feat: remove capability-boundary hints from workbench ui`
- `8b8a0be fix: ground overview answers with local evidence`
