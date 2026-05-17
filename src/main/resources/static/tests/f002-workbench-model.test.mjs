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
    evidenceFeedbackLabel,
    feedbackPayloadForAnswer,
    getWorkspaceVisibility,
    renameSessionTitle,
    normalizeProjectMessage,
    pendingKnowledgeCandidates,
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

test("research process timeline includes projected plan execute events", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const researchProcessSource = source.slice(
            source.indexOf("function processTimelineItems"),
            source.indexOf("function updateAssistantMessage")
    );

    assert.match(researchProcessSource, /trace\?\.timeline/);
    assert.match(researchProcessSource, /processModeTimelineEvent/);
    assert.match(researchProcessSource, /processAgentTimelineEvent/);
    assert.match(researchProcessSource, /processPlanTimelineEvent/);
});

test("F022 research memory loop renders L1 L2 L3 sections", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const memoryLoopSource = source.slice(
            source.indexOf("function researchMemoryLoopPanel"),
            source.indexOf("function candidateTransitionDetail")
    );

    assert.match(memoryLoopSource, /memoryLoopItem\("L1"/);
    assert.match(memoryLoopSource, /memoryLoopItem\("L2"/);
    assert.match(memoryLoopSource, /memoryLoopItem\("L3"/);
    assert.match(memoryLoopSource, /injectionMode/);
});

test("F022 research process evidence list does not mix memory hits into citation evidence list", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const researchProcessSource = source.slice(
            source.indexOf("function researchProcessPanel"),
            source.indexOf("function processStatusLabel")
    );

    assert.doesNotMatch(researchProcessSource, /const evidenceItems = \[\.\.\.retrievalHits, \.\.\.memoryHits\]/);
    assert.match(researchProcessSource, /const evidenceItems = retrievalHits\.slice\(0, 5\)/);
    assert.match(researchProcessSource, /researchMemoryLoopPanel\(trace, memoryHits\)/);
});

test("F022 memory timeline labels context-only memory", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const timelineSource = source.slice(
            source.indexOf("function processHitTimelineEvent"),
            source.indexOf("function processEvidenceTimelineEvent")
    );

    assert.match(timelineSource, /memoryTimelineLabel/);
    assert.match(timelineSource, /仅上下文，不是引用证据/);
});

