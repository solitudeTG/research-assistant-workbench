---
id: LL-001
doc_kind: lesson
status: active
feature_ids: [F002, F011]
created: 2026-05-10
evidence:
  - ../evidence/EV-010-f002-implementation-validation.md
---
# Explicit Validation Fixtures for Integrated Harness Tests

## Lesson

Integrated validation tests must create their own required retrieval and database fixtures explicitly. They must not rely on implicit mock behavior, sequence assumptions, or container startup timing that only happens to work in a narrow test order.

## Trigger

During F011 validation, the parent full backend verification first failed in `Phase1HappyPathTest.uploadThenAskGroundedQuestion`: the response returned `answerMode=REFUSAL`, no citations, and no `$.citations[0].documentId`.

The same closeout also exposed full-suite database lifecycle fragility around the shared PostgreSQL Testcontainer and order-sensitive document IDs.

## Root Cause

The happy-path test expected grounded answer behavior without explicitly seeding the vector-search result from indexed chunks. Other tests also assumed stable sequence values or datasource timing that could change when the full suite initialized contexts in a different order.

## Protection

- `Phase1HappyPathTest` now seeds `vectorSearchPort.search(...)` from indexed chunks and asserts chunks exist after upload indexing.
- `PostgresIntegrationTest` starts the shared PostgreSQL Testcontainer before dynamic datasource properties are resolved.
- `ChatControllerTest` uses the inserted `research_document` id instead of assuming sequence value `1`.

## Recurrence Check

This class of failure can recur whenever a validation test proves an integrated user path while relying on hidden fixtures. Future integrated Harness tests should fail because the product path is broken, not because the test environment accidentally omitted an implicit setup step.

## Source

- Evidence: [EV-010-f002-implementation-validation.md](../evidence/EV-010-f002-implementation-validation.md)

