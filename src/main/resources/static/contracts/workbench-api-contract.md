# Research Workbench Frontend Contract

This note exists only to guide the static workbench UI. It does not change backend behavior.

## Live endpoints consumed today

- `GET /api/system/ping`
  - Purpose: lightweight connectivity indicator in the top bar.
- `GET /api/documents`
  - Purpose: populate the document library rail.
- `POST /api/documents/upload`
  - Purpose: upload a paper into the workspace.
- `GET /api/documents/{documentId}`
  - Purpose: poll indexing state after upload.
- `GET /api/documents/{documentId}/analysis`
  - Purpose: populate the structured analysis inspector when available.
- `GET /api/chat/stream?sessionKey=...&question=...&documentId=...`
  - Purpose: stream the answer tokens and final payload.

## SSE events handled now

- `heartbeat`
  - Current backend already emits this event.
- `message`
  - Current backend already emits token chunks through this event.
- `done`
  - Current backend already emits the final `ChatResponse` payload.

## Optional future SSE events already wired in the frontend

- `retrieval-start`
- `retrieval-step`
- `telemetry`
- `error`

If the backend starts emitting any of the events above, the static workbench will render them into the right-side trace and telemetry panels without requiring a layout rewrite.

## Intentional graceful degradations

- Session history is stored in browser `localStorage` until backend session APIs exist.
- The current workbench pins a single active document because SSE chat currently accepts one `documentId`.
- Missing document analysis returns empty-state cards instead of a fatal error.
