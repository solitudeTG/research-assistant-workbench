---
id: F005
doc_kind: feature
status: completed
owner: codex
created: 2026-05-10
updated: 2026-05-10
parent_feature: F002
---
# Project-Scoped Source Status Machine

## Goal

Upgrade source ingestion from temporary document upload behavior into a project-scoped source library action. The source library exposes stage-by-stage progress for parsing, fetching, indexing, extracting, depositing, failure stage recording, and retry.

## Current Status

Completed for the F005 boundary. Review fixes cover explicit JSON source type validation, direct list response shape, concrete stage failure recording, JSON note import with a default title, and failed note retry through the note/text pipeline.

## Scope

- In scope: project-scoped source import, list, get, and retry API.
- In scope: PDF/note and web status chains.
- In scope: concrete `failureStage` recording and failed-source retry.
- In scope: `source.status.changed` events through the F004 workbench event publisher.
- Out of scope: Agent orchestration, retrieval evidence boundaries, candidate confirmation, feedback, and UI.

## Acceptance Criteria

- PDF/note supports `uploaded -> parsing -> indexing -> extracting -> indexed -> depositing -> deposited`.
- Web supports `submitted -> fetching -> extracting -> indexed -> depositing -> deposited`.
- Processing failures record the concrete failed stage.
- Failed sources can be retried and publish another status event.
- Required Maven acceptance command passes.

## Contract

- API: `POST /api/projects/{projectId}/sources`, `GET /api/projects/{projectId}/sources`, `GET /api/projects/{projectId}/sources/{sourceId}`, and `POST /api/projects/{projectId}/sources/{sourceId}/retry`.
- Events: `source.status.changed`.
- Data: `source_document`.

## Evidence

- [EV-004-f005-project-source-status-machine.md](../evidence/EV-004-f005-project-source-status-machine.md)

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)
- Evidence: [EV-004-f005-project-source-status-machine.md](../evidence/EV-004-f005-project-source-status-machine.md)

## Next Step

Continue with F006 agent run event flow. Do not expand F005 into orchestration, evidence boundaries, candidates, feedback, or UI.
