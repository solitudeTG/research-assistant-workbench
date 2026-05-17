import {
    applyCandidateAction,
    applySseEvent,
    buildProjectMessageUrl,
    buildProjectSessionMessagesUrl,
    buildProjectSessionDeleteUrl,
    buildProjectSessionRenameUrl,
    calculateRestoredScrollTop,
    createLocalSession,
    createWorkbenchState,
    deleteSessionFromState,
    formatRelativeTime,
    getWorkspaceVisibility,
    normalizeDocument,
    normalizeProjectMessage,
    renameSessionTitle,
    requireProjectChatContext,
    selectSession,
    selectSidebarView,
    selectWorkspace,
    startEditingCandidate
} from "./workbench-model.js";

const EVENT_TYPES = [
    "run.started",
    "agent.plan.created",
    "agent.step.started",
    "agent.step.completed",
    "agent.step.failed",
    "tool.called",
    "tool.completed",
    "tool.failed",
    "retrieval.started",
    "retrieval.hit",
    "retrieval.completed",
    "memory.hit",
    "memory.completed",
    "evidence.evaluated",
    "evidence.gap.detected",
    "answer.delta",
    "answer.completed",
    "run.completed",
    "run.failed",
    "source.status.changed",
    "candidate.created",
    "knowledge.entry.created"
];

const SECTION_LABELS = {
    current_candidates: "本轮候选",
    core_concept: "核心概念",
    method_route: "方法路线",
    confirmed_finding: "已确认结论",
    open_question: "待验证问题"
};

const SOURCE_LABELS = {
    pdf: "论文",
    web: "网页",
    web_page: "网页",
    note: "笔记",
    document: "文档"
};

const dom = {
    systemStatus: document.getElementById("system-status"),
    projectSummary: document.getElementById("project-summary"),
    activeProjectChip: document.getElementById("active-project-chip"),
    projectTopic: document.getElementById("project-topic"),
    projectDescription: document.getElementById("project-description"),
    statSources: document.getElementById("stat-sources"),
    statKnowledge: document.getElementById("stat-knowledge"),
    statSessions: document.getElementById("stat-sessions"),
    sessionList: document.getElementById("session-list"),
    newSessionButton: document.getElementById("new-session"),
    refreshSourcesButton: document.getElementById("refresh-sources"),
    fileInput: document.getElementById("file-input"),
    uploadButton: document.getElementById("upload-button"),
    sourceCount: document.getElementById("source-count"),
    sourceList: document.getElementById("source-list"),
    sourceNav: document.getElementById("source-nav"),
    sourcePipeline: document.getElementById("source-pipeline"),
    sourceDetail: document.getElementById("source-detail"),
    uploadProxy: document.querySelector("[data-upload-proxy]"),
    activeSessionTitle: document.getElementById("active-session-title"),
    activeSessionMeta: document.getElementById("active-session-meta"),
    answerState: document.getElementById("answer-state"),
    conversation: document.getElementById("conversation"),
    allowWeb: document.getElementById("allow-web"),
    extractCandidates: document.getElementById("extract-candidates"),
    composer: document.getElementById("composer"),
    statusBanner: document.getElementById("status-banner"),
    sendButton: document.getElementById("send-button"),
    tabs: [...document.querySelectorAll("[data-sidebar-view]")],
    sidebarViews: [...document.querySelectorAll("[data-view]")],
    answerContextLine: document.getElementById("answer-context-line"),
    knowledgeBoard: document.getElementById("knowledge-board"),
    evidenceList: document.getElementById("evidence-list"),
    candidateList: document.getElementById("candidate-list"),
    workspaceButtons: [...document.querySelectorAll("[data-workspace-target]")],
    contextPanels: [...document.querySelectorAll("[data-context-panel]")],
    workspaces: [...document.querySelectorAll("[data-workspace]")],
    knowledgeNav: document.getElementById("knowledge-nav"),
    knowledgeMetrics: document.getElementById("knowledge-metrics"),
    knowledgeWorkspaceBoard: document.getElementById("knowledge-workspace-board"),
    knowledgeCandidateList: document.getElementById("knowledge-candidate-list"),
    knowledgeDetail: document.getElementById("knowledge-detail"),
    projectOverviewTopic: document.getElementById("project-overview-topic"),
    projectOverviewSummary: document.getElementById("project-overview-summary"),
    overviewStatSources: document.getElementById("overview-stat-sources"),
    overviewStatKnowledge: document.getElementById("overview-stat-knowledge"),
    overviewStatSessions: document.getElementById("overview-stat-sessions"),
    diagnosticsRefresh: document.getElementById("diagnostics-refresh"),
    diagnosticsContext: document.getElementById("diagnostics-context"),
    diagnosticsSummary: document.getElementById("diagnostics-summary"),
    diagnosticsTaxonomy: document.getElementById("diagnostics-taxonomy"),
    diagnosticsEvents: document.getElementById("diagnostics-events"),
    diagnosticsDetail: document.getElementById("diagnostics-detail")
};

const app = {
    ...createWorkbenchState({ selectedSidebarView: "evidence-sources" }),
    projects: [],
    activeProject: null,
    systemState: "checking",
    statusMessage: "正在连接研究工作台",
    messages: [],
    messageLoadState: "idle",
    activeStream: null,
    editingSessionId: null,
    selectedSourceId: null,
    selectedKnowledgeEntryId: null,
    selectedDiagnosticId: null,
    retrievalDiagnostics: [],
    diagnosticsLoadState: "idle",
    diagnosticsError: "",
    sampleMode: false,
    collapsedResearchProcesses: new Set(),
    openSessionMenuId: null
};

wireEvents();
render();
void bootstrap();

function wireEvents() {
    dom.workspaceButtons.forEach((button) => {
        button.addEventListener("click", () => {
            Object.assign(app, selectWorkspace(app, button.dataset.workspaceTarget));
            render();
            if (app.activeWorkspace === "observability") {
                void loadRetrievalDiagnostics();
            }
        });
    });
    dom.diagnosticsRefresh?.addEventListener("click", () => {
        void loadRetrievalDiagnostics({ force: true });
    });

    dom.newSessionButton.addEventListener("click", () => {
        void createResearchSession();
    });
    dom.refreshSourcesButton.addEventListener("click", () => {
        void refreshSources();
    });
    dom.uploadButton.addEventListener("click", () => {
        void uploadSource();
    });
    dom.uploadProxy?.addEventListener("click", () => {
        dom.fileInput.click();
    });
    dom.sendButton.addEventListener("click", () => {
        void askQuestion();
    });
    dom.composer.addEventListener("keydown", (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
            event.preventDefault();
            void askQuestion();
        }
    });

    dom.conversation.addEventListener("click", (event) => {
        const toggle = event.target.closest("[data-process-toggle]");
        if (!toggle) {
            return;
        }
        const processId = toggle.dataset.processToggle;
        if (!processId) {
            return;
        }
        if (app.collapsedResearchProcesses.has(processId)) {
            app.collapsedResearchProcesses.delete(processId);
        } else {
            app.collapsedResearchProcesses.add(processId);
        }
        const previousScroll = {
            scrollTop: dom.conversation.scrollTop,
            scrollHeight: dom.conversation.scrollHeight,
            clientHeight: dom.conversation.clientHeight
        };
        renderDialogue();
        dom.conversation.scrollTop = calculateRestoredScrollTop(previousScroll, dom.conversation.scrollHeight);
    });

    dom.sessionList.addEventListener("click", (event) => {
        const menuTrigger = event.target.closest("[data-session-menu]");
        const renameTrigger = event.target.closest("[data-session-rename]");
        const renameSubmit = event.target.closest("[data-session-rename-submit]");
        const renameCancel = event.target.closest("[data-session-rename-cancel]");
        const deleteTrigger = event.target.closest("[data-session-delete]");
        const selectTrigger = event.target.closest("[data-session-id]");
        if (menuTrigger) {
            event.stopPropagation();
            const sessionId = menuTrigger.dataset.sessionMenu;
            app.openSessionMenuId = app.openSessionMenuId === sessionId ? null : sessionId;
            renderSessions();
            return;
        }
        if (renameTrigger) {
            app.editingSessionId = renameTrigger.dataset.sessionRename;
            app.openSessionMenuId = null;
            renderSessions();
            focusSessionRenameInput();
            return;
        }
        if (deleteTrigger) {
            app.openSessionMenuId = null;
            void submitSessionDelete(deleteTrigger.dataset.sessionDelete);
            return;
        }
        if (renameSubmit) {
            void submitSessionRename(renameSubmit.dataset.sessionRenameSubmit);
            return;
        }
        if (renameCancel) {
            app.editingSessionId = null;
            renderSessions();
            return;
        }
        if (selectTrigger) {
            void switchSession(selectTrigger.dataset.sessionId);
        }
    });

    dom.sessionList.addEventListener("keydown", (event) => {
        const input = event.target.closest("[data-session-rename-input]");
        if (!input) {
            return;
        }
        if (event.key === "Enter") {
            event.preventDefault();
            void submitSessionRename(input.dataset.sessionRenameInput);
        }
        if (event.key === "Escape") {
            event.preventDefault();
            app.editingSessionId = null;
            renderSessions();
        }
    });

    document.addEventListener("click", (event) => {
        if (!app.openSessionMenuId || event.target.closest("#session-list")) {
            return;
        }
        app.openSessionMenuId = null;
        renderSessions();
    });

    dom.sourceList.addEventListener("click", (event) => {
        const retryTrigger = event.target.closest("[data-source-retry]");
        const sourceRow = event.target.closest("[data-source-id]");
        if (retryTrigger) {
            void retrySource(retryTrigger.dataset.sourceRetry);
            return;
        }
        if (sourceRow) {
            app.selectedSourceId = sourceRow.dataset.sourceId;
            renderSources();
        }
    });

    dom.tabs.forEach((tab) => {
        tab.addEventListener("click", () => {
            Object.assign(app, selectSidebarView(app, tab.dataset.sidebarView));
            renderSidebar();
        });
    });

    const handleCandidateClick = (event) => {
        const submitEditTrigger = event.target.closest("[data-candidate-edit-submit]");
        const cancelEditTrigger = event.target.closest("[data-candidate-edit-cancel]");
        const editTrigger = event.target.closest("[data-candidate-edit]");
        const actionTrigger = event.target.closest("[data-candidate-action]");
        if (submitEditTrigger) {
            const form = submitEditTrigger.closest("[data-candidate-edit-form]");
            void submitCandidateAction(
                    "edit-and-accept",
                    submitEditTrigger.dataset.candidateEditSubmit,
                    readCandidateEditForm(form)
            );
            return;
        }
        if (cancelEditTrigger) {
            Object.assign(app, applyCandidateAction(app, cancelEditTrigger.dataset.candidateEditCancel, "cancel"));
            render();
            return;
        }
        if (editTrigger) {
            Object.assign(app, startEditingCandidate(app, editTrigger.dataset.candidateEdit));
            render();
            return;
        }
        if (actionTrigger) {
            void submitCandidateAction(actionTrigger.dataset.candidateAction, actionTrigger.dataset.candidateId);
        }
    };
    dom.candidateList.addEventListener("click", handleCandidateClick);
    dom.knowledgeCandidateList.addEventListener("click", handleCandidateClick);
    dom.diagnosticsEvents?.addEventListener("click", (event) => {
        const row = event.target.closest("[data-diagnostic-id]");
        if (!row) {
            return;
        }
        app.selectedDiagnosticId = row.dataset.diagnosticId;
        renderObservabilityWorkspace();
    });
}

