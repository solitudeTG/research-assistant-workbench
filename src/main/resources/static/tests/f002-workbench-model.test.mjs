import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

import {
    applyCandidateAction,
    applySseEvent,
    buildProjectMessageUrl,
    buildProjectSessionRenameUrl,
    buildProjectSessionMessagesUrl,
    createWorkbenchState,
    getWorkspaceVisibility,
    renameSessionTitle,
    normalizeProjectMessage,
    requireProjectChatContext,
    selectSession,
    selectWorkspace,
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

test("workbench app module does not redeclare top-level functions", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const declarations = [...source.matchAll(/^function\s+([A-Za-z_$][\w$]*)\s*\(/gm)].map((match) => match[1]);
    const duplicates = declarations.filter((name, index) => declarations.indexOf(name) !== index);

    assert.deepEqual([...new Set(duplicates)], []);
});

test("research process UI labels are not mojibake", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const researchProcessSource = source.slice(
            source.indexOf("function researchProcessPanel"),
            source.indexOf("function updateAssistantMessage")
    );

    assert.doesNotMatch(researchProcessSource, /灞曞紑|鏀惰捣|宸ュ叿浜嬩欢|璇佹嵁鍛戒腑|璁板繂|杈圭晫|鐮旂┒/);
});

test("session row actions render behind a compact more menu", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const renderSessionsSource = source.slice(
            source.indexOf("function renderSessions"),
            source.indexOf("function renderSources")
    );

    assert.match(renderSessionsSource, /data-session-menu/);
    assert.match(renderSessionsSource, /sessionActionsMenu\(session\)/);
    assert.doesNotMatch(renderSessionsSource, /sessionIconButton\(session\.id,\s*"rename"/);
    assert.doesNotMatch(renderSessionsSource, /sessionIconButton\(session\.id,\s*"delete"/);
});

test("opening the session action menu stops the same click from closing it", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const sessionClickSource = source.slice(
            source.indexOf("dom.sessionList.addEventListener(\"click\""),
            source.indexOf("dom.sessionList.addEventListener(\"keydown\"")
    );
    const menuBranch = sessionClickSource.slice(
            sessionClickSource.indexOf("if (menuTrigger)"),
            sessionClickSource.indexOf("if (renameTrigger)")
    );

    assert.match(menuBranch, /event\.stopPropagation\(\)/);
});

