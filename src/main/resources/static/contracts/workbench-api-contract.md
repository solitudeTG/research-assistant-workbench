# Research Workbench Frontend Contract

This contract documents the F002 project API and SSE surface consumed by the static F010 workbench UI.

## Primary F002 API

- `GET /api/system/ping`
  - Used for the top-bar connectivity indicator.
- `GET /api/projects`
  - Loads the project list. F010 uses the first available project and falls back to an empty sample state when no project exists.
- `GET /api/projects/{projectId}/sessions`
  - Loads project research sessions.
- `POST /api/projects/{projectId}/sessions`
  - Creates a lightweight research session from the left column.
- `GET /api/projects/{projectId}/sources`
  - Populates the left source library.
- `POST /api/projects/{projectId}/sources`
  - Imports a file into the project source library.
- `POST /api/projects/{projectId}/sessions/{sessionId}/messages`
  - Sends the center composer question and returns `messageId`, `answerId`, `streamRunId`, and `sseUrl`.
- `GET /api/projects/{projectId}/knowledge-board`
  - Populates the right knowledge board sections.
- `GET /api/projects/{projectId}/candidates`
  - Populates the candidate confirmation view.
- `POST /api/projects/{projectId}/candidates/{candidateId}/accept`
- `POST /api/projects/{projectId}/candidates/{candidateId}/mark-unverified`
- `POST /api/projects/{projectId}/candidates/{candidateId}/ignore`
  - Updates candidate status. Accepted candidates are expected to become knowledge entries through the F008 backend path.

## SSE Stream

The project message response supplies:

```text
/api/projects/{projectId}/sessions/{sessionId}/runs/{runId}/events
```

F010 listens for these F002 event names:

- `run.started`
- `retrieval.started`
- `retrieval.completed`
- `evidence.evaluated`
- `answer.delta`
- `answer.completed`
- `run.completed`
- `run.failed`
- `source.status.changed`
- `candidate.created`
- `knowledge.entry.created`

The frontend stores processed `eventId` values and ignores duplicate SSE events. `candidate.created` only adds a pending candidate; only `knowledge.entry.created` updates the confirmed knowledge board.

## Compatibility Functions

The static app keeps clearly named compatibility functions for legacy demo endpoints:

- `compatibilityListLegacyDocuments`
- `compatibilityUploadLegacyDocument`
- `compatibilitySendLegacyChatStream`

These functions are fallback paths only. The main workbench flow is project-scoped F002 API plus run SSE.

## Deliberate F010 Limits

- F010 does not implement backend APIs, broad search, web retrieval, account preferences, or multi-tenant UI.
- F010 does not invent source-scoped live SSE beyond the existing F002 run event projection.
- F010 keeps missing backend data as empty/sample UI state instead of blocking the static workbench.