async function bootstrap() {
    await pingSystem();
    await loadProjects();
    render();
}

async function pingSystem() {
    try {
        await getJson("/api/system/ping");
        app.systemState = "ok";
    } catch (error) {
        app.systemState = "error";
        app.statusMessage = `后端健康检查不可用：${error.message}`;
    }
}

async function loadProjects() {
    try {
        const projects = await getJson("/api/projects");
        app.projects = Array.isArray(projects) ? projects : [];
        if (app.projects.length === 0) {
            installSampleState("当前后端还没有项目。界面使用本地样例，创建项目后会切换到真实数据。");
            return;
        }
        app.sampleMode = false;
        app.activeProject = app.projects[0];
        app.activeProjectId = app.activeProject.id;
        await loadSessions();
        await Promise.allSettled([
            loadActiveSessionMessages(),
            refreshSources(),
            refreshKnowledgeBoard(),
            refreshCandidates()
        ]);
    } catch (error) {
        installSampleState(`F002 项目接口暂不可用，已进入本地样例模式：${error.message}`);
        await refreshSources();
    }
}

async function loadSessions() {
    if (!hasProjectApi()) {
        return;
    }
    const previousSessionId = app.activeSessionId;
    const sessions = await getJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions`);
    app.sessions = Array.isArray(sessions) ? sessions : [];
    if (app.sessions.length === 0) {
        const session = await postJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions`, {
            title: "研究线索梳理"
        });
        app.sessions = [session];
    }
    app.activeSessionId = app.sessions.some((session) => session.id === previousSessionId)
            ? previousSessionId
            : app.sessions[0]?.id || null;
}

async function switchSession(sessionId) {
    if (!sessionId || sessionId === app.activeSessionId) {
        return;
    }
    closeStream();
    Object.assign(app, selectSession(app, sessionId));
    app.selectedDiagnosticId = null;
    app.retrievalDiagnostics = [];
    app.diagnosticsLoadState = "idle";
    app.messages = [];
    app.messageLoadState = "loading";
    render();
    await loadActiveSessionMessages();
    if (app.activeWorkspace === "observability") {
        await loadRetrievalDiagnostics();
    }
    render();
}

async function loadRetrievalDiagnostics({ force = false } = {}) {
    if (app.sampleMode) {
        app.retrievalDiagnostics = sampleRetrievalDiagnostics();
        app.diagnosticsLoadState = "sample";
        app.diagnosticsError = "";
        ensureSelectedDiagnostic();
        renderObservabilityWorkspace();
        return;
    }
    if (!hasProjectApi() || !app.activeSessionId) {
        app.retrievalDiagnostics = [];
        app.diagnosticsLoadState = "empty";
        app.diagnosticsError = "需要先选择真实项目会话。";
        renderObservabilityWorkspace();
        return;
    }
    if (!force && app.diagnosticsLoadState === "loaded" && app.retrievalDiagnostics.length) {
        return;
    }
    app.diagnosticsLoadState = "loading";
    app.diagnosticsError = "";
    renderObservabilityWorkspace();
    try {
        const diagnostics = await getJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions/${encodeURIComponent(app.activeSessionId)}/retrieval-diagnostics`);
        app.retrievalDiagnostics = normalizeRetrievalDiagnostics(diagnostics);
        app.diagnosticsLoadState = app.retrievalDiagnostics.length ? "loaded" : "empty";
        app.diagnosticsError = "";
        ensureSelectedDiagnostic();
    } catch (error) {
        app.retrievalDiagnostics = [];
        app.selectedDiagnosticId = null;
        app.diagnosticsLoadState = "error";
        app.diagnosticsError = error.message;
    }
    renderObservabilityWorkspace();
}

async function submitSessionRename(sessionId) {
    const input = dom.sessionList.querySelector(`[data-session-rename-input="${cssEscape(sessionId)}"]`);
    const title = input?.value?.trim() || "";
    if (!sessionId || !title) {
        app.statusMessage = "会话名称不能为空。";
        renderStatus();
        return;
    }
    if (!hasProjectApi()) {
        Object.assign(app, renameSessionTitle(app, sessionId, title));
        app.editingSessionId = null;
        render();
        return;
    }
    try {
        const session = await patchJson(
                buildProjectSessionRenameUrl({ projectId: app.activeProjectId, sessionId }),
                { title }
        );
        app.sessions = app.sessions.map((item) => item.id === sessionId ? session : item);
        app.editingSessionId = null;
        app.statusMessage = "会话名称已更新。";
    } catch (error) {
        app.statusMessage = `重命名会话失败：${error.message}`;
    }
    render();
}

async function submitSessionDelete(sessionId) {
    const session = app.sessions.find((item) => item.id === sessionId);
    if (!session) {
        return;
    }
    const title = session.title || "未命名会话";
    const confirmed = window.confirm(`删除会话“${title}”？这会删除该会话的消息、回答、证据、候选和研究过程记录，无法撤销。`);
    if (!confirmed) {
        return;
    }
    closeStream();
    if (!hasProjectApi()) {
        Object.assign(app, deleteSessionFromState(app, sessionId));
        app.selectedDiagnosticId = null;
        app.retrievalDiagnostics = [];
        app.diagnosticsLoadState = "idle";
        app.statusMessage = "本地样例会话已删除。";
        render();
        return;
    }
    try {
        await deleteJson(buildProjectSessionDeleteUrl({ projectId: app.activeProjectId, sessionId }));
        Object.assign(app, deleteSessionFromState(app, sessionId));
        app.selectedDiagnosticId = null;
        app.retrievalDiagnostics = [];
        app.diagnosticsLoadState = "idle";
        app.statusMessage = "会话已删除。";
        if (app.activeSessionId) {
            await loadActiveSessionMessages();
        }
    } catch (error) {
        app.statusMessage = `删除会话失败：${error.message}`;
    }
    render();
}

async function loadActiveSessionMessages() {
    if (!hasProjectApi() || !app.activeSessionId) {
        app.messageLoadState = "idle";
        return;
    }
    let context;
    try {
        context = requireProjectChatContext(app);
    } catch (error) {
        app.messageLoadState = "error";
        app.statusMessage = error.message;
        return;
    }
    app.messageLoadState = "loading";
    try {
        const messages = await getJson(buildProjectSessionMessagesUrl(context));
        app.messages = Array.isArray(messages) ? messages.map(normalizeProjectMessage) : [];
        app.messageLoadState = "idle";
    } catch (error) {
        app.messages = [];
        app.messageLoadState = "error";
        app.statusMessage = `载入会话消息失败：${error.message}`;
    }
}

async function createResearchSession() {
    if (!hasProjectApi()) {
        const session = createLocalSession("本地研究会话");
        app.sessions = [{ ...session, id: session.sessionKey, status: "drafting", lastMessageAt: session.updatedAt }, ...app.sessions];
        Object.assign(app, selectSession(app, session.sessionKey));
        app.messages = [];
        app.messageLoadState = "idle";
        Object.assign(app, selectWorkspace(app, "session"));
        render();
        return;
    }
    try {
        const title = `研究会话 ${app.sessions.length + 1}`;
        const session = await postJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions`, { title });
        app.sessions = [session, ...app.sessions];
        Object.assign(app, selectSession(app, session.id));
        Object.assign(app, selectWorkspace(app, "session"));
        app.messages = [];
        app.messageLoadState = "idle";
        app.statusMessage = "已创建新的项目会话。";
    } catch (error) {
        app.statusMessage = `创建会话失败：${error.message}`;
    }
    render();
}

async function refreshSources() {
    try {
        if (hasProjectApi()) {
            const sources = await getJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sources`);
            app.sources = Array.isArray(sources) ? sources.map(normalizeSource) : [];
        } else {
            app.sources = await compatibilityListLegacyDocuments();
        }
    } catch (error) {
        app.sources = await compatibilityListLegacyDocuments();
        app.statusMessage = `项目资料接口暂不可用，已尝试旧文档接口：${error.message}`;
    }
    ensureSelectedSource();
    render();
}

async function refreshKnowledgeBoard() {
    if (!hasProjectApi()) {
        return;
    }
    try {
        const sections = await getJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/knowledge-board`);
        app.knowledgeBoard = createWorkbenchState({ knowledgeBoard: sections }).knowledgeBoard;
    } catch (error) {
        app.statusMessage = `知识板暂不可用：${error.message}`;
    }
}

async function refreshCandidates() {
    if (!hasProjectApi()) {
        return;
    }
    try {
        const candidates = await getJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/candidates`);
        app.candidates = Array.isArray(candidates) ? candidates : [];
    } catch (error) {
        app.statusMessage = `候选列表暂不可用：${error.message}`;
    }
}

async function uploadSource() {
    if (!dom.fileInput.files.length) {
        app.statusMessage = "请先选择要导入的论文或笔记。";
        renderStatus();
        return;
    }
    const file = dom.fileInput.files[0];
    const formData = new FormData();
    formData.append("file", file);
    app.statusMessage = `正在导入 ${file.name}`;
    renderStatus();
    try {
        const source = hasProjectApi()
                ? await postForm(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sources`, formData)
                : await compatibilityUploadLegacyDocument(formData);
        const normalized = normalizeSource(source);
        app.sources = [normalized, ...app.sources.filter((item) => item.id !== normalized.id)];
        app.selectedSourceId = normalized.id;
        dom.fileInput.value = "";
        Object.assign(app, selectWorkspace(app, "sources"));
        app.statusMessage = "资料已进入处理队列。";
    } catch (error) {
        app.statusMessage = `资料导入失败：${error.message}`;
    }
    render();
}

