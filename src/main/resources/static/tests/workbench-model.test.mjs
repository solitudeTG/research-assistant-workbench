import test from "node:test";
import assert from "node:assert/strict";

import {
    applyStreamEvent,
    buildProjectSessionDeleteUrl,
    buildAnalysisViewModel,
    calculateRestoredScrollTop,
    createWorkbenchState,
    deleteSessionFromState,
    normalizeDocument
} from "../js/workbench-model.js";

test("normalizeDocument fills presentation defaults for sparse payloads", () => {
    const normalized = normalizeDocument({
        documentId: 7,
        originalFileName: "agent-paper.pdf"
    });

    assert.deepEqual(normalized, {
        documentId: 7,
        title: "agent-paper.pdf",
        originalFileName: "agent-paper.pdf",
        status: "UNKNOWN",
        totalChunks: 0,
        totalTokens: 0,
        createdAt: "",
        updatedAt: "",
        tone: "idle"
    });
});

test("buildAnalysisViewModel returns graceful placeholders when analysis is absent", () => {
    const viewModel = buildAnalysisViewModel(null, "Research Assistant");

    assert.equal(viewModel.summary, "索引完成后，这里会展示论文的结构化总结。");
    assert.equal(viewModel.abstractText, "暂未提取到摘要内容。");
    assert.deepEqual(viewModel.methods, []);
    assert.deepEqual(viewModel.contributions, []);
    assert.deepEqual(viewModel.keywords, ["Research Assistant"]);
    assert.deepEqual(viewModel.outline, ["正在等待结构化大纲"]);
});

test("applyStreamEvent supports current SSE events and richer trace updates", () => {
    let state = {
        answerParts: [],
        citations: [],
        trace: [],
        telemetry: { status: "idle", answerMode: "PENDING" }
    };

    state = applyStreamEvent(state, "message", "Grounded");
    state = applyStreamEvent(state, "retrieval-step", {
        label: "Keyword retrieval",
        detail: "Using lexical and vector search"
    });
    state = applyStreamEvent(state, "done", {
        answerMode: "LOCAL_EVIDENCE",
        citations: [{ chunkId: 3, documentId: 2, chunkIndex: 0, excerpt: "Abstract section" }]
    });

    assert.equal(state.answerParts.join(" "), "Grounded");
    assert.equal(state.trace.length, 1);
    assert.equal(state.trace[0].label, "Keyword retrieval");
    assert.equal(state.telemetry.status, "done");
    assert.equal(state.telemetry.answerMode, "LOCAL_EVIDENCE");
    assert.equal(state.citations[0].chunkId, 3);
});

test("buildProjectSessionDeleteUrl builds the project scoped delete endpoint", () => {
    assert.equal(
            buildProjectSessionDeleteUrl({ projectId: "p 1", sessionId: "s/2" }),
            "/api/projects/p%201/sessions/s%2F2"
    );
});

test("deleteSessionFromState removes active session and selects the next one", () => {
    const state = createWorkbenchState({
        activeProjectId: "p1",
        activeSessionId: "s1",
        sessions: [
            { id: "s1", title: "First" },
            { id: "s2", title: "Second" }
        ],
        messages: [{ id: "m1" }],
        evidenceSources: [{ id: "e1" }],
        candidates: [{ id: "c1", sessionId: "s1" }],
        activeAnswerContext: { answerId: "a1" },
        currentAnswer: {
            answerId: "a1",
            text: "answer",
            status: "completed",
            evidenceState: "WEAK",
            outputMode: "LOCAL_WEAK_EVIDENCE",
            citationCount: 1
        },
        agentTraces: { run1: { summary: { toolCount: 1 } } },
        processedEventIds: ["evt-1"]
    });

    const next = deleteSessionFromState(state, "s1");

    assert.equal(next.activeSessionId, "s2");
    assert.deepEqual(next.sessions.map((session) => session.id), ["s2"]);
    assert.deepEqual(next.messages, []);
    assert.deepEqual(next.evidenceSources, []);
    assert.deepEqual(next.activeAnswerContext, null);
    assert.deepEqual(next.candidates, []);
    assert.equal(next.currentAnswer.status, "idle");
    assert.deepEqual(next.agentTraces, {});
    assert.deepEqual(next.processedEventIds, []);
});

test("calculateRestoredScrollTop preserves latest conversation position after content height changes", () => {
    assert.equal(
            calculateRestoredScrollTop(
                    { scrollTop: 700, scrollHeight: 1000, clientHeight: 260 },
                    820
            ),
            520
    );
    assert.equal(
            calculateRestoredScrollTop(
                    { scrollTop: 40, scrollHeight: 1000, clientHeight: 260 },
                    820
            ),
            0
    );
});