test("assistant markdown answers render explicit line breaks instead of relying only on CSS whitespace", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const messageBodySource = source.slice(
            source.indexOf("function messageBody"),
            source.indexOf("function researchProcessPanel")
    );

    assert.match(source, /function messageContentElement/);
    assert.match(messageBodySource, /messageContentElement\(/);
    assert.doesNotMatch(messageBodySource, /textElement\("p",\s*message\.content/);
}
);

test("assistant markdown reports render headings and lists as structured DOM", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const messageContentSource = source.slice(
            source.indexOf("function messageContentElement"),
            source.indexOf("function researchProcessPanel")
    );

    assert.match(messageContentSource, /document\.createElement\("h1"\)/);
    assert.match(messageContentSource, /document\.createElement\("h2"\)/);
    assert.match(messageContentSource, /document\.createElement\("ul"\)/);
    assert.match(messageContentSource, /document\.createElement\("li"\)/);
    assert.match(messageContentSource, /message-content--markdown/);
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

test("answer state chip and composer stay content-aligned in narrow surfaces", async () => {
    const css = await readFile(new URL("../css/workbench.css", import.meta.url), "utf8");

    assert.match(css, /--dialogue-content-width:\s*880px;/);
    assert.match(css, /\.answer-state\s*\{[\s\S]*width:\s*max-content;/);
    assert.match(css, /\.answer-state\s*\{[\s\S]*max-width:\s*min\(100%,\s*260px\);/);
    assert.match(css, /\.answer-state\s*\{[\s\S]*overflow-wrap:\s*anywhere;/);
    assert.doesNotMatch(css, /\.answer-state\s*\{[\s\S]*max-width:\s*180px;/);
    assert.match(css, /\.message\s*\{[\s\S]*max-width:\s*var\(--dialogue-content-width\);/);
    assert.match(css, /\.research-process\s*\{[\s\S]*max-width:\s*var\(--dialogue-content-width\);/);
    assert.match(css, /\.composer-surface\s*\{[\s\S]*width:\s*min\(var\(--dialogue-content-width\),\s*100%\);/);
    assert.match(css, /\.composer-modes\s*\{[\s\S]*padding:\s*8px 14px;/);
    assert.match(css, /\.composer-footer\s*\{[\s\S]*padding:\s*0 14px 12px;/);
    assert.match(css, /\.conversation\s*\{[\s\S]*padding:\s*30px 42px 220px;/);
});

test("right sidebar tabs and empty states explain knowledge evidence and candidate roles", async () => {
    const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");

    assert.match(html, /data-sidebar-view="knowledge-board"[^>]*title="已确认的项目知识"/);
    assert.match(html, /data-sidebar-view="evidence-sources"[^>]*title="当前回答的最终引用"/);
    assert.match(html, /data-sidebar-view="candidate-confirmation"[^>]*title="待确认后写入知识库的候选"/);
    assert.match(source, /当前回答还没有最终引用。完成证据评估后，这里只显示可进入报告的最终来源。/);
    assert.match(source, /本轮尚未生成待确认候选。候选是回答完成后提炼出的知识草稿，确认后才会写入知识库。/);
});

test("manual knowledge action posts to project knowledge board entries endpoint", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");

    assert.match(source, /data-knowledge-action/);
    assert.match(source, /function\s+createManualKnowledgeEntry\(/);
    assert.match(source, /\/knowledge-board\/entries/);
    assert.match(source, /section:\s*"confirmed_finding"/);
    assert.match(source, /evidenceStatus:\s*"confirmed"/);
    assert.match(source, /refreshKnowledgeBoard\(\)/);
});

test("answer feedback controls collect answer-level reasons without exposing chunk checkboxes", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const css = await readFile(new URL("../css/workbench.css", import.meta.url), "utf8");

    assert.match(source, /data-feedback-action/);
    assert.match(source, /data-feedback-reason/);
    assert.match(source, /function\s+answerFeedbackControls\(/);
    assert.match(source, /function\s+submitAnswerFeedback\(/);
    assert.match(source, /\/answers\/\$\{encodeURIComponent\(answerId\)\}\/feedback/);
    assert.match(source, /feedbackPayloadForAnswer/);
    assert.match(source, /evidenceSourceIds/);
    assert.match(source, /refreshEvidenceSources\(answerId\)/);
    assert.doesNotMatch(source, /data-feedback-evidence/);
    assert.doesNotMatch(source, /querySelectorAll\("\[data-feedback-evidence\]:checked"\)/);
    assert.match(source, /点赞|点踩/);
    assert.match(css, /\.answer-feedback/);
    assert.match(css, /\.answer-feedback__reasons/);
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
    assert.match(html, /<h2>回答取证诊断<\/h2>/);
    assert.match(html, /检查本轮回答背后的论文证据：哪些结论已有材料支持，哪些还不能使用。/);
    assert.match(html, /id="diagnostics-verdict"[\s\S]*一句话结论/);
    assert.match(html, /步骤[\s\S]*系统动作[\s\S]*查法数[\s\S]*候选材料[\s\S]*主要来源[\s\S]*这意味着[\s\S]*时间/);
    assert.doesNotMatch(html, /aria-label="检索诊断摘要"/);

    assert.match(css, /\.workspace-list-pane,[\s\S]*\.workspace-inspector\s*\{[\s\S]*border:\s*0;/);
    assert.match(css, /\.workspace-inspector\s*\{[\s\S]*border-left:\s*1px solid var\(--line\);/);
    assert.match(css, /\.table-toolbar\s*\{[\s\S]*border-bottom:\s*1px solid/);
    assert.match(css, /\.drop-zone,[\s\S]*\.review-strip\s*\{[\s\S]*background:\s*transparent;/);

    assert.match(source, /diagnosticIndicator\("找到多少证据"/);
    assert.match(source, /diagnosticIndicator\("问了几种查法"/);
    assert.match(source, /diagnosticIndicator\("没有找到的次数"/);
    assert.match(source, /diagnosticIndicator\("是否进入引用"/);
    assert.match(source, /renderDiagnosticsVerdict\(summary,\s*events\)/);
    assert.match(source, /textElement\("strong",\s*"检索细分"\)/);
    assert.match(source, /textElement\("strong",\s*"证据缺口"\)/);
    assert.match(source, /stepExplanationText\(event\)/);
    assert.match(source, /第 \$\{event\.toolCallIndex \?\? "\?"\} 次查资料/);
    assert.doesNotMatch(source, /Loading retrieval diagnostics|Retrieval diagnostics unavailable|Backend stats|Zero-hit classification|正在载入检索诊断|检索诊断暂时不可用/);
});

test("knowledge and observability primary menu panels match the simplified session rail style", async () => {
    const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
    const css = await readFile(new URL("../css/workbench.css", import.meta.url), "utf8");
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");

    assert.match(html, /data-context-panel="knowledge"[\s\S]*<h2>知识<\/h2>[\s\S]*placeholder="筛选知识"/);
    assert.doesNotMatch(html, /data-context-panel="knowledge"[\s\S]*<p>知识<\/p>\s*<h2>知识分区<\/h2>/);
    assert.match(html, /data-context-panel="observability"[\s\S]*<h2>观测<\/h2>[\s\S]*id="diagnostics-nav"/);
    assert.doesNotMatch(html, /data-context-panel="observability"[\s\S]*<p>观测<\/p>\s*<h2>检索诊断<\/h2>/);

    assert.match(css, /\.drawer-panel--quiet\s+\.drawer-search\s*\{[\s\S]*margin-bottom:\s*12px;/);
    assert.match(css, /\.context-nav-item\s+strong\s*\{[\s\S]*font-variant-numeric:\s*tabular-nums;/);
    assert.match(css, /@media \(max-width:\s*560px\)\s*\{[\s\S]*\.table-toolbar\s*\{[\s\S]*grid-template-columns:\s*1fr;/);

    assert.match(source, /diagnosticsNav:\s*document\.getElementById\("diagnostics-nav"\)/);
    assert.match(source, /function renderDiagnosticsNav\(\)/);
    assert.match(source, /contextNavItem\("取证概览",\s*summary\.coverageLabel/);
    assert.match(source, /contextNavItem\("引用待核验",\s*summary\.evidenceChainLabel/);
    assert.match(source, /contextNavItem\("异常",\s*summary\.zeroHitCount/);
});

test("observability workspace explains retrieval diagnostics as research evidence quality", async () => {
    const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const css = await readFile(new URL("../css/workbench.css", import.meta.url), "utf8");

    assert.match(html, /aria-label="回答取证诊断工作区"/);
    assert.match(html, /data-diagnostics-scope="answer"[\s\S]*当前回答/);
    assert.match(html, /data-diagnostics-scope="session"[\s\S]*本会话/);
    assert.doesNotMatch(html, /data-diagnostics-mode="recent"/);
    assert.match(html, /id="diagnostics-verdict"/);
    assert.match(html, /aria-label="取证概览说明"/);
    assert.match(html, /aria-label="检索步骤列标题"/);
    assert.match(html, /aria-label="检索步骤含义"/);

    assert.match(source, /diagnosticsContextText\(session,\s*summary\)/);
    assert.match(source, /diagnosticsScope:\s*"answer"/);
    assert.match(source, /diagnosticsScopeButtons:\s*\[\.\.\.document\.querySelectorAll\("\[data-diagnostics-scope\]"\)\]/);
    assert.match(source, /function visibleRetrievalDiagnostics\(\)/);
    assert.match(source, /event\.answerRunKey === app\.selectedDiagnosticRunKey/);
    assert.match(source, /const answerRunKey = raw\.answerRunKey/);
    assert.match(source, /question:\s*raw\.question/);
    assert.match(source, /row\.dataset\.diagnosticAnswerId/);
    assert.match(source, /diagnosticQuestionLabel\(event\)/);
    assert.match(source, /diagnosticMeaningLabel\(event\)/);
    assert.match(source, /backendSummaryLabel\(event\.backendStats\)/);
    assert.match(source, /evidenceGapText\(event\)/);
    assert.doesNotMatch(source, /k0 \/ v15 \/ m0/);

    assert.match(css, /\.diagnostics-verdict\s*\{/);
    assert.match(css, /\.diagnostics-header-row\s*\{/);
    assert.match(css, /\.diagnostics-mode-toggle\s*\{/);
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
                answerId: "answer-15",
                answerMode: "LOCAL_EVIDENCE",
                createdAt: "2026-05-11T10:00:00Z"
            }),
            {
                id: "15",
                sessionId: "session-a",
                role: "assistant",
                content: "Recovered answer",
                answerId: "answer-15",
                runId: null,
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

test("F024 candidate.created preserves L3 promotion metadata as pending review", () => {
    const next = applySseEvent(baseState(), {
        eventId: "event-f024-promotion",
        eventType: "candidate.created",
        runId: "run-f024",
        payload: {
            id: "candidate-l3-1",
            title: "Adaptive beamforming",
            statement: "Adaptive beamforming repeatedly guides antenna scheduling.",
            suggestedSection: "confirmed_finding",
            sourceTypes: ["l3_memory"],
            evidenceSourceIds: [],
            sourceKind: "l3_memory",
            sourceMemoryEntryId: 42,
            promotionHitCount: 2,
            promotionLastScore: 0.72,
            promotionReason: "l3_memory_repeated_hit"
        }
    });

    const candidate = next.candidates[0];
    assert.equal(candidate.status, "pending");
    assert.equal(candidate.sourceKind, "l3_memory");
    assert.equal(candidate.sourceMemoryEntryId, 42);
    assert.equal(candidate.promotionHitCount, 2);
    assert.equal(candidate.promotionLastScore, 0.72);
    assert.equal(candidate.promotionReason, "l3_memory_repeated_hit");
    assert.deepEqual(candidate.evidenceSourceIds, []);
    assert.deepEqual(next.knowledgeBoard.sections.flatMap((section) => section.entries), []);
    assert.equal(next.agentTraces["run-f024"].summary.candidateEventCount, 1);
});

test("F024 candidate.decayed removes L3 promotion from pending review without confirming knowledge", () => {
    const state = {
        ...baseState(),
        candidates: [{
            id: "candidate-l3-1",
            candidateId: "candidate-l3-1",
            status: "pending",
            title: "Adaptive beamforming",
            statement: "Adaptive beamforming repeatedly guides antenna scheduling.",
            suggestedSection: "confirmed_finding",
            sourceKind: "l3_memory",
            sourceMemoryEntryId: 42,
            promotionHitCount: 2,
            promotionLastScore: 0.72,
            promotionReason: "l3_memory_repeated_hit",
            sourceTypes: ["l3_memory"],
            evidenceSourceIds: []
        }]
    };

    const next = applySseEvent(state, {
        eventId: "event-f024-decay",
        eventType: "candidate.decayed",
        runId: "run-f024",
        payload: {
            id: "candidate-l3-1",
            status: "decayed",
            sourceKind: "l3_memory",
            sourceMemoryEntryId: 42,
            decayReason: "duplicate_confirmed_knowledge"
        }
    });

    assert.equal(next.candidates[0].status, "decayed");
    assert.equal(next.candidates[0].decayReason, "duplicate_confirmed_knowledge");
    assert.deepEqual(pendingKnowledgeCandidates(next).map((candidate) => candidate.id), []);
    assert.deepEqual(next.knowledgeBoard.sections.flatMap((section) => section.entries), []);
    assert.equal(next.agentTraces["run-f024"].summary.candidateEventCount, 1);
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

test("feedback evidence labels expose concrete evidence text instead of only source type", () => {
    const label = evidenceFeedbackLabel({
        id: "evidence-1",
        sourceType: "paper",
        sourceId: "chunk-37",
        snippet: "Space-time beamforming aligns interference mitigation with spectral efficiency."
    });

    assert.match(label, /^paper · Space-time beamforming/);
    assert.notEqual(label, "paper");
});

test("answer feedback payload attributes evidence internally from answer-level reasons", () => {
    const evidenceSources = [
        { id: "evidence-paper", answerId: "answer-1", sourceType: "paper" },
        { id: "evidence-other-answer", answerId: "answer-2", sourceType: "paper" }
    ];

    assert.deepEqual(
            feedbackPayloadForAnswer({
                answerId: "answer-1",
                rating: "up",
                reason: "helpful",
                evidenceSources
            }),
            {
                rating: "up",
                reason: "helpful",
                note: "helpful",
                evidenceSourceIds: ["evidence-paper"]
            }
    );
    assert.deepEqual(
            feedbackPayloadForAnswer({
                answerId: "answer-1",
                rating: "down",
                reason: "missing_evidence",
                evidenceSources
            }).evidenceSourceIds,
            []
    );
    assert.deepEqual(
            feedbackPayloadForAnswer({
                answerId: "answer-1",
                rating: "down",
                reason: "citation_wrong",
                evidenceSources
            }).evidenceSourceIds,
            ["evidence-paper"]
    );
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
                hitCount: 3,
                workingMemoryHitCount: 1,
                projectKnowledgeHitCount: 1,
                longTermMemoryHitCount: 1,
                toolCalled: true,
                contextOnly: true
            }
        }
    });

    const trace = state.agentTraces["run-memory"];
    assert.equal(trace.summary.memoryCount, 3);
    assert.equal(trace.memory.counts.L1, 1);
    assert.equal(trace.memory.counts.L2, 1);
    assert.equal(trace.memory.counts.L3, 1);
    assert.equal(trace.memory.counts.total, 3);
    assert.equal(trace.memory.toolCalled, true);
    assert.equal(trace.memory.contextOnly, true);
});

test("F021 memory trace keeps recalled memory context separate from citation evidence", () => {
    let state = createWorkbenchState();

    state = applySseEvent(state, {
        eventId: "f021-retrieval-hit",
        eventType: "retrieval.hit",
        runId: "run-f021",
        answerId: "answer-f021",
        payload: {
            data: {
                sourceType: "paper",
                title: "Citable source",
                snippet: "This is paper evidence.",
                score: 0.91
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "f021-memory-hit",
        eventType: "memory.hit",
        runId: "run-f021",
        answerId: "answer-f021",
        payload: {
            data: {
                memoryLayer: "L3",
                sourceType: "long_term_memory",
                sourceId: "memory-7",
                snippet: "Prior project synthesis",
                score: 0.66,
                contextOnly: false
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "f022-l2-memory-hit",
        eventType: "memory.hit",
        runId: "run-f021",
        answerId: "answer-f021",
        payload: {
            data: {
                memoryLayer: "L2",
                sourceType: "project_knowledge",
                sourceId: "knowledge-3",
                title: "Confirmed direction",
                snippet: "Use local evidence first.",
                contextOnly: true,
                injectionMode: "preloaded_prompt",
                reason: "recent_confirmed_project_knowledge"
            }
        }
    });
    state = applySseEvent(state, {
        eventId: "f021-memory-completed",
        eventType: "memory.completed",
        runId: "run-f021",
        answerId: "answer-f021",
        payload: {
            data: {
                hitCount: 2,
                memoryLayer: "L1",
                sourceType: "working_memory",
                snippet: "Rolling summary loaded"
            }
        }
    });

    const trace = state.agentTraces["run-f021"];
    assert.deepEqual(trace.memoryHits[0], {
        eventId: "f021-memory-hit",
        eventType: "memory.hit",
        runId: "run-f021",
        answerId: "answer-f021",
        sequence: null,
        createdAt: "",
        memoryLayer: "L3",
        sourceType: "long_term_memory",
        title: "",
        sourceId: "memory-7",
        snippet: "Prior project synthesis",
        score: 0.66,
        injectionMode: "tool_recall",
        reason: "memory_recall_result",
        contextOnly: true
    });
    assert.equal(trace.memory.byLayer.L2[0].sourceType, "project_knowledge");
    assert.equal(trace.memory.byLayer.L2[0].contextOnly, true);
    assert.equal(trace.memory.byLayer.L2[0].injectionMode, "preloaded_prompt");
    assert.equal(trace.memorySummary.memoryLayer, "L1");
    assert.equal(trace.memorySummary.sourceType, "working_memory");
    assert.equal(trace.memorySummary.snippet, "Rolling summary loaded");
    assert.equal(trace.memorySummary.contextOnly, true);
    assert.equal(trace.summary.citationEvidenceCount, 1);
    assert.equal(trace.summary.memoryContextCount, 2);
    assert.equal(trace.summary.evidenceCount, 1);
    assert.equal(trace.summary.memoryCount, 2);
});

test("F022 groups memory hits by L1 L2 L3 and defaults injection metadata", () => {
    let state = createWorkbenchState();
    for (const hit of [
        { memoryLayer: "L1", sourceType: "working_memory", snippet: "Working summary" },
        { memoryLayer: "L2", sourceType: "project_knowledge", snippet: "Confirmed finding" },
        { memoryLayer: "L3", sourceType: "long_term_memory", snippet: "Prior memory" }
    ]) {
        state = applySseEvent(state, {
            eventId: `f022-${hit.memoryLayer}`,
            eventType: "memory.hit",
            runId: "run-f022",
            answerId: "answer-f022",
            payload: { data: hit }
        });
    }

    const trace = state.agentTraces["run-f022"];
    assert.equal(trace.memory.byLayer.L1.length, 1);
    assert.equal(trace.memory.byLayer.L2.length, 1);
    assert.equal(trace.memory.byLayer.L3.length, 1);
    assert.equal(trace.memory.counts.total, 3);
    assert.equal(trace.memory.byLayer.L1[0].injectionMode, "preloaded_prompt");
    assert.equal(trace.memory.byLayer.L1[0].reason, "working_memory_window");
    assert.equal(trace.memory.byLayer.L2[0].reason, "recent_confirmed_project_knowledge");
    assert.equal(trace.memory.byLayer.L3[0].injectionMode, "tool_recall");
    assert.equal(trace.memory.byLayer.L3[0].reason, "memory_recall_result");
});

test("F022 preserves backend memory injection metadata when provided", () => {
    const state = applySseEvent(createWorkbenchState(), {
        eventId: "f022-backend-memory",
        eventType: "memory.hit",
        runId: "run-f022-backend",
        answerId: "answer-f022-backend",
        payload: {
            data: {
                memoryLayer: "L2",
                sourceType: "project_knowledge",
                title: "Stable route",
                rank: 4,
                injectionMode: "preloaded_prompt",
                reason: "manual_confirmation",
                snippet: "Use the stable route."
            }
        }
    });

    const hit = state.agentTraces["run-f022-backend"].memory.byLayer.L2[0];
    assert.equal(hit.title, "Stable route");
    assert.equal(hit.rank, 4);
    assert.equal(hit.injectionMode, "preloaded_prompt");
    assert.equal(hit.reason, "manual_confirmation");
});

test("F023 preserves semantic project knowledge recall metadata", () => {
    const state = applySseEvent(createWorkbenchState(), {
        eventId: "f023-semantic-l2",
        eventType: "memory.hit",
        runId: "run-f023",
        answerId: "answer-f023",
        payload: {
            data: {
                memoryLayer: "L2",
                sourceType: "project_knowledge",
                sourceId: "knowledge-semantic-1",
                title: "Semantic route",
                snippet: "Adaptive beamforming is the stable project direction.",
                score: 0.91,
                semanticScore: 0.98,
                evidenceScore: 1,
                recencyScore: 0.12,
                rank: 1,
                reason: "semantic_confirmed_project_knowledge",
                contextOnly: true
            }
        }
    });

    const hit = state.agentTraces["run-f023"].memory.byLayer.L2[0];
    assert.equal(hit.sourceType, "project_knowledge");
    assert.equal(hit.contextOnly, true);
    assert.equal(hit.score, 0.91);
    assert.equal(hit.semanticScore, 0.98);
    assert.equal(hit.evidenceScore, 1);
    assert.equal(hit.recencyScore, 0.12);
    assert.equal(hit.rank, 1);
    assert.equal(hit.reason, "semantic_confirmed_project_knowledge");
    assert.equal(state.agentTraces["run-f023"].summary.citationEvidenceCount, 0);
});

test("F021 candidate and confirmed knowledge projections stay separated", () => {
    let state = createWorkbenchState({
        candidates: [
            {
                id: "candidate-existing",
                candidateId: "candidate-existing",
                status: "pending",
                title: "Existing candidate",
                statement: "This should remain pending.",
                suggestedSection: "core_concept"
            }
        ]
    });

    state = applySseEvent(state, {
        eventId: "f021-candidate-created",
        eventType: "candidate.created",
        answerId: "answer-f021",
        payload: {
            id: "candidate-new",
            title: "New pending candidate",
            statement: "Candidates are not stable knowledge yet.",
            suggestedSection: "method_route"
        }
    });
    state = applySseEvent(state, {
        eventId: "f021-entry-created",
        eventType: "knowledge.entry.created",
        payload: {
            id: "entry-confirmed",
            sourceCandidateId: "candidate-new",
            section: "method_route",
            title: "Confirmed knowledge",
            content: "Only accepted candidates become confirmed knowledge.",
            evidenceStatus: "confirmed"
        }
    });

    const entries = state.knowledgeBoard.sections.flatMap((section) => section.entries);
    assert.deepEqual(
            state.candidates.map((candidate) => [candidate.id, candidate.status]),
            [
                ["candidate-new", "accepted"],
                ["candidate-existing", "pending"]
            ]
    );
    assert.deepEqual(entries.map((entry) => entry.id), ["entry-confirmed"]);
    assert.equal(entries.some((entry) => entry.id === "candidate-existing"), false);
    assert.deepEqual(
            state.cognitionWorkspace.pendingCandidates.map((candidate) => candidate.id),
            ["candidate-existing"]
    );
    assert.deepEqual(
            state.cognitionWorkspace.confirmedKnowledge.map((entry) => entry.id),
            ["entry-confirmed"]
    );
    assert.deepEqual(
            state.cognitionWorkspace.recentChanges.map((change) => change.eventType),
            ["knowledge.entry.created", "candidate.created"]
    );
});

test("candidate review queue contains only pending candidates after knowledge write", () => {
    const candidates = [
        { id: "candidate-pending", status: "pending", title: "Needs review" },
        { id: "candidate-accepted", status: "accepted", title: "Already written" },
        { id: "candidate-edited", status: "edited_accepted", title: "Edited and written" },
        { id: "candidate-ignored", status: "ignored", title: "Ignored" }
    ];

    assert.deepEqual(
            pendingKnowledgeCandidates(candidates).map((candidate) => candidate.id),
            ["candidate-pending"]
    );
});

test("F021 feedback.applied stores affected evidence and chunk counts in trace state", () => {
    const state = applySseEvent(createWorkbenchState(), {
        eventId: "f021-feedback-applied",
        eventType: "feedback.applied",
        runId: "run-feedback",
        answerId: "answer-feedback",
        payload: {
            data: {
                projectId: "project-1",
                answerId: "answer-feedback",
                rating: "helpful",
                feedbackScore: 0.75,
                updatedEvidenceSourceCount: 3,
                updatedChunkCount: 14,
                appliedEvidenceSourceIds: ["e-1", "e-2", "e-3"]
            }
        }
    });

    const trace = state.agentTraces["run-feedback"];
    assert.deepEqual(trace.feedbackApplications, [
        {
            eventId: "f021-feedback-applied",
            eventType: "feedback.applied",
            runId: "run-feedback",
            answerId: "answer-feedback",
            sequence: null,
            createdAt: "",
            projectId: "project-1",
            rating: "helpful",
            feedbackScore: 0.75,
            updatedEvidenceSourceCount: 3,
            updatedChunkCount: 14,
            appliedEvidenceSourceIds: ["e-1", "e-2", "e-3"]
        }
    ]);
    assert.equal(trace.summary.feedbackAppliedCount, 1);
    assert.equal(trace.summary.updatedEvidenceSourceCount, 3);
    assert.equal(trace.summary.updatedChunkCount, 14);
    assert.equal(trace.summary.latestFeedbackScore, 0.75);
});

test("F021 workbench bootstrap loads global cognition snapshot for Knowledge workspace", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");

    assert.match(source, /function\s+loadGlobalKnowledge\(/);
    assert.match(source, /\/api\/system\/global-knowledge/);
    assert.match(source, /app\.globalKnowledge\s*=/);
    assert.match(source, /loadGlobalKnowledge\(\)/);
});

test("F021 empty global cognition is shown as unrecorded, not unloaded", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const cognitionSource = source.slice(
            source.indexOf("function globalCognitionDescriptor"),
            source.indexOf("function renderRecentCognitionChanges")
    );

    assert.match(cognitionSource, /Object\.hasOwn\(global,\s*camelKey\)/);
    assert.match(cognitionSource, /尚未记录这类稳定认知/);
    assert.match(cognitionSource, /后端认知投影尚未加载/);
});

test("F021 global cognition notes render as tabs and only enter textarea mode after edit", async () => {
    const source = await readFile(new URL("../js/workbench-app.js", import.meta.url), "utf8");
    const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
    const cognitionSource = source.slice(
            source.indexOf("function renderGlobalCognitionPanel"),
            source.indexOf("function renderRecentCognitionChanges")
    );
    const css = await readFile(new URL("../css/workbench.css", import.meta.url), "utf8");

    assert.match(source, /function\s+saveGlobalCognitionNote\(/);
    assert.match(source, /activeGlobalCognitionNote/);
    assert.match(source, /globalCognitionEditingNote/);
    assert.match(source, /data-global-cognition-tab/);
    assert.match(source, /data-global-cognition-edit/);
    assert.match(source, /data-global-cognition-save/);
    assert.match(source, /data-global-cognition-cancel/);
    assert.match(source, /data-global-cognition-content/);
    assert.match(source, /patchJson\(\"\/api\/system\/global-knowledge\"/);
    assert.match(cognitionSource, /globalCognitionTabs/);
    assert.match(cognitionSource, /globalCognitionActivePanel/);
    assert.match(cognitionSource, /isEditing/);
    assert.match(cognitionSource, /textarea/);
    assert.doesNotMatch(cognitionSource, /global-cognition-title/);
    assert.match(cognitionSource, /USER/);
    assert.match(cognitionSource, /SOUL/);
    assert.match(cognitionSource, /RESEARCH_STATE/);
    assert.doesNotMatch(cognitionSource, /cognitionNote\(/);
    assert.match(css, /\.global-cognition-tabs\s*\{/);
    assert.match(css, /\.global-cognition-tab\.is-active\s*\{/);
    assert.match(css, /\.global-cognition-tab\s*\{[\s\S]*background:\s*var\(--surface\);/);
    assert.match(css, /\.global-cognition-tab\.is-active\s*\{[\s\S]*background:\s*var\(--surface-muted\);/);
    assert.match(css, /\.global-cognition-reader\s*\{/);
    assert.match(css, /\.global-cognition-editor\s*\{/);
    assert.doesNotMatch(css, /\.global-cognition-title\s*\{/);
    assert.match(html, /id="global-cognition-panel"\s+class="global-cognition-host"/);
    assert.doesNotMatch(html, /id="global-cognition-panel"\s+class="cognition-note-grid"/);
    assert.doesNotMatch(css, /\.cognition-note-grid\s*\{/);
});

test("plan execute trace events fold into serial multi-agent projection", () => {
    let state = createWorkbenchState({
        currentAnswer: {
            answerId: "answer-plan",
            text: "",
            status: "streaming",
            evidenceState: null,
            outputMode: null,
            citationCount: 0
        },
        evidenceSources: []
    });

    const applyTrace = (event) => {
        state = applySseEvent(state, {
            runId: "run-plan",
            answerId: "answer-plan",
            ...event
        });
    };

    applyTrace({
        eventId: "mode-selected",
        eventType: "agent.step.completed",
        payload: {
            actor: { agentRole: "supervisor", displayName: "Supervisor" },
            step: { stepId: "mode-selection", label: "Mode selection" },
            data: {
                mode: "PLAN_EXECUTE",
                reason: "semantic_document_research",
                decisionSource: "semantic",
                fallbackReason: "simple_react_request",
                semanticConfidence: 0.92
            }
        }
    });
    applyTrace({
        eventId: "plan-created",
        eventType: "agent.plan.created",
        payload: {
            actor: { agentRole: "supervisor", displayName: "Supervisor" },
            data: {
                mode: "PLAN_EXECUTE",
                summary: "Research, audit, then compose.",
                execution: "serial",
                steps: [
                    { stepId: "deep-research", actorRole: "deep_research_agent", label: "Deep research" },
                    { stepId: "audit", actorRole: "evidence_audit_agent", label: "Audit evidence" },
                    { stepId: "compose", actorRole: "document_composer_agent", label: "Compose document" }
                ]
            }
        }
    });
    applyTrace({
        eventId: "deep-started",
        eventType: "agent.step.started",
        sequence: 3,
        payload: {
            actor: { agentRole: "deep_research_agent", displayName: "Deep Research Agent" },
            step: { stepId: "deep-research", label: "Deep research" },
            data: {
                paperEvidenceCount: 4,
                webEvidenceCount: 2
            }
        }
    });
    applyTrace({
        eventId: "deep-completed",
        eventType: "agent.step.completed",
        sequence: 4,
        payload: {
            actor: { agentRole: "deep_research_agent", displayName: "Deep Research Agent" },
            step: { stepId: "deep-research", label: "Deep research" },
            data: {
                paperEvidenceCount: 4,
                webEvidenceCount: 2
            }
        }
    });
    applyTrace({
        eventId: "audit-started",
        eventType: "agent.step.started",
        sequence: 5,
        payload: {
            actor: { agentRole: "evidence_audit_agent", displayName: "Evidence Audit Agent" },
            step: { stepId: "audit", label: "Audit evidence" }
        }
    });
    applyTrace({
        eventId: "audit-completed",
        eventType: "agent.step.completed",
        sequence: 6,
        payload: {
            actor: { agentRole: "evidence_audit_agent", displayName: "Evidence Audit Agent" },
            step: { stepId: "audit", label: "Audit evidence" },
            data: {
                verdict: "pass_with_cautions",
                recommendedAnswerMode: "LOCAL_WEAK_EVIDENCE",
                unsupportedClaimCount: 2,
                sourcePolicyIssueCount: 1,
                requiredRevisionCount: 3
            }
        }
    });
    applyTrace({
        eventId: "composer-started",
        eventType: "agent.step.started",
        sequence: 7,
        payload: {
            actor: { agentRole: "document_composer_agent", displayName: "Document Composer Agent" },
            step: { stepId: "compose", label: "Compose document" }
        }
    });
    applyTrace({
        eventId: "composer-completed",
        eventType: "agent.step.completed",
        sequence: 8,
        payload: {
            actor: { agentRole: "document_composer_agent", displayName: "Document Composer Agent" },
            step: { stepId: "compose", label: "Compose document" },
            data: {
                format: "markdown",
                title: "Grounded Research Brief",
                sectionCount: 5
            }
        }
    });

    const trace = state.agentTraces["run-plan"];
    assert.equal(trace.mode, "PLAN_EXECUTE");
    assert.equal(trace.modeReason, "semantic_document_research");
    assert.deepEqual(trace.modeSelection, {
        mode: "PLAN_EXECUTE",
        reason: "semantic_document_research",
        decisionSource: "semantic",
        fallbackReason: "simple_react_request",
        semanticConfidence: 0.92
    });
    assert.equal(trace.plan.summary, "Research, audit, then compose.");
    assert.equal(trace.plan.execution, "serial");
    assert.equal(trace.plan.steps.length, 3);
    assert.equal(trace.summary.execution, "serial");
    assert.deepEqual(
            trace.timeline.map((event) => `${event.actorRole}:${event.status}`),
            [
                "deep_research_agent:started",
                "deep_research_agent:completed",
                "evidence_audit_agent:started",
                "evidence_audit_agent:completed",
                "document_composer_agent:started",
                "document_composer_agent:completed"
            ]
    );
    assert.deepEqual(Object.keys(trace.subagents), [
        "deep_research_agent",
        "evidence_audit_agent",
        "document_composer_agent"
    ]);
    assert.equal(trace.subagents.deep_research_agent.status, "completed");
    assert.equal(trace.subagents.evidence_audit_agent.timeline.length, 2);
    assert.equal(trace.audit.verdict, "pass_with_cautions");
    assert.equal(trace.audit.recommendedAnswerMode, "LOCAL_WEAK_EVIDENCE");
    assert.deepEqual(trace.audit.counts, {
        unsupportedClaimCount: 2,
        sourcePolicyIssueCount: 1,
        requiredRevisionCount: 3
    });
    assert.equal(trace.document.format, "markdown");
    assert.equal(trace.document.title, "Grounded Research Brief");
    assert.equal(trace.document.sectionCount, 5);
    assert.equal(trace.summary.evidenceCount, 0);
    assert.equal(state.currentAnswer.citationCount, 0);
    assert.deepEqual(state.evidenceSources, []);
});

test("plan execute mode keeps subagent visibility when no tools or retrieval hits are present", () => {
    let state = createWorkbenchState({
        currentAnswer: {
            answerId: "answer-plan-weak",
            text: "",
            status: "streaming",
            evidenceState: null,
            outputMode: null,
            citationCount: 0
        }
    });

    const applyTrace = (event) => {
        state = applySseEvent(state, {
            runId: "run-plan-weak",
            answerId: "answer-plan-weak",
            ...event
        });
    };

    applyTrace({
        eventId: "weak-plan-created",
        eventType: "agent.plan.created",
        payload: {
            actor: { agentRole: "supervisor", displayName: "Supervisor" },
            data: {
                mode: "PLAN_EXECUTE",
                summary: "Research, then audit.",
                execution: "serial",
                steps: [
                    { stepId: "deep-research", actorRole: "deep_research_agent", label: "Deep research" },
                    { stepId: "audit", actorRole: "evidence_audit_agent", label: "Audit evidence" }
                ]
            }
        }
    });
    applyTrace({
        eventId: "weak-deep-completed",
        eventType: "agent.step.completed",
        payload: {
            actor: { agentRole: "deep_research_agent", displayName: "Deep Research Agent" },
            step: { stepId: "deep-research", label: "Deep research" },
            data: {
                paperEvidenceCount: 0,
                webEvidenceCount: 0
            }
        }
    });
    applyTrace({
        eventId: "weak-audit-completed",
        eventType: "agent.step.completed",
        payload: {
            actor: { agentRole: "evidence_audit_agent", displayName: "Evidence Audit Agent" },
            step: { stepId: "audit", label: "Audit evidence" },
            data: {
                verdict: "pass_with_cautions",
                recommendedAnswerMode: "LOCAL_WEAK_EVIDENCE"
            }
        }
    });

    const trace = state.agentTraces["run-plan-weak"];
    assert.equal(trace.summary.mode, "PLAN_EXECUTE");
    assert.equal(trace.summary.subagentCount, 2);
    assert.equal(trace.summary.activeSubagentCount, 2);
    assert.equal(trace.summary.toolCount, 0);
    assert.equal(trace.summary.evidenceCount, 0);
    assert.deepEqual(Object.keys(trace.subagents), [
        "deep_research_agent",
        "evidence_audit_agent"
    ]);
});

test("react mode selection does not invent child subagents", () => {
    const state = applySseEvent(createWorkbenchState(), {
        eventId: "react-mode-selected",
        eventType: "agent.step.completed",
        runId: "run-react",
        answerId: "answer-react",
        payload: {
            actor: { agentRole: "supervisor", displayName: "Supervisor" },
            step: { stepId: "mode-selection", label: "Mode selection" },
            data: {
                mode: "REACT",
                reason: "single-paper question",
                decisionSource: "fallback",
                fallbackReason: "single-paper question",
                semanticConfidence: 0
            }
        }
    });

    const trace = state.agentTraces["run-react"];
    assert.equal(trace.mode, "REACT");
    assert.equal(trace.modeReason, "single-paper question");
    assert.deepEqual(trace.modeSelection, {
        mode: "REACT",
        reason: "single-paper question",
        decisionSource: "fallback",
        fallbackReason: "single-paper question",
        semanticConfidence: 0
    });
    assert.deepEqual(trace.timeline, []);
    assert.deepEqual(trace.subagents, {});
    assert.equal(trace.summary.toolCount, 0);
});

test("failed plan execute subagent is grouped by actor and duplicate ids are ignored", () => {
    let state = createWorkbenchState();
    const failedEvent = {
        eventId: "audit-failed",
        eventType: "agent.step.failed",
        runId: "run-failed",
        answerId: "answer-failed",
        sequence: 9,
        payload: {
            actor: { agentRole: "evidence_audit_agent", displayName: "Evidence Audit Agent" },
            step: { stepId: "audit", label: "Audit evidence" },
            data: {
                errorType: "AuditUnavailable",
                recoverable: true
            }
        }
    };

    state = applySseEvent(state, failedEvent);
    state = applySseEvent(state, failedEvent);

    const trace = state.agentTraces["run-failed"];
    assert.equal(trace.timeline.length, 1);
    assert.equal(trace.timeline[0].actorRole, "evidence_audit_agent");
    assert.equal(trace.timeline[0].status, "failed");
    assert.equal(trace.timeline[0].errorType, "AuditUnavailable");
    assert.equal(trace.subagents.evidence_audit_agent.status, "failed");
    assert.equal(trace.subagents.evidence_audit_agent.timeline.length, 1);
    assert.deepEqual(state.processedEventIds, ["audit-failed"]);
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