async function retrySource(sourceId) {
    if (!sourceId) {
        return;
    }
    app.statusMessage = "正在重试失败资料。";
    renderStatus();
    if (!hasProjectApi()) {
        app.sources = app.sources.map((source) => source.id === sourceId ? { ...source, status: "uploaded", failureStage: null } : source);
        render();
        return;
    }
    try {
        const source = await postJson(
                `/api/projects/${encodeURIComponent(app.activeProjectId)}/sources/${encodeURIComponent(sourceId)}/retry`,
                {}
        );
        app.sources = app.sources.map((item) => item.id === sourceId ? normalizeSource(source) : item);
        app.statusMessage = "资料已重新进入处理队列。";
    } catch (error) {
        app.statusMessage = `重试失败：${error.message}`;
    }
    render();
}

async function askQuestion() {
    const question = dom.composer.value.trim();
    if (!question) {
        app.statusMessage = "请先输入研究问题。";
        renderStatus();
        return;
    }
    closeStream();
    const now = new Date().toISOString();
    const assistantId = nextId("assistant");
    app.messageLoadState = "idle";
    app.messages = [
        ...app.messages,
        { id: nextId("user"), role: "user", content: question, createdAt: now },
        { id: assistantId, role: "assistant", content: "", createdAt: now, status: "streaming" }
    ];
    app.currentAnswer = {
        answerId: null,
        text: "",
        status: "streaming",
        evidenceState: null,
        outputMode: null,
        citationCount: 0,
        messageId: assistantId
    };
    app.statusMessage = "正在生成回答。";
    dom.composer.value = "";
    render();
    scrollConversationToBottom();

    let chatContext;
    try {
        chatContext = requireProjectChatContext(app);
    } catch (error) {
        updateAssistantMessage(assistantId, {
            content: "项目会话尚未初始化，无法发送研究消息。请刷新页面或先创建项目会话。",
            status: "error"
        });
        app.currentAnswer.status = "error";
        app.statusMessage = error.message;
        render();
        return;
    }

    try {
        const response = await postJson(
                buildProjectMessageUrl(chatContext),
                {
                    question,
                    sourceFilters: [],
                    allowWebSupplement: dom.allowWeb.checked,
                    extractKnowledgeCandidates: dom.extractCandidates.checked,
                    answerMode: "local_first"
                }
        );
        app.currentAnswer.answerId = response.answerId;
        app.currentAnswer.runId = response.streamRunId;
        app.activeAnswerContext = { answerId: response.answerId, citationCount: 0, candidateCount: 0 };
        updateAssistantMessage(assistantId, {
            answerId: response.answerId,
            runId: response.streamRunId
        });
        render();
        scrollConversationToBottom();
        openProjectEventStream(response.sseUrl, assistantId);
    } catch (error) {
        updateAssistantMessage(assistantId, {
            content: `项目回答请求失败：${error.message}`,
            status: "error"
        });
        app.currentAnswer.status = "error";
        app.statusMessage = "项目回答请求失败。";
        render();
    }
}

function openProjectEventStream(sseUrl, assistantMessageId) {
    const source = new EventSource(sseUrl);
    app.activeStream = source;
    for (const eventType of EVENT_TYPES) {
        source.addEventListener(eventType, async (event) => {
            const workbenchEvent = parseEventData(event.data);
            Object.assign(app, applySseEvent(app, workbenchEvent));
            if (eventType === "evidence.evaluated") {
                await refreshEvidenceSources(workbenchEvent.answerId || app.currentAnswer?.answerId);
            }
            syncAssistantFromCurrentAnswer(assistantMessageId, workbenchEvent);
            if (eventType === "run.completed") {
                await loadSessions();
            }
            if (eventType === "run.completed" || eventType === "run.failed") {
                closeStream();
            }
            render();
            scrollConversationToBottom();
        });
    }
    source.onerror = () => {
        if (app.currentAnswer?.status === "completed") {
            closeStream();
            return;
        }
        updateAssistantMessage(assistantMessageId, {
            content: app.currentAnswer?.text || "SSE 连接中断，请稍后重试。",
            status: "error"
        });
        app.currentAnswer = { ...(app.currentAnswer || {}), status: "error" };
        app.statusMessage = "事件流连接中断。";
        closeStream();
        render();
    };
}

async function refreshEvidenceSources(answerId = app.currentAnswer?.answerId || app.activeAnswerContext?.answerId) {
    if (!hasProjectApi() || !answerId) {
        return;
    }
    try {
        const evidenceSources = await getJson(
                `/api/projects/${encodeURIComponent(app.activeProjectId)}/answers/${encodeURIComponent(answerId)}/evidence`
        );
        app.evidenceSources = Array.isArray(evidenceSources) ? evidenceSources : [];
    } catch (error) {
        app.statusMessage = `证据来源暂不可用：${error.message}`;
    }
}

async function submitCandidateAction(action, candidateId, editPayload = null) {
    if (!candidateId) {
        return;
    }
    Object.assign(app, applyCandidateAction(app, candidateId, action));
    render();
    if (!hasProjectApi()) {
        return;
    }
    const routeByAction = {
        accept: "accept",
        "edit-and-accept": "edit-and-accept",
        "mark-unverified": "mark-unverified",
        ignore: "ignore"
    };
    const route = routeByAction[action];
    if (!route) {
        return;
    }
    try {
        await postJson(
                `/api/projects/${encodeURIComponent(app.activeProjectId)}/candidates/${encodeURIComponent(candidateId)}/${route}`,
                editPayload || {}
        );
        await Promise.allSettled([refreshCandidates(), refreshKnowledgeBoard()]);
        app.statusMessage = "候选状态已更新。";
    } catch (error) {
        app.statusMessage = `候选操作失败：${error.message}`;
    }
    render();
}

function syncAssistantFromCurrentAnswer(messageId, workbenchEvent) {
    const eventType = workbenchEvent?.eventType;
    if (eventType === "answer.delta" || eventType === "answer.completed") {
        updateAssistantMessage(messageId, {
            content: app.currentAnswer?.text || "",
            status: app.currentAnswer?.status || "streaming"
        });
    }
    if (eventType === "evidence.evaluated") {
        Object.assign(app, selectSidebarView(app, "evidence-sources"));
    }
    if (eventType === "candidate.created") {
        Object.assign(app, selectSidebarView(app, "candidate-confirmation"));
    }
    if (eventType === "knowledge.entry.created") {
        Object.assign(app, selectSidebarView(app, "knowledge-board"));
    }
    if (eventType === "run.completed") {
        updateAssistantMessage(messageId, { status: "completed" });
        app.currentAnswer = { ...(app.currentAnswer || {}), status: "completed" };
        app.statusMessage = "回答已完成。";
    }
    if (eventType === "run.failed") {
        updateAssistantMessage(messageId, { status: "error" });
        app.currentAnswer = { ...(app.currentAnswer || {}), status: "error" };
        app.statusMessage = "回答运行失败。";
    }
}

function render() {
    renderStatus();
    renderWorkspaceNavigation();
    renderProject();
    renderSessions();
    renderSources();
    renderDialogue();
    renderSidebar();
    renderKnowledgeWorkspace();
    renderObservabilityWorkspace();
}

function renderStatus() {
    dom.systemStatus.dataset.state = app.systemState;
    dom.systemStatus.textContent = app.systemState === "ok" ? "在线" : app.systemState === "error" ? "离线" : "连接中";
    dom.statusBanner.textContent = app.statusMessage;
}

function renderWorkspaceNavigation() {
    const visibility = getWorkspaceVisibility(app);
    dom.workspaceButtons.forEach((button) => {
        button.classList.toggle("is-active", button.dataset.workspaceTarget === app.activeWorkspace);
    });
    dom.contextPanels.forEach((panel) => {
        panel.classList.toggle("is-active", panel.dataset.contextPanel === app.activeWorkspace);
    });
    dom.workspaces.forEach((workspace) => {
        const isActive = Boolean(visibility[workspace.dataset.workspace]);
        workspace.classList.toggle("is-active", isActive);
        workspace.toggleAttribute("hidden", !isActive);
    });
}

function renderProject() {
    const project = app.activeProject;
    const knowledgeCount = knowledgeEntryCount();
    dom.projectTopic.textContent = project?.topic || "项目空间";
    dom.projectDescription.textContent = project?.summary || "选择或创建一个研究项目后，资料、会话和知识会围绕该项目工作。";
    dom.projectSummary.textContent = project?.summary || "项目级资料、会话、证据与知识板";
    dom.activeProjectChip.textContent = `${app.sessions.length} 会话 · ${app.sources.length} 来源 · ${knowledgeCount} 知识`;
    dom.statSources.textContent = String(app.sources.length);
    dom.statKnowledge.textContent = String(knowledgeCount);
    dom.statSessions.textContent = String(app.sessions.length);
    dom.projectOverviewTopic.textContent = project?.topic || "项目空间";
    dom.projectOverviewSummary.textContent = project?.summary || "当前没有真实项目数据，仍可查看工作区结构。";
    dom.overviewStatSources.textContent = String(app.sources.length);
    dom.overviewStatKnowledge.textContent = String(knowledgeCount);
    dom.overviewStatSessions.textContent = String(app.sessions.length);
    renderSourceNav();
    renderKnowledgeNav();
}

function renderSourceNav() {
    const counts = countSourcesByStatus();
    const items = [
        ["全部资料", app.sources.length],
        ["待处理", counts.uploaded + counts.submitted],
        ["索引中", counts.parsing + counts.fetching + counts.indexing + counts.extracting + counts.depositing],
        ["已入库", counts.deposited + counts.indexed],
        ["失败", counts.failed],
        ["网页来源", app.sources.filter((source) => source.type === "web" || source.type === "web_page").length],
        ["个人笔记", app.sources.filter((source) => source.type === "note").length]
    ];
    dom.sourceNav.replaceChildren(...items.map(([label, count], index) => contextNavItem(label, count, index === 0)));
}

function renderKnowledgeNav() {
    const board = normalizedBoard();
    const sectionCount = (sectionName) => board.sections.find((section) => section.section === sectionName)?.entries.length || 0;
    const pendingCandidates = app.candidates.filter((candidate) => candidate.status === "pending").length;
    const items = [
        ["全部知识", knowledgeEntryCount()],
        ["本轮候选", pendingCandidates],
        ["核心概念", sectionCount("core_concept")],
        ["方法路线", sectionCount("method_route")],
        ["已确认结论", sectionCount("confirmed_finding")],
        ["待验证问题", sectionCount("open_question")],
        ["已归档", 0]
    ];
    dom.knowledgeNav.replaceChildren(...items.map(([label, count], index) => contextNavItem(label, count, index === 0)));
}

