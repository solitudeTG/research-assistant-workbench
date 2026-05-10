---
id: F007
doc_kind: feature
status: completed
owner: codex
created: 2026-05-10
updated: 2026-05-10
parent_feature: F002
---
# Retrieval Evidence Boundary

## Goal

Separate L3 Memory Recall, Paper RAG, and Web Supplement boundaries so project answers expose a verifiable evidence state instead of mixing historical memory, current paper evidence, and web context into one unauditable source.

## Current Status

completed.

## Scope

- In scope: retrieval mode layering for project answers.
- In scope: paper evidence source persistence and answer evidence retrieval.
- In scope: retrieval trace payloads on `retrieval.completed`.
- In scope: `EvidenceLevel` values `SUFFICIENT`, `WEAK`, `NONE`.
- In scope: `AnswerMode` values `LOCAL_EVIDENCE`, `LOCAL_WEAK_EVIDENCE`, `WEB_SUPPLEMENT`, `REFUSAL`.
- Out of scope: candidate confirmation, `feedbackScore` update behavior, UI work, and F010 live timeline behavior.

## Acceptance Criteria

- Strong paper evidence outputs `SUFFICIENT` and `LOCAL_EVIDENCE`.
- Weak local evidence with web supplement allowed outputs `WEAK` and `WEB_SUPPLEMENT`.
- No paper evidence with web supplement disabled outputs `NONE` and `REFUSAL`.
- L3 memory can enrich retrieval context but does not count as current paper evidence.
- `retrieval.completed` exposes real retrieval state, including retrieval mode, paper evidence count, memory recall count, top paper score, and web supplement allowance.
- `evidence.evaluated` exposes `evidenceState`, `outputMode`, and `citationCount`.
- Answer paper evidence is persisted in `evidence_source` and can be read through `/api/projects/{projectId}/answers/{answerId}/evidence`.
- Acceptance command passes:
  `& 'C:\Users\HUAWEI\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' '-Dtest=ProjectEvidenceBoundaryTest,PaperRagServiceTest,MemoryRecallServiceTest,QueryRewriteServiceTest' test`

## Contracts

- API: `GET /api/projects/{projectId}/answers/{answerId}/evidence`.
- Events: `retrieval.completed`, `evidence.evaluated`.
- Data: `assistant_answer.evidence_state`, `assistant_answer.answer_mode`, `evidence_source`.
- Boundary: memory recall is context only; paper chunks are the only persisted current paper evidence in this slice.

## Evidence

- Evidence record: [EV-006-f007-retrieval-evidence-boundary.md](../evidence/EV-006-f007-retrieval-evidence-boundary.md)

## Links

- Parent Feature: [F002-next-generation-research-workbench.md](F002-next-generation-research-workbench.md)
- Spec: [F002-next-generation-research-workbench-spec.md](../specs/F002-next-generation-research-workbench-spec.md)
- Plan: [F002-next-generation-research-workbench-plan.md](../plans/F002-next-generation-research-workbench-plan.md)

## Next Step

Start F008 candidate confirmation and knowledge board with TDD. Do not include feedback scoring or UI work in F008 beyond API-consumable state.