test("main session polish keeps the dialogue continuous and composer quiet", async () => {
    const css = await readFile(new URL("../css/workbench.css", import.meta.url), "utf8");

    assert.match(css, /--shell-header-height:\s*98px;/);
    assert.match(css, /\.drawer-header\s*\{[\s\S]*min-height:\s*var\(--shell-header-height\);/);
    assert.match(css, /\.dialogue-header,[\s\S]*\.workspace-header\s*\{[\s\S]*min-height:\s*var\(--shell-header-height\);/);
    assert.match(css, /\.sidebar-tabs\s*\{[\s\S]*min-height:\s*var\(--shell-header-height\);/);
    assert.match(css, /\.drawer-search\s*\{[\s\S]*border-bottom:\s*1px solid/);
    assert.match(css, /\.drawer-search::before\s*\{[\s\S]*content:\s*"search";/);
    assert.match(css, /\.message-avatar\s*\{[\s\S]*display:\s*none;/);
    assert.match(css, /\.message--assistant\s+\.message-body\s*\{[\s\S]*background:\s*transparent;/);
    assert.match(css, /\.composer-surface\s*\{[\s\S]*box-shadow:\s*none;/);
    assert.match(css, /\.right-column\s*\{[\s\S]*background:\s*oklch\(97% 0\.006 245\);/);
});

test("main session shell uses Chinese-only visible headings and status labels", async () => {
    const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");

    assert.match(html, /<h1>研究工作台<\/h1>/);
    assert.match(html, /<p id="project-summary" hidden>/);
    assert.match(html, /<h2>会话<\/h2>/);
    assert.doesNotMatch(html, /<p class="overline">研究工作区<\/p>/);
    assert.doesNotMatch(html, /<p class="overline">研究会话<\/p>/);
    assert.doesNotMatch(html, /<p>会话<\/p>\s*<h2>研究会话<\/h2>/);
    assert.doesNotMatch(html, /Research workspace|Research dialogue|Sessions/);

    assert.match(source, /continue:\s*"进行中"/);
    assert.doesNotMatch(source, /`\$\{session\.status \|\| "drafting"\}/);
});

test("session rows show recency without repeating low-value status text", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const renderSessionsSource = source.slice(
            source.indexOf("function renderSessions"),
            source.indexOf("function renderSources")
    );

    assert.match(renderSessionsSource, /textElement\("span",\s*formatRelativeTime\(session\.lastMessageAt \|\| session\.updatedAt\)\)/);
    assert.doesNotMatch(renderSessionsSource, /sessionStatusLabel\(session\.status\)/);
});

test("secondary workspaces share the simplified Chinese product UI language", async () => {
    const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
    const css = await readFile(new URL("../css/workbench.css", import.meta.url), "utf8");
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");

    assert.doesNotMatch(html, /研究工作台 \/ 资料库|研究工作台 \/ 知识|研究工作台 \/ 项目|研究工作台 \/ 检索诊断/);
    assert.doesNotMatch(html, /Candidate review|Event detail|Observation/);
    assert.match(html, /<h2>资料库<\/h2>/);
    assert.match(html, /<h2>知识库<\/h2>/);
    assert.match(html, /<h2>项目概览<\/h2>/);
    assert.match(html, /<h2>检索诊断<\/h2>/);

    assert.match(css, /\.workspace-list-pane,[\s\S]*\.workspace-inspector\s*\{[\s\S]*border:\s*0;/);
    assert.match(css, /\.workspace-inspector\s*\{[\s\S]*border-left:\s*1px solid var\(--line\);/);
    assert.match(css, /\.table-toolbar\s*\{[\s\S]*border-bottom:\s*1px solid/);
    assert.match(css, /\.drop-zone,[\s\S]*\.review-strip\s*\{[\s\S]*background:\s*transparent;/);

    assert.match(source, /metricItem\("检索调用",\s*summary\.callCount\)/);
    assert.match(source, /textElement\("strong",\s*"后端命中"\)/);
    assert.doesNotMatch(source, /Loading retrieval diagnostics|Retrieval diagnostics unavailable|Backend stats|Zero-hit classification/);
});

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

test("primary workspace selection is separate from session sidebar view", () => {
    const state = createWorkbenchState({
        selectedSidebarView: "evidence-sources"
    });

    assert.equal(state.activeWorkspace, "session");

    const sources = selectWorkspace(state, "sources");
    assert.equal(sources.activeWorkspace, "sources");
    assert.equal(sources.selectedSidebarView, "evidence-sources");
    assert.deepEqual(getWorkspaceVisibility(sources), {
        session: false,
        sources: true,
        knowledge: false,
        project: false,
        observability: false,
        showChatComposer: false,
        showSessionInspector: false
    });

    const knowledge = selectWorkspace(sources, "knowledge");
    assert.equal(knowledge.activeWorkspace, "knowledge");
    assert.equal(knowledge.selectedSidebarView, "evidence-sources");
    assert.equal(getWorkspaceVisibility(knowledge).showChatComposer, false);
    assert.equal(getWorkspaceVisibility(knowledge).showSessionInspector, false);

    const fallback = selectWorkspace(knowledge, "unknown");
    assert.equal(fallback.activeWorkspace, "session");
    assert.equal(fallback.selectedSidebarView, "evidence-sources");
});

test("observability workspace is a first-level mutually exclusive workspace", () => {
    const state = createWorkbenchState({
        selectedSidebarView: "candidate-confirmation",
        activeWorkspace: "observability"
    });

    assert.equal(state.activeWorkspace, "observability");
    assert.deepEqual(getWorkspaceVisibility(state), {
        session: false,
        sources: false,
        knowledge: false,
        project: false,
        observability: true,
        showChatComposer: false,
        showSessionInspector: false
    });

    for (const workspace of ["session", "sources", "knowledge", "project", "observability"]) {
        const selected = selectWorkspace(state, workspace);
        const visibility = getWorkspaceVisibility(selected);
        const visibleWorkspaces = ["session", "sources", "knowledge", "project", "observability"]
                .filter((name) => visibility[name]);

        assert.deepEqual(visibleWorkspaces, [workspace]);
        assert.equal(visibility.showChatComposer, workspace === "session");
        assert.equal(visibility.showSessionInspector, workspace === "session");
        assert.equal(selected.selectedSidebarView, "candidate-confirmation");
    }
});

test("workbench chat requires project session context instead of legacy chat fallback", () => {
    const context = requireProjectChatContext({
        activeProjectId: "project-1",
        activeSessionId: "session-a",
        sampleMode: false
    });

    assert.equal(
            buildProjectMessageUrl(context),
            "/api/projects/project-1/sessions/session-a/messages"
    );
    assert.throws(
            () => requireProjectChatContext({ activeProjectId: null, activeSessionId: "sample-session", sampleMode: true }),
            /project session/i
    );
});

test("project session history uses project-scoped message contract", () => {
    const context = requireProjectChatContext({
        activeProjectId: "project-1",
        activeSessionId: "session-a",
        sampleMode: false
    });

    assert.equal(
            buildProjectSessionMessagesUrl(context),
            "/api/projects/project-1/sessions/session-a/messages"
    );
    assert.deepEqual(
            normalizeProjectMessage({
                id: 15,
                sessionId: "session-a",
                role: "ASSISTANT",
                content: "Recovered answer",
                answerMode: "LOCAL_EVIDENCE",
                createdAt: "2026-05-11T10:00:00Z"
            }),
            {
                id: "15",
                sessionId: "session-a",
                role: "assistant",
                content: "Recovered answer",
                answerMode: "LOCAL_EVIDENCE",
                createdAt: "2026-05-11T10:00:00Z"
            }
    );
});

test("session rename helpers keep the project-scoped session contract", () => {
    const context = requireProjectChatContext({
        activeProjectId: "project-1",
        activeSessionId: "session-a",
        sampleMode: false
    });
    const state = {
        ...baseState(),
        sessions: [
            { id: "session-a", title: "Old title", status: "continue" },
            { id: "session-b", title: "Other title", status: "continue" }
        ]
    };

    assert.equal(
            buildProjectSessionRenameUrl(context),
            "/api/projects/project-1/sessions/session-a"
    );

    const next = renameSessionTitle(state, "session-a", "Transformer literature review");

    assert.equal(next.sessions[0].title, "Transformer literature review");
    assert.equal(next.sessions[1].title, "Other title");
    assert.equal(state.sessions[0].title, "Old title");
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

test("agent trace events fold into research process summary", () => {
    let state = createWorkbenchState({
        activeProjectId: "project-1",
        activeSessionId: "session-a",
        agentTraces: {
            "seed-run": {
                tools: [{ toolName: "seed_tool" }],
                timeline: [],
                retrievalHits: [],
                evidenceEvents: [],
                memoryHits: [],
                answerDeltas: [],
                summary: {
                    toolCount: 1,
                    evidenceCount: 0,
                    weakClaims: 0,
                    requiresConfirmation: false
                }
            }
        }
    });

    assert.deepEqual(createWorkbenchState().agentTraces, {});

    state = applySseEvent(state, {
        eventId: "trace-tool-called",
        eventType: "tool.called",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            data: {
                toolName: "paper_rag",
                toolDisplayName: "Paper retrieval"
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-tool-completed",
        eventType: "tool.completed",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            data: {
                toolName: "paper_rag",
                durationMs: 120
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-tool-failed",
        eventType: "tool.failed",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            data: {
                toolName: "web_search",
                errorType: "TimeoutException",
                recoverable: true
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-retrieval-hit",
        eventType: "retrieval.hit",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            data: {
                sourceType: "paper",
                title: "Agent Paper",
                score: 0.87
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-memory-hit",
        eventType: "memory.hit",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            data: {
                memoryLayer: "L3",
                label: "长期记忆召回",
                snippet: "Prior discussion",
                score: 0.66
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-memory-completed",
        eventType: "memory.completed",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            data: {
                hitCount: 1
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-evidence-evaluated",
        eventType: "evidence.evaluated",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            data: {
                evidenceState: "WEAK",
                citationCount: 1,
                weakClaims: 1,
                requiresUserConfirmation: true
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-evidence-gap",
        eventType: "evidence.gap.detected",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            weakClaims: 2,
            requiresUserConfirmation: false,
            claim: "Needs stronger support"
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-answer-delta",
        eventType: "answer.delta",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            delta: "Grounded answer",
            index: 0
        }
    });
    state = applySseEvent(state, {
        eventId: "trace-answer-delta",
        eventType: "answer.delta",
        runId: "run-1",
        answerId: "answer-1",
        payload: {
            delta: " duplicate",
            index: 1
        }
    });

    assert.equal(state.agentTraces["seed-run"].summary.toolCount, 1);

    const trace = state.agentTraces["run-1"];
    assert.equal(trace.tools.length, 3);
    assert.deepEqual(trace.tools.map((tool) => tool.toolName), ["paper_rag", "paper_rag", "web_search"]);
    assert.equal(trace.tools[2].eventType, "tool.failed");
    assert.equal(trace.timeline.length, 4);
    assert.equal(trace.retrievalHits.length, 1);
    assert.equal(trace.retrievalHits[0].title, "Agent Paper");
    assert.equal(trace.memoryHits.length, 1);
    assert.equal(trace.memoryHits[0].snippet, "Prior discussion");
    assert.equal(trace.memorySummary.hitCount, 1);
    assert.equal(trace.evidenceEvents.length, 2);
    assert.equal(trace.answerDeltas.length, 1);
    assert.equal(trace.answerDeltas[0].delta, "Grounded answer");
    assert.equal(trace.summary.toolCount, 3);
    assert.equal(trace.summary.evidenceCount, 1);
    assert.equal(trace.summary.memoryCount, 1);
    assert.equal(trace.summary.weakClaims, 3);
    assert.equal(trace.summary.requiresConfirmation, true);
    assert.equal(state.currentAnswer.text, "Grounded answer");
});

test("memory.completed hit count contributes to trace summary when hit rows are absent", () => {
    const state = applySseEvent(createWorkbenchState(), {
        eventId: "trace-memory-completed",
        eventType: "memory.completed",
        runId: "run-memory",
        answerId: "answer-memory",
        payload: {
            data: {
                hitCount: 1
            }
        }
    });

    assert.equal(state.agentTraces["run-memory"].summary.memoryCount, 1);
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