function renderSessions() {
    dom.sessionList.replaceChildren();
    if (!app.sessions.length) {
        dom.sessionList.appendChild(emptyBlock("还没有研究会话。"));
        return;
    }
    for (const session of app.sessions) {
        const item = document.createElement("article");
        item.className = `row-item session-row${session.id === app.activeSessionId ? " is-active" : ""}`;
        if (app.editingSessionId === session.id) {
            item.appendChild(sessionRenameForm(session));
            dom.sessionList.appendChild(item);
            continue;
        }
        const selectButton = document.createElement("button");
        selectButton.type = "button";
        selectButton.className = "session-select";
        selectButton.dataset.sessionId = session.id;
        selectButton.append(
                textElement("strong", session.title || "未命名会话"),
                textElement("span", formatRelativeTime(session.lastMessageAt || session.updatedAt))
        );
        const actions = document.createElement("div");
        actions.className = "session-row-actions";
        const menuButton = document.createElement("button");
        menuButton.type = "button";
        menuButton.className = "session-menu-button";
        menuButton.setAttribute("data-session-menu", session.id);
        menuButton.title = "会话操作";
        menuButton.setAttribute("aria-label", "会话操作");
        menuButton.setAttribute("aria-expanded", String(app.openSessionMenuId === session.id));
        menuButton.textContent = "more_vert";
        actions.appendChild(menuButton);
        if (app.openSessionMenuId === session.id) {
            actions.appendChild(sessionActionsMenu(session));
        }
        item.append(selectButton, actions);
        dom.sessionList.appendChild(item);
    }
}

function renderSources() {
    ensureSelectedSource();
    dom.sourceCount.textContent = `当前 ${app.sources.length} 份资料`;
    dom.sourceList.replaceChildren();
    renderSourcePipeline();
    if (!app.sources.length) {
        dom.sourceList.appendChild(emptyBlock("资料库为空。导入论文、网页或个人笔记后，状态会显示在这里。"));
        renderSourceDetail(null);
        return;
    }
    for (const source of app.sources) {
        const item = document.createElement("article");
        item.className = `source-table-row${source.id === app.selectedSourceId ? " is-selected" : ""}`;
        item.dataset.sourceId = source.id;
        item.append(
                textElement("strong", source.title || "未命名资料"),
                textElement("span", sourceTypeLabel(source.type)),
                statusChip(source.status || "unknown"),
                textElement("span", String(source.totalChunks ?? source.chunkCount ?? "—")),
                textElement("span", `${source.depositedKnowledgeCount ?? 0} 条`),
                textElement("span", formatRelativeTime(source.updatedAt || source.createdAt))
        );
        const actions = document.createElement("div");
        actions.className = "table-row-actions";
        if (isFailedSource(source)) {
            const retry = document.createElement("button");
            retry.type = "button";
            retry.className = "quiet-button";
            retry.dataset.sourceRetry = source.id;
            retry.textContent = "重试";
            actions.appendChild(retry);
        } else {
            actions.appendChild(textElement("span", "查看"));
        }
        item.appendChild(actions);
        dom.sourceList.appendChild(item);
    }
    renderSourceDetail(selectedSource());
}

function renderSourcePipeline() {
    const counts = countSourcesByStatus();
    const steps = [
        ["已上传", counts.uploaded + counts.submitted],
        ["解析中", counts.parsing + counts.fetching],
        ["索引中", counts.indexing],
        ["提取中", counts.extracting],
        ["已入库", counts.deposited + counts.indexed],
        ["失败", counts.failed]
    ];
    dom.sourcePipeline.replaceChildren(...steps.map(([label, count]) => {
        const step = document.createElement("div");
        step.className = "pipeline-step";
        step.append(textElement("strong", String(count)), textElement("span", label));
        return step;
    }));
}

function renderSourceDetail(source) {
    dom.sourceDetail.replaceChildren();
    if (!source) {
        dom.sourceDetail.appendChild(emptyBlock("选择一份资料后查看处理阶段、失败原因和来源摘要。"));
        return;
    }
    const meta = document.createElement("div");
    meta.className = "detail-stack";
    meta.append(
            textElement("h3", source.title || "未命名资料"),
            detailRow("类型", sourceTypeLabel(source.type)),
            detailRow("状态", source.status || "unknown"),
            detailRow("失败阶段", source.failureStage || "无"),
            detailRow("沉淀知识", `${source.depositedKnowledgeCount ?? 0} 条`)
    );
    if (source.errorMessage) {
        const error = document.createElement("div");
        error.className = "detail-alert";
        error.textContent = source.errorMessage;
        meta.appendChild(error);
    }
    dom.sourceDetail.appendChild(meta);
}

function renderDialogue() {
    const session = app.sessions.find((item) => item.id === app.activeSessionId);
    dom.activeSessionTitle.textContent = session?.title || "多轮研究对话";
    dom.activeSessionMeta.textContent = session
            ? `${sessionStatusLabel(session.status)} · 最近更新 ${formatRelativeTime(session.lastMessageAt || session.updatedAt)}`
            : "选择会话后开始提问。";
    dom.answerState.textContent = answerStateText();
    dom.conversation.replaceChildren();
    if (app.messageLoadState === "loading") {
        dom.conversation.appendChild(emptyBlock("正在载入会话消息。"));
        return;
    }
    if (app.messageLoadState === "error") {
        dom.conversation.appendChild(emptyBlock("会话消息暂时不可用。"));
        return;
    }
    if (!app.messages.length) {
        dom.conversation.appendChild(emptyBlock("输入研究问题后，这里会显示回答过程。确认后的结论才会写入知识板。"));
        return;
    }
    for (const message of app.messages) {
        const article = document.createElement("article");
        article.className = `message message--${message.role}`;
        article.append(
                textElement("div", message.role === "assistant" ? "AI" : "我", "message-avatar"),
                messageBody(message)
        );
        dom.conversation.appendChild(article);
    }
}

function renderSidebar() {
    dom.tabs.forEach((tab) => {
        tab.classList.toggle("is-active", tab.dataset.sidebarView === app.selectedSidebarView);
    });
    dom.sidebarViews.forEach((view) => {
        view.classList.toggle("is-active", view.dataset.view === app.selectedSidebarView);
    });
    const citationCount = app.currentAnswer?.citationCount || app.activeAnswerContext?.citationCount || 0;
    const candidateCount = app.candidates.filter((candidate) => candidate.status === "pending").length;
    dom.answerContextLine.textContent = `当前回答审阅：${citationCount} 条最终引用，${candidateCount} 条待确认候选。`;
    renderKnowledgeBoard(dom.knowledgeBoard, { compact: true });
    renderEvidenceSources();
    renderCandidates(dom.candidateList);
}

function renderKnowledgeWorkspace() {
    renderKnowledgeMetrics();
    renderKnowledgeBoard(dom.knowledgeWorkspaceBoard, { compact: false });
    renderCandidates(dom.knowledgeCandidateList);
    renderKnowledgeDetail(selectedKnowledgeEntry());
}

function renderKnowledgeMetrics() {
    const board = normalizedBoard();
    const entries = board.sections.flatMap((section) => section.entries);
    const pendingCandidates = app.candidates.filter((candidate) => candidate.status === "pending").length;
    const unverified = entries.filter((entry) => entry.evidenceStatus === "unverified").length;
    const metrics = [
        ["已确认", entries.length],
        ["待验证", unverified],
        ["候选", pendingCandidates],
        ["引用覆盖", entries.filter((entry) => entry.evidenceSourceIds?.length).length]
    ];
    const review = document.createElement("div");
    review.className = "review-strip";
    review.append(
            textElement("strong", `本轮候选 ${pendingCandidates} 条`),
            textElement("span", pendingCandidates ? "待确认候选不会自动写入知识库" : "暂无待审阅候选")
    );
    dom.knowledgeMetrics.replaceChildren(...metrics.map(([label, count]) => metricItem(label, count)), review);
}

function renderKnowledgeBoard(target, { compact }) {
    target.replaceChildren();
    const board = normalizedBoard();
    for (const section of board.sections) {
        const details = document.createElement("details");
        details.className = compact ? "knowledge-section" : "knowledge-section knowledge-section--workspace";
        details.open = section.entries.length > 0 || section.section === "core_concept";
        const summary = document.createElement("summary");
        summary.append(
                textElement("span", SECTION_LABELS[section.section] || section.title || section.section),
                textElement("small", String(section.entries.length))
        );
        details.appendChild(summary);
        if (section.entries.length === 0) {
            details.appendChild(emptyBlock(compact
                    ? "项目还没有确认知识。候选确认或手动新建后会进入知识工作区。"
                    : "暂无已确认条目。"));
        } else {
            for (const entry of section.entries) {
                const row = document.createElement("article");
                row.className = `knowledge-entry${entry.id === app.selectedKnowledgeEntryId ? " is-selected" : ""}`;
                row.addEventListener("click", () => {
                    app.selectedKnowledgeEntryId = entry.id;
                    renderKnowledgeWorkspace();
                });
                row.append(
                        textElement("strong", entry.title || "未命名知识"),
                        textElement("p", entry.content || entry.statement || ""),
                        textElement("span", `${entry.evidenceStatus || "unverified"} · ${formatRelativeTime(entry.updatedAt || entry.createdAt)}`)
                );
                details.appendChild(row);
            }
        }
        target.appendChild(details);
    }
}

function renderKnowledgeDetail(entry) {
    dom.knowledgeDetail.replaceChildren();
    if (!entry) {
        dom.knowledgeDetail.appendChild(emptyBlock("选择知识条目后查看证据覆盖、来源和维护动作。"));
        return;
    }
    const detail = document.createElement("div");
    detail.className = "detail-stack";
    detail.append(
            textElement("h3", entry.title || "未命名知识"),
            detailRow("分区", SECTION_LABELS[entry.section] || entry.section || "待分类"),
            detailRow("证据状态", entry.evidenceStatus || "unverified"),
            textElement("p", entry.content || entry.statement || ""),
            detailRow("关联证据", `${entry.evidenceSourceIds?.length || 0} 条`),
            detailActions(["保存修改", "移动分区", "标为待验证", "归档"])
    );
    dom.knowledgeDetail.appendChild(detail);
}

function renderObservabilityWorkspace() {
    if (!dom.diagnosticsSummary) {
        return;
    }
    ensureSelectedDiagnostic();
    const session = app.sessions.find((item) => item.id === app.activeSessionId);
    const events = app.retrievalDiagnostics || [];
    const summary = summarizeRetrievalDiagnostics(events);
    dom.diagnosticsContext.textContent = session
            ? `${session.title || "当前会话"} · ${events.length} 条检索事件`
            : "选择会话后查看检索诊断。";
    dom.diagnosticsSummary.replaceChildren(
            metricItem("检索调用", summary.callCount),
            metricItem("查询改写", summary.queryCount),
            metricItem("返回片段", summary.returnedChunks),
            metricItem("零命中", summary.zeroHitCount)
    );
    dom.diagnosticsTaxonomy.replaceChildren(...taxonomyChips(summary.reasonCounts, app.diagnosticsLoadState));
    renderDiagnosticsEvents(events);
    renderDiagnosticsDetail(selectedDiagnostic());
}

