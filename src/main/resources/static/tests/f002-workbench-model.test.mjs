import test from "node:test";
import assert from "node:assert/strict";

import {
    applyCandidateAction,
    applySseEvent,
    selectSession,
    startEditingCandidate
} from "../js/workbench-model.js";

function baseState() {
    return {
        activeProjectId: "project-1",
        activeSessionId: "session-a",
        selectedSidebarView: "knowledge-board",
        activeAnswerContext: {
            answerId: "answer-1",
            citationCount: 2,
            candidateCount: 1
        },
        sources: [
            {
                id: "source-1",
                title: "Planning paper",
                type: "pdf",
                status: "parsing",
                failureStage: null,
                depositedKnowledgeCount: 0
            }
        ],
        currentAnswer: {
            answerId: "answer-1",
            text: "",
            status: "idle",
            evidenceState: null,
            citationCount: 0
        },
        candidates: [],
        editingCandidateId: null,
        knowledgeBoard: {
            sections: [
                { section: "core_concept", title: "Core concepts", entries: [] },
                { section: "method_route", title: "Method route", entries: [] }
            ]
        },
        processedEventIds: []
    };
}

test("selecting a session updates activeSessionId and clears activeAnswerContext", () => {
    const state = baseState();

    const next = selectSession(state, "session-b");

    assert.equal(next.activeSessionId, "session-b");
    assert.equal(next.activeAnswerContext, null);
    assert.equal(state.activeSessionId, "session-a");
    assert.deepEqual(state.activeAnswerContext, {
        answerId: "answer-1",
        citationCount: 2,
        candidateCount: 1
    });
});

test("source.status.changed updates the matching source row", () => {
    const state = baseState();

    const next = applySseEvent(state, {
        eventId: "event-1",
        eventType: "source.status.changed",
        sourceId: "source-1",
        payload: {
            sourceId: "source-1",
            status: "deposited",
            failureStage: null,
            depositedKnowledgeCount: 3,
            title: "Planning paper v2"
        }
    });

    assert.equal(next.sources[0].status, "deposited");
    assert.equal(next.sources[0].depositedKnowledgeCount, 3);
    assert.equal(next.sources[0].title, "Planning paper v2");
    assert.equal(state.sources[0].status, "parsing");
});

test("answer.delta updates the center answer state", () => {
    const state = baseState();

    const next = applySseEvent(state, {
        eventId: "event-2",
        eventType: "answer.delta",
        answerId: "answer-1",
        payload: {
            answerId: "answer-1",
            text: "Local evidence supports the claim."
        }
    });

    assert.equal(next.currentAnswer.answerId, "answer-1");
    assert.equal(next.currentAnswer.text, "Local evidence supports the claim.");
    assert.equal(next.currentAnswer.status, "streaming");
});

test("candidate.created adds a pending candidate without adding a knowledge entry", () => {
    const state = baseState();

    const next = applySseEvent(state, {
        eventId: "event-3",
        eventType: "candidate.created",
        payload: {
            id: "candidate-1",
            title: "Agent review boundary",
            statement: "Candidates require user confirmation before deposition.",
            suggestedSection: "core_concept",
            evidenceSourceIds: ["evidence-1"]
        }
    });

    assert.equal(next.candidates.length, 1);
    assert.equal(next.candidates[0].id, "candidate-1");
    assert.equal(next.candidates[0].status, "pending");
    assert.deepEqual(next.knowledgeBoard.sections.flatMap((section) => section.entries), []);
});

test("knowledge.entry.created adds an entry to the correct section", () => {
    const state = baseState();

    const next = applySseEvent(state, {
        eventId: "event-4",
        eventType: "knowledge.entry.created",
        payload: {
            id: "entry-1",
            section: "method_route",
            title: "Evidence-first loop",
            content: "Answers should cite current project evidence.",
            evidenceStatus: "confirmed"
        }
    });

    const coreSection = next.knowledgeBoard.sections.find((section) => section.section === "core_concept");
    const methodSection = next.knowledgeBoard.sections.find((section) => section.section === "method_route");
    assert.equal(coreSection.entries.length, 0);
    assert.equal(methodSection.entries.length, 1);
    assert.equal(methodSection.entries[0].id, "entry-1");
});

test("duplicate SSE event IDs are ignored", () => {
    const state = baseState();
    const event = {
        eventId: "event-5",
        eventType: "answer.delta",
        payload: {
            answerId: "answer-1",
            text: "First delta."
        }
    };

    const next = applySseEvent(state, event);
    const duplicate = applySseEvent(next, {
        ...event,
        payload: {
            answerId: "answer-1",
            text: "Duplicate should not apply."
        }
    });

    assert.equal(duplicate.currentAnswer.text, "First delta.");
    assert.deepEqual(duplicate.processedEventIds, ["event-5"]);
});

test("entity fallback IDs are deterministic for pure SSE model helpers", () => {
    const candidateEvent = {
        eventId: "event-candidate",
        eventType: "candidate.created",
        payload: {
            title: "Fallback candidate",
            statement: "Missing candidate ids should still be deterministic."
        }
    };
    const entryEvent = {
        eventId: "event-entry",
        eventType: "knowledge.entry.created",
        payload: {
            section: "core_concept",
            title: "Fallback entry",
            content: "Missing entry ids should still be deterministic."
        }
    };

    const first = applySseEvent(applySseEvent(baseState(), candidateEvent), entryEvent);
    const second = applySseEvent(applySseEvent(baseState(), candidateEvent), entryEvent);

    assert.equal(first.candidates[0].id, "candidate-event-candidate");
    assert.equal(first.knowledgeBoard.sections.find((section) => section.section === "core_concept").entries[0].id, "entry-event-entry");
    assert.deepEqual(first, second);
});

test("evidence.evaluated can hydrate evidence source rows from event payload", () => {
    const next = applySseEvent(baseState(), {
        eventId: "event-evidence",
        eventType: "evidence.evaluated",
        answerId: "answer-1",
        payload: {
            evidenceState: "SUFFICIENT",
            outputMode: "LOCAL_EVIDENCE",
            citationCount: 1,
            evidenceSources: [
                {
                    id: "evidence-1",
                    sourceType: "paper",
                    snippet: "Relevant paragraph"
                }
            ]
        }
    });

    assert.equal(next.currentAnswer.evidenceState, "SUFFICIENT");
    assert.equal(next.currentAnswer.citationCount, 1);
    assert.equal(next.evidenceSources.length, 1);
    assert.equal(next.evidenceSources[0].id, "evidence-1");
});

test("candidate action helpers keep candidate edits local to candidate state", () => {
    const state = {
        ...baseState(),
        candidates: [
            {
                id: "candidate-1",
                status: "pending",
                title: "Draft",
                suggestedSection: "core_concept"
            }
        ]
    };

    const editing = startEditingCandidate(state, "candidate-1");
    const ignored = applyCandidateAction(editing, "candidate-1", "ignore");

    assert.equal(editing.editingCandidateId, "candidate-1");
    assert.equal(ignored.candidates[0].status, "ignored");
    assert.equal(ignored.editingCandidateId, null);
    assert.deepEqual(ignored.knowledgeBoard.sections.flatMap((section) => section.entries), []);
});
