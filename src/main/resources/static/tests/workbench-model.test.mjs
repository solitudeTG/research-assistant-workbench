import test from "node:test";
import assert from "node:assert/strict";

import {
    applyStreamEvent,
    buildAnalysisViewModel,
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

    assert.equal(viewModel.summary, "Document analysis will appear here after indexing completes.");
    assert.equal(viewModel.abstractText, "No abstract has been extracted yet.");
    assert.deepEqual(viewModel.methods, []);
    assert.deepEqual(viewModel.contributions, []);
    assert.deepEqual(viewModel.keywords, ["Research Assistant"]);
    assert.deepEqual(viewModel.outline, ["Awaiting structured outline"]);
});

test("applyStreamEvent supports current SSE events and future richer trace events", () => {
    let state = {
        answerParts: [],
        citations: [],
        trace: [],
        telemetry: { status: "idle", answerMode: "PENDING" }
    };

    state = applyStreamEvent(state, "message", "Grounded");
    state = applyStreamEvent(state, "retrieval-step", {
        label: "Keyword retrieval",
        detail: "Using fallback lexical search"
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