function renderDiagnosticsEvents(events) {
    dom.diagnosticsEvents.replaceChildren();
    if (app.diagnosticsLoadState === "loading") {
        dom.diagnosticsEvents.appendChild(emptyBlock("正在载入检索诊断。"));
        return;
    }
    if (app.diagnosticsLoadState === "error") {
        dom.diagnosticsEvents.appendChild(emptyBlock(`检索诊断暂时不可用：${app.diagnosticsError}`));
        return;
    }
    if (!events.length) {
        const message = app.sampleMode
                ? "样例模式未配置检索诊断。"
                : "当前会话还没有返回检索诊断；这不等于检索已经成功。";
        dom.diagnosticsEvents.appendChild(emptyBlock(message));
        return;
    }
    for (const event of events) {
        const row = document.createElement("article");
        row.className = `diagnostics-row${event.id === app.selectedDiagnosticId ? " is-selected" : ""}`;
        row.dataset.diagnosticId = event.id;
        row.append(
                textElement("strong", `#${event.toolCallIndex ?? "?"} ${event.toolName || "paper_rag"}`),
                textElement("span", event.zeroHitReason || event.rewriteStrategy || "OK"),
                textElement("span", String(event.queryCount ?? event.retrievalQueryCount ?? 0)),
                textElement("span", String(event.returnedScopedChunkCount ?? 0)),
                textElement("span", backendSummary(event.backendStats)),
                textElement("span", formatRelativeTime(event.createdAt))
        );
        dom.diagnosticsEvents.appendChild(row);
    }
}

function renderDiagnosticsDetail(event) {
    dom.diagnosticsDetail.replaceChildren();
    if (!event) {
        const text = app.diagnosticsLoadState === "error"
                ? "诊断接口失败，暂无事件详情。"
                : "选择一条检索事件后查看边界、命中与后端统计。";
        dom.diagnosticsDetail.appendChild(emptyBlock(text));
        return;
    }
    const detail = document.createElement("div");
    detail.className = "detail-stack diagnostics-detail";
    detail.append(
            textElement("h3", event.originalQuery || event.query || "检索观察"),
            detailRow("工具", `${event.toolName || "paper_rag"} #${event.toolCallIndex ?? "?"}`),
            detailRow("策略", event.rewriteStrategy || "未知"),
            detailRow("零命中原因", event.zeroHitReason || "无"),
            detailRow("查询数", String(event.queryCount ?? event.retrievalQueryCount ?? 0)),
            detailRow("返回片段", String(event.returnedScopedChunkCount ?? 0)),
            detailRow("合并候选", String(event.mergedCandidateCount ?? "无")),
            detailRow("重排片段", String(event.rerankedChunkCount ?? "无"))
    );
    if (event.zeroHitReason) {
        const alert = document.createElement("div");
        alert.className = "detail-alert";
        alert.textContent = `零命中分类：${event.zeroHitReason}`;
        detail.appendChild(alert);
    }
    detail.appendChild(backendStatsTable(event.backendStats));
    if (event.retrievalQueries?.length) {
        const queries = document.createElement("div");
        queries.className = "diagnostics-query-list";
        queries.appendChild(textElement("strong", "检索查询"));
        queries.append(...event.retrievalQueries.slice(0, 5).map((query) => textElement("code", query)));
        detail.appendChild(queries);
    }
    if (event.topChunks?.length) {
        const chunks = document.createElement("div");
        chunks.className = "diagnostics-chunks";
        chunks.appendChild(textElement("strong", "关联证据"));
        chunks.append(...event.topChunks.slice(0, 3).map((chunk) => {
            const item = document.createElement("article");
            item.className = "process-evidence__item";
            item.append(
                    textElement("span", "资料", "process-source-type"),
                    textElement("strong", `Paper chunk #${chunk.chunkId ?? chunk.chunkIndex ?? "?"}`),
                    textElement("p", chunk.snippet || "暂无摘录。")
            );
            return item;
        }));
        detail.appendChild(chunks);
    }
    dom.diagnosticsDetail.appendChild(detail);
}

function backendStatsTable(stats = {}) {
    const table = document.createElement("div");
    table.className = "backend-stats";
    table.append(
            textElement("strong", "后端命中"),
            backendStatsRow("关键词", stats.keyword),
            backendStatsRow("向量", stats.vector),
            backendStatsRow("元数据", stats.metadata)
    );
    return table;
}

function backendStatsRow(name, stats = {}) {
    const row = document.createElement("div");
    row.className = "backend-stats-row";
    row.append(
            textElement("span", name),
            textElement("span", `${Number(stats.queryCount ?? 0)} 次查询`),
            textElement("span", `${Number(stats.preScopeHits ?? stats.hitCount ?? 0)} 初筛`),
            textElement("span", `${Number(stats.postScopeHits ?? stats.hitCount ?? 0)} 入界`)
    );
    return row;
}

function taxonomyChips(reasonCounts, loadState) {
    if (loadState === "error") {
        return [taxonomyChip("endpoint_failed", 1, true)];
    }
    if (loadState === "empty") {
        return [taxonomyChip("no_data", 0, false)];
    }
    const entries = Object.entries(reasonCounts);
    if (!entries.length) {
        return [taxonomyChip("no_zero_hit", 0, false)];
    }
    return entries.map(([reason, count]) => taxonomyChip(reason, count, true));
}

function taxonomyChip(label, count, warning) {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = `taxonomy-chip${warning ? " taxonomy-chip--warning" : ""}`;
    chip.textContent = `${taxonomyLabel(label)} ${count}`;
    return chip;
}

function taxonomyLabel(label) {
    const labels = {
        endpoint_failed: "接口失败",
        no_data: "暂无数据",
        no_zero_hit: "无零命中",
        UNKNOWN: "未知原因"
    };
    return labels[label] || String(label || "未知原因").replaceAll("_", " ");
}

function summarizeRetrievalDiagnostics(events) {
    return events.reduce((summary, event) => {
        const queryCount = Number(event.queryCount ?? event.retrievalQueryCount ?? 0);
        const returnedChunks = Number(event.returnedScopedChunkCount ?? 0);
        summary.callCount += 1;
        summary.queryCount += queryCount;
        summary.returnedChunks += returnedChunks;
        if (event.zeroHitReason || returnedChunks === 0) {
            summary.zeroHitCount += 1;
            const reason = event.zeroHitReason || "UNKNOWN";
            summary.reasonCounts[reason] = (summary.reasonCounts[reason] || 0) + 1;
        }
        return summary;
    }, { callCount: 0, queryCount: 0, returnedChunks: 0, zeroHitCount: 0, reasonCounts: {} });
}

function normalizeRetrievalDiagnostics(payload) {
    const events = Array.isArray(payload)
            ? payload
            : Array.isArray(payload?.retrievals)
                    ? payload.retrievals
            : Array.isArray(payload?.events)
                    ? payload.events
                    : Array.isArray(payload?.diagnostics)
                            ? payload.diagnostics
                            : Array.isArray(payload?.observations)
                                    ? payload.observations
                                    : [];
    return events.map((event, index) => normalizeRetrievalDiagnostic(event, index));
}

function normalizeRetrievalDiagnostic(raw = {}, index) {
    const observation = raw.observation || raw.retrievalObservation || raw;
    const summary = observation.retrievalObservationSummary || raw.retrievalObservationSummary || {};
    const backendStats = normalizeBackendStats(observation.backendStats || summary.backendStats || {});
    const retrievalQueries = raw.retrievalQueries || observation.retrievalQueries || observation.rewrite?.retrievalQueries || [];
    return {
        id: String(observation.observationId || raw.observationId || raw.id || raw.traceId || `diagnostic-${index}`),
        toolName: observation.toolName || raw.toolName || "paper_rag",
        toolCallIndex: observation.toolCallIndex ?? summary.toolCallIndex ?? raw.toolCallIndex ?? index + 1,
        originalQuery: observation.originalQuery || observation.query || raw.queryText || raw.query || "",
        rewriteStrategy: raw.rewriteStrategy || observation.rewriteStrategy || "unknown",
        retrievalQueries,
        keywords: raw.keywords || observation.keywords || [],
        queryCount: Number(observation.queryCount ?? observation.retrievalQueryCount ?? retrievalQueries.length ?? 0),
        backendStats,
        mergedCandidateCount: observation.mergedCandidateCount,
        rerankedChunkCount: observation.rerankedChunkCount,
        returnedScopedChunkCount: Number(raw.returnedScopedChunkCount ?? observation.returnedScopedChunkCount ?? summary.returnedScopedChunkCount ?? 0),
        zeroHitReason: raw.zeroHitReason || observation.zeroHitReason || summary.zeroHitReason || null,
        topChunks: Array.isArray(raw.topChunks) ? raw.topChunks : [],
        createdAt: observation.createdAt || raw.createdAt || ""
    };
}

function normalizeBackendStats(stats) {
    return {
        keyword: normalizeBackendStat(stats.keyword),
        vector: normalizeBackendStat(stats.vector),
        metadata: normalizeBackendStat(stats.metadata)
    };
}

function normalizeBackendStat(stat = {}) {
    return {
        queryCount: Number(stat.queryCount ?? 0),
        hitCount: Number(stat.hitCount ?? 0),
        preScopeHits: Number(stat.preScopeHits ?? stat.hitCount ?? 0),
        postScopeHits: Number(stat.postScopeHits ?? stat.hitCount ?? 0),
        durationMs: Number(stat.durationMs ?? 0)
    };
}

function selectedDiagnostic() {
    return (app.retrievalDiagnostics || []).find((event) => event.id === app.selectedDiagnosticId) || app.retrievalDiagnostics?.[0] || null;
}

function ensureSelectedDiagnostic() {
    if (!app.retrievalDiagnostics?.length) {
        app.selectedDiagnosticId = null;
        return;
    }
    if (!app.retrievalDiagnostics.some((event) => event.id === app.selectedDiagnosticId)) {
        app.selectedDiagnosticId = app.retrievalDiagnostics[0].id;
    }
}

function sampleRetrievalDiagnostics() {
    const now = new Date().toISOString();
    return [
        {
            id: "sample-diagnostic-1",
            toolName: "paper_rag",
            toolCallIndex: 1,
            originalQuery: "Space-Time Beamforming",
            rewriteStrategy: "original_only",
            retrievalQueries: ["Space-Time Beamforming"],
            keywords: ["space-time", "beamforming"],
            queryCount: 1,
            returnedScopedChunkCount: 3,
            zeroHitReason: null,
            backendStats: normalizeBackendStats({
                keyword: { queryCount: 1, preScopeHits: 5, postScopeHits: 2, durationMs: 4 },
                vector: { queryCount: 1, preScopeHits: 8, postScopeHits: 3, durationMs: 7 },
                metadata: { queryCount: 1, preScopeHits: 1, postScopeHits: 1, durationMs: 2 }
            }),
            topChunks: [
                { chunkId: 37, snippet: "Space-time beamforming aligns interference mitigation with spectral efficiency." }
            ],
            createdAt: now
        },
        {
            id: "sample-diagnostic-2",
            toolName: "paper_rag",
            toolCallIndex: 2,
            originalQuery: "Doppler shift diversity distinguish co-located users",
            rewriteStrategy: "generated_subquery",
            retrievalQueries: ["Doppler shift diversity distinguish co-located users"],
            keywords: ["Doppler", "co-located users"],
            queryCount: 1,
            returnedScopedChunkCount: 0,
            zeroHitReason: "SCOPE_FILTERED_EMPTY",
            backendStats: normalizeBackendStats({
                keyword: { queryCount: 1, preScopeHits: 0, postScopeHits: 0, durationMs: 2 },
                vector: { queryCount: 1, preScopeHits: 4, postScopeHits: 0, durationMs: 6 },
                metadata: { queryCount: 1, preScopeHits: 0, postScopeHits: 0, durationMs: 1 }
            }),
            topChunks: [],
            createdAt: now
        }
    ];
}

function backendSummary(stats = {}) {
    const keyword = Number(stats.keyword?.postScopeHits ?? stats.keyword?.hitCount ?? 0);
    const vector = Number(stats.vector?.postScopeHits ?? stats.vector?.hitCount ?? 0);
    const metadata = Number(stats.metadata?.postScopeHits ?? stats.metadata?.hitCount ?? 0);
    return `k${keyword} / v${vector} / m${metadata}`;
}

function renderEvidenceSources() {
    dom.evidenceList.replaceChildren();
    if (!app.evidenceSources.length) {
        dom.evidenceList.appendChild(emptyBlock("当前回答还没有最终引用。回答完成并完成证据评估后会显示。"));
        return;
    }
    for (const evidence of app.evidenceSources) {
        const item = document.createElement("article");
        item.className = "evidence-row";
        item.append(
                textElement("strong", evidence.sourceTitle || evidence.title || evidence.sourceType || "证据来源"),
                textElement("p", evidence.snippet || evidence.summary || "暂无摘录。"),
                textElement("span", `${evidence.sourceType || "local"} · ${evidence.strength || evidence.relevanceScore || "未评分"}`)
        );
        dom.evidenceList.appendChild(item);
    }
}

function renderCandidates(target) {
    target.replaceChildren();
    if (!app.candidates.length) {
        target.appendChild(emptyBlock("本轮尚未生成待确认候选。开启“提炼候选”后，回答完成时会出现在这里。"));
        return;
    }
    for (const candidate of app.candidates) {
        const item = document.createElement("article");
        item.className = "candidate-row";
        item.append(
                textElement("strong", candidate.title || "未命名候选"),
                textElement("p", candidate.statement || ""),
                textElement("span", `${SECTION_LABELS[candidate.suggestedSection] || candidate.suggestedSection || "待分类"} · ${candidate.status || "pending"}`)
        );
        if (app.editingCandidateId === candidate.id) {
            item.appendChild(candidateEditForm(candidate));
            target.appendChild(item);
            continue;
        }
        const actions = document.createElement("div");
        actions.className = "candidate-actions";
        actions.append(
                candidateButton(candidate.id, "accept", "写入知识库"),
                candidateButton(candidate.id, "mark-unverified", "标为待验证"),
                candidateButton(candidate.id, "ignore", "忽略"),
                candidateEditButton(candidate.id)
        );
        item.appendChild(actions);
        target.appendChild(item);
    }
}

function messageBody(message) {
    const body = document.createElement("div");
    body.className = "message-body";
    body.append(
            textElement("strong", message.role === "assistant" ? "研究回答" : "研究问题"),
            messageContentElement(message.content || (message.status === "streaming" ? "正在接收回答..." : "")),
            textElement("span", `${message.status || "sent"} · ${formatRelativeTime(message.createdAt)}`)
    );
    if (message.role === "assistant") {
        const process = researchProcessPanel(message);
        if (process) {
            body.appendChild(process);
        }
    }
    return body;
}

function messageContentElement(content) {
    const container = document.createElement("div");
    container.className = "message-content message-content--markdown";
    const lines = String(content || "").split(/\r?\n/);
    let list = null;
    let paragraphLines = [];

    const flushParagraph = () => {
        if (!paragraphLines.length) {
            return;
        }
        const paragraph = document.createElement("p");
        paragraphLines.forEach((line, index) => {
            if (index > 0) {
                paragraph.appendChild(document.createElement("br"));
            }
            paragraph.appendChild(document.createTextNode(line));
        });
        container.appendChild(paragraph);
        paragraphLines = [];
    };
    const closeList = () => {
        if (list) {
            container.appendChild(list);
            list = null;
        }
    };

    lines.forEach((rawLine) => {
        const line = rawLine.trimEnd();
        if (!line.trim()) {
            flushParagraph();
            closeList();
            return;
        }
        if (line.startsWith("# ")) {
            flushParagraph();
            closeList();
            const heading = document.createElement("h1");
            heading.textContent = line.slice(2).trim();
            container.appendChild(heading);
            return;
        }
        if (line.startsWith("## ")) {
            flushParagraph();
            closeList();
            const heading = document.createElement("h2");
            heading.textContent = line.slice(3).trim();
            container.appendChild(heading);
            return;
        }
        if (line.startsWith("- ")) {
            flushParagraph();
            if (!list) {
                list = document.createElement("ul");
            }
            const item = document.createElement("li");
            item.textContent = line.slice(2).trim();
            list.appendChild(item);
            return;
        }
        closeList();
        paragraphLines.push(line);
    });
    flushParagraph();
    closeList();
    return container;
}

function researchProcessPanel(message) {
    const runId = message.runId || (app.currentAnswer?.messageId === message.id ? app.currentAnswer.runId : null);
    const trace = runId ? app.agentTraces?.[runId] : null;
    const shouldShow = Boolean(trace) || message.status === "streaming" || Boolean(runId);
    if (!shouldShow) {
        return null;
    }

    const processId = runId || message.id;
    const isCollapsed = app.collapsedResearchProcesses.has(processId);

    const summary = trace?.summary || {};
    const tools = trace?.tools || [];
    const retrievalHits = trace?.retrievalHits || [];
    const memoryHits = trace?.memoryHits || [];
    const memoryCount = Number(summary.memoryCount ?? memoryHits.length);
    const evidenceEvents = trace?.evidenceEvents || [];
    const answerDeltas = trace?.answerDeltas || [];
    const gaps = evidenceEvents.filter((event) => event.eventType === "evidence.gap.detected");
    const subagentCount = Number(summary.activeSubagentCount ?? summary.subagentCount ?? 0);

    const section = document.createElement("section");
    section.className = `research-process${isCollapsed ? " is-collapsed" : ""}`;
    section.setAttribute("aria-label", "研究过程");

    const header = document.createElement("button");
    header.type = "button";
    header.className = "research-process__header";
    header.dataset.processToggle = processId;
    header.setAttribute("aria-expanded", String(!isCollapsed));
    header.append(
            textElement("span", processStatusLabel(message, trace), "process-status"),
            textElement("strong", "研究过程"),
            textElement("small", processSummaryText(tools, retrievalHits, memoryHits, gaps, answerDeltas, memoryCount, subagentCount)),
            textElement("span", isCollapsed ? "展开" : "收起", "process-toggle-label")
    );
    section.appendChild(header);
    if (isCollapsed) {
        return section;
    }

    const metrics = document.createElement("div");
    metrics.className = "process-metrics";
    metrics.append(
            processMetric("子Agent", subagentCount),
            processMetric("工具", processToolCount(tools)),
            processMetric("证据", Number(summary.evidenceCount ?? retrievalHits.length)),
            processMetric("记忆", memoryCount),
            processMetric("边界", gaps.length)
    );

    const timeline = document.createElement("ol");
    timeline.className = "process-timeline";
    const timelineItems = processTimelineItems(trace, message);
    timeline.replaceChildren(...timelineItems.map(processTimelineItem));

    const evidenceMatrix = document.createElement("div");
    evidenceMatrix.className = "process-evidence";
    evidenceMatrix.appendChild(textElement("strong", "过程命中"));
    const evidenceItems = [...retrievalHits, ...memoryHits].slice(0, 5);
    if (evidenceItems.length) {
        evidenceMatrix.append(...evidenceItems.map(processEvidenceItem));
    } else {
        evidenceMatrix.appendChild(textElement("p", "等待资料命中、联网补充或记忆召回事件。过程命中不等于最终引用。"));
    }

    if (gaps.length) {
        const gapList = document.createElement("div");
        gapList.className = "process-gaps";
        gapList.appendChild(textElement("strong", "证据边界"));
        gapList.append(...gaps.slice(0, 3).map((gap) => textElement("p", gap.reason || gap.claim || "当前回答存在证据边界。")));
        evidenceMatrix.appendChild(gapList);
    }

    section.append(metrics, timeline, evidenceMatrix);
    return section;
}

function processStatusLabel(message, trace) {
    if (message.status === "error") {
        return "失败";
    }
    if (message.status === "completed") {
        return "完成";
    }
    if (trace?.answerDeltas?.length) {
        return "生成中";
    }
    return "运行中";
}

function processSummaryText(tools, retrievalHits, memoryHits, gaps, answerDeltas, memoryCount = memoryHits.length, subagentCount = 0) {
    if (!subagentCount && !tools.length && !retrievalHits.length && !memoryHits.length && !answerDeltas.length) {
        return "等待后端步骤事件";
    }
    const parts = [];
    if (subagentCount) {
        parts.push(`${subagentCount} 个子Agent`);
    }
    if (tools.length) {
        parts.push(`${tools.length} 个工具事件`);
    }
    if (retrievalHits.length) {
        parts.push(`${retrievalHits.length} 条证据命中`);
    }
    if (memoryCount) {
        parts.push(`${memoryCount} 条记忆`);
    }
    if (gaps.length) {
        parts.push(`${gaps.length} 个边界提示`);
    }
    return parts.join(" · ");
}

function processMetric(label, value) {
    const item = document.createElement("div");
    item.className = "process-metric";
    item.append(textElement("span", label), textElement("strong", String(value)));
    return item;
}

function processToolCount(tools) {
    const keys = new Set();
    for (const tool of tools || []) {
        keys.add(tool.stepId || tool.toolName || tool.eventId);
    }
    return keys.size;
}

function processTimelineItems(trace, message) {
    const tools = trace?.tools || [];
    const retrievalHits = trace?.retrievalHits || [];
    const memoryHits = trace?.memoryHits || [];
    const evidenceEvents = trace?.evidenceEvents || [];
    const answerDeltas = trace?.answerDeltas || [];
    const agentEvents = (trace?.timeline || []).filter((event) => String(event.eventType || "").startsWith("agent."));
    const orderedEvents = [
        processPlanTimelineEvent(trace?.plan),
        ...agentEvents.map((event, index) => processAgentTimelineEvent(event, index)),
        ...tools.map((event, index) => processToolTimelineEvent(event, index)),
        ...retrievalHits.map((event, index) => processHitTimelineEvent(event, index, "retrieval")),
        ...memoryHits.map((event, index) => processHitTimelineEvent(event, index, "memory")),
        ...evidenceEvents.map((event, index) => processEvidenceTimelineEvent(event, index)),
        ...answerDeltas.slice(0, 1).map((event, index) => processAnswerTimelineEvent(event, index))
    ].filter(Boolean);
    if (!orderedEvents.length) {
        return [{
            eventType: message.status === "completed" ? "run.completed" : "run.started",
            status: message.status === "completed" ? "completed" : "running",
            label: message.status === "completed" ? "回答已完成" : "等待研究步骤",
            detail: message.status === "completed" ? "本轮没有额外工具事件。" : "后端事件到达后会更新这里。"
        }];
    }
    return orderedEvents.sort((left, right) => processEventOrder(left) - processEventOrder(right));
}

function processPlanTimelineEvent(plan) {
    if (!plan) {
        return null;
    }
    return {
        eventType: "agent.plan.created",
        status: "completed",
        label: "执行计划",
        detail: plan.summary || `${plan.steps?.length || 0} 个串行步骤`,
        order: 0
    };
}

function processAgentTimelineEvent(event, index) {
    return {
        eventType: event.eventType,
        status: agentTimelineStatus(event),
        label: event.label || event.actorDisplayName || agentRoleLabel(event.actorRole),
        detail: agentTimelineDetail(event),
        order: processEventOrderValue(event, 10 + index)
    };
}

function processToolTimelineEvent(event, index) {
    return {
        eventType: event.eventType,
        status: toolEventStatus(event),
        label: toolDisplayName(event),
        detail: toolDetail(event),
        order: processEventOrderValue(event, index)
    };
}

function processHitTimelineEvent(event, index, kind) {
    return {
        eventType: event.eventType,
        status: "completed",
        label: kind === "memory" ? "记忆命中" : "证据命中",
        detail: event.title || event.label || event.sourceId || event.snippet || "过程命中事件",
        order: processEventOrderValue(event, 100 + index)
    };
}

function processEvidenceTimelineEvent(event, index) {
    return {
        eventType: event.eventType,
        status: event.eventType === "evidence.gap.detected" ? "failed" : "completed",
        label: event.eventType === "evidence.gap.detected" ? "证据边界" : "证据评估",
        detail: event.reason || event.claim || event.resultSummary || "证据状态已更新",
        order: processEventOrderValue(event, 200 + index)
    };
}

function processAnswerTimelineEvent(event, index) {
    return {
        eventType: event.eventType,
        status: "running",
        label: "回答生成",
        detail: event.delta ? "正在组织最终回答。" : "开始生成回答。",
        order: processEventOrderValue(event, 300 + index)
    };
}

function processEventOrder(item) {
    return Number.isFinite(item.order) ? item.order : Number.MAX_SAFE_INTEGER;
}

function processEventOrderValue(event, fallback) {
    const numericValue = Number(event.sequence ?? event.offset);
    if (Number.isFinite(numericValue)) {
        return numericValue;
    }
    const timeValue = Date.parse(event.createdAt || event.timestamp || "");
    return Number.isFinite(timeValue) ? timeValue : fallback;
}

function processTimelineItem(item) {
    const li = document.createElement("li");
    li.className = `process-step process-step--${item.status}`;
    li.append(
            textElement("span", "", "process-step__dot"),
            textElement("strong", item.label || "研究步骤"),
            textElement("p", item.detail || "")
    );
    return li;
}

function toolEventStatus(event) {
    if (event.eventType === "tool.failed") {
        return "failed";
    }
    if (event.eventType === "tool.completed") {
        return "completed";
    }
    return "running";
}

function toolDisplayName(event) {
    const names = {
        paper_rag: "资料检索步骤",
        web_search: "联网检索步骤",
        tavily_web_search: "联网检索步骤",
        memory_recall: "记忆召回步骤"
    };
    return event.toolDisplayName || names[event.toolName] || event.toolName || "工具调用";
}

function toolDetail(event) {
    if (event.eventType === "tool.failed") {
        return `${event.errorType || "工具失败"} · ${event.message || "未返回错误详情"}`;
    }
    if (event.resultSummary) {
        return event.resultSummary;
    }
    if (event.query) {
        return `查询：${event.query}`;
    }
    return event.eventType === "tool.completed" ? "步骤已完成。" : "步骤运行中。";
}

function agentTimelineStatus(event) {
    if (event.status === "failed") {
        return "failed";
    }
    if (event.status === "completed") {
        return "completed";
    }
    return "running";
}

function agentTimelineDetail(event) {
    if (event.status === "failed") {
        return `${event.errorType || "步骤失败"} · ${event.message || "子 Agent 未完成"}`;
    }
    if (event.verdict) {
        return `审证结论：${event.verdict}`;
    }
    if (event.format || event.title) {
        return [event.format, event.title].filter(Boolean).join(" · ");
    }
    return event.status === "completed" ? "子 Agent 步骤已完成。" : "子 Agent 步骤运行中。";
}

function agentRoleLabel(role) {
    const labels = {
        deep_research_agent: "Deep Research Agent",
        evidence_audit_agent: "Evidence Audit Agent",
        document_composer_agent: "Document Composer Agent"
    };
    return labels[role] || role || "子 Agent";
}

function processEvidenceItem(event) {
    const item = document.createElement("article");
    item.className = "process-evidence__item";
    const type = event.sourceType === "web"
            ? "联网"
            : event.memoryLayer
                    ? "记忆"
                    : "资料";
    item.append(
            textElement("span", type, "process-source-type"),
            textElement("strong", event.title || event.label || event.sourceId || "研究来源"),
            textElement("p", event.snippet || event.reason || "暂无摘录。")
    );
    return item;
}

function updateAssistantMessage(messageId, patch) {
    app.messages = app.messages.map((message) => message.id === messageId ? { ...message, ...patch } : message);
}

function installSampleState(message) {
    app.sampleMode = true;
    app.activeProject = {
        id: null,
        topic: "样例研究项目",
        summary: "用于展示一级工作区导航的本地样例。"
    };
    app.activeProjectId = null;
    app.sessions = [{
        id: "sample-session",
        title: "研究线索梳理",
        status: "drafting",
        updatedAt: new Date().toISOString()
    }];
    app.activeSessionId = "sample-session";
    app.sources = sampleSources();
    app.knowledgeBoard = sampleKnowledgeBoard();
    app.candidates = sampleCandidates();
    app.selectedSourceId = app.sources[0]?.id || null;
    app.statusMessage = message;
}

function normalizeSource(raw) {
    const id = raw.sourceId || raw.id || raw.documentId;
    return {
        id: String(id),
        sourceId: String(id),
        type: raw.type || "document",
        title: raw.title || raw.originalFileName || `资料 ${id}`,
        status: String(raw.status || "unknown").toLowerCase(),
        failureStage: raw.failureStage || null,
        errorMessage: raw.errorMessage || "",
        depositedKnowledgeCount: Number(raw.depositedKnowledgeCount ?? 0),
        totalChunks: Number(raw.totalChunks ?? raw.chunkCount ?? 0),
        createdAt: raw.createdAt || "",
        updatedAt: raw.updatedAt || raw.createdAt || ""
    };
}

async function compatibilityListLegacyDocuments() {
    try {
        const payload = await getJson("/api/documents");
        const documents = Array.isArray(payload?.documents) ? payload.documents : [];
        return documents.map((documentRecord) => {
            const normalized = normalizeDocument(documentRecord);
            return normalizeSource({
                id: normalized.documentId,
                type: "document",
                title: normalized.title,
                status: normalized.status,
                totalChunks: normalized.totalChunks,
                depositedKnowledgeCount: 0,
                updatedAt: normalized.updatedAt
            });
        });
    } catch (error) {
        return app.sources?.length ? app.sources : sampleSources();
    }
}

async function compatibilityUploadLegacyDocument(formData) {
    const payload = await postForm("/api/documents/upload", formData);
    return {
        id: payload.documentId,
        sourceId: payload.documentId,
        type: "document",
        title: payload.originalFileName || `文档 ${payload.documentId}`,
        status: payload.status || "uploaded"
    };
}

function hasProjectApi() {
    return Boolean(app.activeProjectId && !app.sampleMode);
}

async function getJson(url) {
    const response = await fetch(url);
    return readResponse(response);
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
    return readResponse(response);
}

async function patchJson(url, body) {
    const response = await fetch(url, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
    return readResponse(response);
}

async function deleteJson(url) {
    const response = await fetch(url, { method: "DELETE" });
    return readResponse(response);
}

async function postForm(url, formData) {
    const response = await fetch(url, { method: "POST", body: formData });
    return readResponse(response);
}

async function readResponse(response) {
    const text = await response.text();
    const payload = text ? parseEventData(text) : null;
    if (!response.ok) {
        throw new Error(payload?.message || payload?.error || `HTTP ${response.status}`);
    }
    return payload;
}

function parseEventData(raw) {
    if (!raw || typeof raw !== "string") {
        return raw || {};
    }
    try {
        return JSON.parse(raw);
    } catch (error) {
        return { text: raw };
    }
}

function closeStream() {
    if (app.activeStream) {
        app.activeStream.close();
        app.activeStream = null;
    }
}

function scrollConversationToBottom() {
    dom.conversation.scrollTop = dom.conversation.scrollHeight;
}

function answerStateText() {
    const answer = app.currentAnswer || {};
    if (answer.status === "streaming") {
        return "回答生成中";
    }
    if (answer.status === "completed") {
        return `${answer.outputMode || "已完成"} · ${answer.citationCount || 0} 条引用`;
    }
    if (answer.status === "error") {
        return "回答失败";
    }
    return "等待问题";
}

function knowledgeEntryCount() {
    return normalizedBoard().sections.reduce((sum, section) => sum + (section.entries?.length || 0), 0);
}

function normalizedBoard() {
    return createWorkbenchState({ knowledgeBoard: app.knowledgeBoard }).knowledgeBoard;
}

function selectedSource() {
    return app.sources.find((source) => source.id === app.selectedSourceId) || app.sources[0] || null;
}

function ensureSelectedSource() {
    if (!app.sources.length) {
        app.selectedSourceId = null;
        return;
    }
    if (!app.sources.some((source) => source.id === app.selectedSourceId)) {
        app.selectedSourceId = app.sources[0].id;
    }
}

function selectedKnowledgeEntry() {
    const entries = normalizedBoard().sections.flatMap((section) => section.entries.map((entry) => ({ ...entry, section: entry.section || section.section })));
    if (!entries.length) {
        return null;
    }
    return entries.find((entry) => entry.id === app.selectedKnowledgeEntryId) || entries[0];
}

function countSourcesByStatus() {
    return app.sources.reduce((counts, source) => {
        const status = String(source.status || "unknown").toLowerCase();
        counts[status] = (counts[status] || 0) + 1;
        return counts;
    }, {
        uploaded: 0,
        submitted: 0,
        parsing: 0,
        fetching: 0,
        indexing: 0,
        extracting: 0,
        depositing: 0,
        deposited: 0,
        indexed: 0,
        failed: 0
    });
}

function isFailedSource(source) {
    return String(source.status || "").toLowerCase() === "failed" || Boolean(source.failureStage);
}

function sourceTypeLabel(type) {
    return SOURCE_LABELS[String(type || "").toLowerCase()] || type || "资料";
}

function sessionStatusLabel(status) {
    const labels = {
        continue: "进行中",
        drafting: "草稿",
        completed: "已完成",
        failed: "失败"
    };
    return labels[String(status || "").toLowerCase()] || "进行中";
}

function statusChip(status) {
    const chip = document.createElement("mark");
    chip.dataset.status = String(status || "unknown").toLowerCase();
    chip.textContent = status || "unknown";
    return chip;
}

function contextNavItem(label, count, active = false) {
    const item = document.createElement("button");
    item.type = "button";
    item.className = `context-nav-item${active ? " is-active" : ""}`;
    item.append(textElement("span", label), textElement("strong", String(count)));
    return item;
}

function metricItem(label, count) {
    const item = document.createElement("div");
    item.className = "metric-item";
    item.append(textElement("strong", String(count)), textElement("span", label));
    return item;
}

function detailRow(label, value) {
    const row = document.createElement("div");
    row.className = "detail-row";
    row.append(textElement("span", label), textElement("strong", value || "—"));
    return row;
}

function detailActions(labels) {
    const actions = document.createElement("div");
    actions.className = "detail-actions";
    actions.append(...labels.map((label, index) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = index === 0 ? "primary-button" : "quiet-button";
        button.textContent = label;
        return button;
    }));
    return actions;
}

function sessionActionsMenu(session) {
    const menu = document.createElement("div");
    menu.className = "session-actions-menu";
    menu.setAttribute("role", "menu");
    menu.append(
            sessionMenuAction(session.id, "rename", "重命名"),
            sessionMenuAction(session.id, "delete", "删除")
    );
    return menu;
}

function sessionMenuAction(sessionId, action, label) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = action === "delete" ? "session-menu-item session-menu-item--danger" : "session-menu-item";
    button.setAttribute("role", "menuitem");
    if (action === "delete") {
        button.dataset.sessionDelete = sessionId;
    } else {
        button.dataset.sessionRename = sessionId;
    }
    button.textContent = label;
    return button;
}

function candidateButton(candidateId, action, label) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "quiet-button";
    button.dataset.candidateId = candidateId;
    button.dataset.candidateAction = action;
    button.textContent = label;
    return button;
}

function candidateEditButton(candidateId) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "quiet-button";
    button.dataset.candidateEdit = candidateId;
    button.textContent = app.editingCandidateId === candidateId ? "编辑中" : "编辑";
    return button;
}

function sessionIconButton(sessionId, action, label, icon) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = action === "delete" ? "icon-button icon-button--danger" : "icon-button";
    if (action === "delete") {
        button.dataset.sessionDelete = sessionId;
    } else {
        button.dataset.sessionRename = sessionId;
    }
    button.title = label;
    button.setAttribute("aria-label", label);
    button.textContent = icon;
    return button;
}

function candidateEditForm(candidate) {
    const form = document.createElement("div");
    form.className = "candidate-edit-form";
    form.dataset.candidateEditForm = candidate.id;
    const titleInput = document.createElement("input");
    titleInput.name = "title";
    titleInput.value = candidate.title || "";
    titleInput.placeholder = "知识标题";
    const contentInput = document.createElement("textarea");
    contentInput.name = "content";
    contentInput.rows = 4;
    contentInput.value = candidate.statement || candidate.content || "";
    contentInput.placeholder = "确认后的知识内容";
    const sectionSelect = document.createElement("select");
    sectionSelect.name = "section";
    for (const section of ["core_concept", "method_route", "confirmed_finding", "open_question"]) {
        const option = document.createElement("option");
        option.value = section;
        option.textContent = SECTION_LABELS[section] || section;
        option.selected = section === candidate.suggestedSection;
        sectionSelect.appendChild(option);
    }
    const evidenceSelect = document.createElement("select");
    evidenceSelect.name = "evidenceStatus";
    for (const status of [{ value: "confirmed", label: "已确认" }, { value: "unverified", label: "待验证" }]) {
        const option = document.createElement("option");
        option.value = status.value;
        option.textContent = status.label;
        evidenceSelect.appendChild(option);
    }
    const actions = document.createElement("div");
    actions.className = "candidate-actions";
    const submit = document.createElement("button");
    submit.type = "button";
    submit.className = "primary-button";
    submit.dataset.candidateEditSubmit = candidate.id;
    submit.textContent = "确认写入";
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "quiet-button";
    cancel.dataset.candidateEditCancel = candidate.id;
    cancel.textContent = "取消";
    actions.append(submit, cancel);
    form.append(titleInput, contentInput, sectionSelect, evidenceSelect, actions);
    return form;
}

function sessionRenameForm(session) {
    const form = document.createElement("div");
    form.className = "session-rename-form";
    const input = document.createElement("input");
    input.type = "text";
    input.value = session.title || "";
    input.maxLength = 120;
    input.dataset.sessionRenameInput = session.id;
    input.setAttribute("aria-label", "会话名称");
    const actions = document.createElement("div");
    actions.className = "session-row-actions";
    const submit = document.createElement("button");
    submit.type = "button";
    submit.className = "primary-button";
    submit.dataset.sessionRenameSubmit = session.id;
    submit.textContent = "保存";
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "quiet-button";
    cancel.dataset.sessionRenameCancel = session.id;
    cancel.textContent = "取消";
    actions.append(submit, cancel);
    form.append(input, actions);
    return form;
}

function focusSessionRenameInput() {
    requestAnimationFrame(() => {
        const input = dom.sessionList.querySelector("[data-session-rename-input]");
        if (input) {
            input.focus();
            input.select();
        }
    });
}

function readCandidateEditForm(form) {
    if (!form) {
        return {};
    }
    const title = form.querySelector('[name="title"]')?.value?.trim() || "未命名知识";
    const content = form.querySelector('[name="content"]')?.value?.trim() || "";
    const section = form.querySelector('[name="section"]')?.value || "open_question";
    const evidenceStatus = form.querySelector('[name="evidenceStatus"]')?.value || "unverified";
    return { title, content, section, evidenceStatus };
}

function emptyBlock(text) {
    return textElement("div", text, "empty-state");
}

function textElement(tagName, text, className = "") {
    const element = document.createElement(tagName);
    if (className) {
        element.className = className;
    }
    element.textContent = text ?? "";
    return element;
}

function nextId(prefix) {
    if (window.crypto?.randomUUID) {
        return `${prefix}-${window.crypto.randomUUID()}`;
    }
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function cssEscape(value) {
    if (window.CSS?.escape) {
        return window.CSS.escape(value);
    }
    return String(value).replaceAll('"', '\\"');
}

function sampleSources() {
    const now = new Date().toISOString();
    return [
        normalizeSource({ id: "source-1", type: "pdf", title: "Topological Insulators and Berry Phase", status: "deposited", depositedKnowledgeCount: 8, totalChunks: 24, updatedAt: now }),
        normalizeSource({ id: "source-2", type: "pdf", title: "Spin-Momentum Locking in Bi2Se3", status: "indexing", depositedKnowledgeCount: 0, totalChunks: 0, updatedAt: now }),
        normalizeSource({ id: "source-3", type: "note", title: "Quantum Hall Effect Notes", status: "deposited", depositedKnowledgeCount: 3, totalChunks: 12, updatedAt: now }),
        normalizeSource({ id: "source-4", type: "web_page", title: "Nature: Topological Physics Review", status: "failed", failureStage: "fetching", errorMessage: "网页抓取超时，可重试。", updatedAt: now })
    ];
}

function sampleKnowledgeBoard() {
    return createWorkbenchState({
        knowledgeBoard: [
            {
                section: "core_concept",
                entries: [
                    { id: "k-1", section: "core_concept", title: "拓扑绝缘体中的贝里相位", content: "贝里相位可作为能带拓扑性质的可观测线索。", evidenceStatus: "confirmed", evidenceSourceIds: ["e-1"], updatedAt: new Date().toISOString() }
                ]
            },
            {
                section: "method_route",
                entries: [
                    { id: "k-2", section: "method_route", title: "从边缘态验证体拓扑", content: "先定位边缘导电态，再回到体能带结构解释。", evidenceStatus: "confirmed", evidenceSourceIds: ["e-2"], updatedAt: new Date().toISOString() }
                ]
            },
            {
                section: "open_question",
                entries: [
                    { id: "k-3", section: "open_question", title: "马约拉纳零模证据强度", content: "当前证据仍需排除非拓扑平庸态解释。", evidenceStatus: "unverified", evidenceSourceIds: [], updatedAt: new Date().toISOString() }
                ]
            }
        ]
    }).knowledgeBoard;
}

function sampleCandidates() {
    return [
        {
            id: "c-1",
            status: "pending",
            title: "马约拉纳费米子观测证据",
            statement: "零偏压峰不能单独作为马约拉纳零模的充分证据。",
            suggestedSection: "open_question",
            evidenceSourceIds: ["e-3"]
        },
        {
            id: "c-2",
            status: "pending",
            title: "自旋动量锁定实验路径",
            statement: "ARPES 与输运测量可互补验证自旋动量锁定。",
            suggestedSection: "method_route",
            evidenceSourceIds: ["e-4"]
        }
    ];
}
