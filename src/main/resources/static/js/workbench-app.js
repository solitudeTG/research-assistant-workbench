import {
    applyCandidateAction,
    applySseEvent,
    applyStreamEvent,
    createLocalSession,
    createWorkbenchState,
    formatRelativeTime,
    normalizeDocument,
    selectSession,
    selectSidebarView,
    startEditingCandidate
} from "./workbench-model.js";

const EVENT_TYPES = [
    "run.started",
    "retrieval.started",
    "retrieval.completed",
    "evidence.evaluated",
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
    candidateList: document.getElementById("candidate-list")
};

const app = {
    ...createWorkbenchState(),
    projects: [],
    activeProject: null,
    systemState: "checking",
    statusMessage: "正在连接研究工作台",
    messages: [],
    activeStream: null,
    sampleMode: false
};

wireEvents();
render();
void bootstrap();

function wireEvents() {
    dom.newSessionButton.addEventListener("click", () => {
        void createResearchSession();
    });

    dom.refreshSourcesButton.addEventListener("click", () => {
        void refreshSources();
    });

    dom.uploadButton.addEventListener("click", () => {
        void uploadSource();
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

    dom.sessionList.addEventListener("click", (event) => {
        const trigger = event.target.closest("[data-session-id]");
        if (!trigger) {
            return;
        }
        Object.assign(app, selectSession(app, trigger.dataset.sessionId));
        app.messages = [];
        render();
    });

    dom.tabs.forEach((tab) => {
        tab.addEventListener("click", () => {
            Object.assign(app, selectSidebarView(app, tab.dataset.sidebarView));
            renderSidebar();
        });
    });

    dom.candidateList.addEventListener("click", (event) => {
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
            renderCandidates();
            return;
        }
        if (editTrigger) {
            Object.assign(app, startEditingCandidate(app, editTrigger.dataset.candidateEdit));
            renderCandidates();
            return;
        }
        if (actionTrigger) {
            void submitCandidateAction(
                    actionTrigger.dataset.candidateAction,
                    actionTrigger.dataset.candidateId
            );
        }
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
            installSampleState("当前后端还没有项目。界面使用空项目样例，创建项目后会自动切换到真实数据。");
            return;
        }
        app.sampleMode = false;
        app.activeProject = app.projects[0];
        app.activeProjectId = app.activeProject.id;
        await Promise.allSettled([
            loadSessions(),
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
    const sessions = await getJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions`);
    app.sessions = Array.isArray(sessions) ? sessions : [];
    if (app.sessions.length === 0) {
        const session = await postJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions`, {
            title: "研究线索梳理"
        });
        app.sessions = [session];
    }
    app.activeSessionId = app.sessions[0]?.id || null;
}

async function createResearchSession() {
    if (!hasProjectApi()) {
        const session = createLocalSession("本地研究会话");
        app.sessions = [{ ...session, id: session.sessionKey, status: "drafting", lastMessageAt: session.updatedAt }, ...app.sessions];
        Object.assign(app, selectSession(app, session.sessionKey));
        render();
        return;
    }

    try {
        const title = `研究会话 ${app.sessions.length + 1}`;
        const session = await postJson(`/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions`, { title });
        app.sessions = [session, ...app.sessions];
        Object.assign(app, selectSession(app, session.id));
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
        app.sources = [normalizeSource(source), ...app.sources.filter((item) => item.id !== (source.sourceId || source.id))];
        dom.fileInput.value = "";
        app.statusMessage = "资料已进入处理队列。";
    } catch (error) {
        app.statusMessage = `资料导入失败：${error.message}`;
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

    if (!hasProjectApi() || !app.activeSessionId) {
        compatibilitySendLegacyChatStream(question, assistantId);
        return;
    }

    try {
        const response = await postJson(
                `/api/projects/${encodeURIComponent(app.activeProjectId)}/sessions/${encodeURIComponent(app.activeSessionId)}/messages`,
                {
                    question,
                    sourceFilters: [],
                    allowWebSupplement: dom.allowWeb.checked,
                    extractKnowledgeCandidates: dom.extractCandidates.checked,
                    answerMode: "local_first"
                }
        );
        app.currentAnswer.answerId = response.answerId;
        app.activeAnswerContext = {
            answerId: response.answerId,
            citationCount: 0,
            candidateCount: 0
        };
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
        app.currentAnswer = {
            ...(app.currentAnswer || {}),
            status: "error"
        };
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
        app.statusMessage = `璇佹嵁鏉ユ簮鏆備笉鍙敤锛?{error.message}`;
    }
}

async function submitCandidateAction(action, candidateId, editPayload = null) {
    if (!candidateId) {
        return;
    }
    Object.assign(app, applyCandidateAction(app, candidateId, action));
    renderCandidates();

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
    renderProject();
    renderSessions();
    renderSources();
    renderDialogue();
    renderSidebar();
}

function renderStatus() {
    dom.systemStatus.dataset.state = app.systemState;
    dom.systemStatus.textContent = app.systemState === "ok" ? "在线" : app.systemState === "error" ? "离线" : "连接中";
    dom.statusBanner.textContent = app.statusMessage;
}

function renderProject() {
    const project = app.activeProject;
    dom.projectTopic.textContent = project?.topic || "项目空间";
    dom.projectDescription.textContent = project?.summary || "当前没有真实项目数据，仍可查看三栏工作台结构。";
    dom.projectSummary.textContent = project?.summary || "项目级资料、会话、证据与知识板";
    dom.activeProjectChip.textContent = project?.id ? `项目 ${project.id.slice(0, 8)}` : "本地样例";
    dom.statSources.textContent = String(app.sources.length);
    dom.statKnowledge.textContent = String(knowledgeEntryCount());
    dom.statSessions.textContent = String(app.sessions.length);
}

function renderSessions() {
    dom.sessionList.replaceChildren();
    if (!app.sessions.length) {
        dom.sessionList.appendChild(emptyBlock("还没有研究会话。"));
        return;
    }
    for (const session of app.sessions) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `row-item${session.id === app.activeSessionId ? " is-active" : ""}`;
        button.dataset.sessionId = session.id;
        button.append(
                textElement("strong", session.title || "未命名会话"),
                textElement("span", `${session.status || "drafting"} · ${formatRelativeTime(session.lastMessageAt || session.updatedAt)}`)
        );
        dom.sessionList.appendChild(button);
    }
}

function renderSources() {
    dom.sourceCount.textContent = `当前 ${app.sources.length} 份资料`;
    dom.sourceList.replaceChildren();
    if (!app.sources.length) {
        dom.sourceList.appendChild(emptyBlock("资料库为空。导入论文、网页或个人笔记后，状态会显示在这里。"));
        return;
    }
    for (const source of app.sources) {
        const item = document.createElement("article");
        item.className = "source-row";
        item.innerHTML = `
            <div>
                <strong>${escapeHtml(source.title || "未命名资料")}</strong>
                <span>${escapeHtml(sourceTypeLabel(source.type))} · ${escapeHtml(source.depositedKnowledgeCount ?? 0)} 条知识</span>
            </div>
            <mark data-status="${escapeHtml(source.status || "unknown")}">${escapeHtml(source.status || "unknown")}</mark>
        `;
        dom.sourceList.appendChild(item);
    }
}

function renderDialogue() {
    const session = app.sessions.find((item) => item.id === app.activeSessionId);
    dom.activeSessionTitle.textContent = session?.title || "多轮研究对话";
    dom.activeSessionMeta.textContent = session
            ? `${session.status || "drafting"} · 最近更新 ${formatRelativeTime(session.lastMessageAt || session.updatedAt)}`
            : "选择会话后开始提问。";
    dom.answerState.textContent = answerStateText();
    dom.conversation.replaceChildren();
    if (!app.messages.length) {
        dom.conversation.appendChild(emptyBlock("在下方输入研究问题。回答是研究过程，确认后的知识才会写入右侧知识板。"));
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
    dom.answerContextLine.textContent = `当前回答关联 ${citationCount} 条引用，${candidateCount} 条待确认候选。`;
    renderKnowledgeBoard();
    renderEvidenceSources();
    renderCandidates();
}

function renderKnowledgeBoard() {
    dom.knowledgeBoard.replaceChildren();
    const board = createWorkbenchState({ knowledgeBoard: app.knowledgeBoard }).knowledgeBoard;
    for (const section of board.sections) {
        const details = document.createElement("details");
        details.className = "knowledge-section";
        details.open = section.entries.length > 0 || section.section === "core_concept";
        const summary = document.createElement("summary");
        summary.append(
                textElement("span", SECTION_LABELS[section.section] || section.title || section.section),
                textElement("small", String(section.entries.length))
        );
        details.appendChild(summary);
        if (section.entries.length === 0) {
            details.appendChild(emptyBlock("暂无已确认条目。"));
        } else {
            for (const entry of section.entries) {
                const row = document.createElement("article");
                row.className = "knowledge-entry";
                row.append(
                        textElement("strong", entry.title || "未命名知识"),
                        textElement("p", entry.content || entry.statement || ""),
                        textElement("span", entry.evidenceStatus || "unverified")
                );
                details.appendChild(row);
            }
        }
        dom.knowledgeBoard.appendChild(details);
    }
}

function renderEvidenceSources() {
    dom.evidenceList.replaceChildren();
    if (!app.evidenceSources.length) {
        dom.evidenceList.appendChild(emptyBlock("当前回答尚未返回可审计证据。"));
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

function renderCandidates() {
    dom.candidateList.replaceChildren();
    if (!app.candidates.length) {
        dom.candidateList.appendChild(emptyBlock("回答产生的知识候选会出现在这里，确认前不会进入知识板。"));
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
            dom.candidateList.appendChild(item);
            continue;
        }
        const actions = document.createElement("div");
        actions.className = "candidate-actions";
        actions.append(
                candidateButton(candidate.id, "accept", "写入"),
                candidateButton(candidate.id, "mark-unverified", "待验证"),
                candidateButton(candidate.id, "ignore", "忽略"),
                candidateEditButton(candidate.id)
        );
        item.appendChild(actions);
        dom.candidateList.appendChild(item);
    }
}

function messageBody(message) {
    const body = document.createElement("div");
    body.className = "message-body";
    body.append(
            textElement("strong", message.role === "assistant" ? "证据约束回答" : "研究问题"),
            textElement("p", message.content || (message.status === "streaming" ? "正在接收回答..." : "")),
            textElement("span", `${message.status || "sent"} · ${formatRelativeTime(message.createdAt)}`)
    );
    return body;
}

function updateAssistantMessage(messageId, patch) {
    app.messages = app.messages.map((message) => message.id === messageId ? { ...message, ...patch } : message);
}

function installSampleState(message) {
    app.sampleMode = true;
    app.activeProject = {
        id: null,
        topic: "样例研究项目",
        summary: "用于展示 F010 三栏工作台的空状态。"
    };
    app.activeProjectId = null;
    app.sessions = [{
        id: "sample-session",
        title: "研究线索梳理",
        status: "drafting",
        updatedAt: new Date().toISOString()
    }];
    app.activeSessionId = "sample-session";
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
                depositedKnowledgeCount: 0,
                updatedAt: normalized.updatedAt
            });
        });
    } catch (error) {
        return [];
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

function compatibilitySendLegacyChatStream(question, assistantMessageId) {
    const streamState = {
        answerParts: [],
        citations: [],
        trace: [],
        telemetry: {}
    };
    const params = new URLSearchParams({
        sessionKey: app.activeSessionId || "sample-session",
        question
    });
    const source = new EventSource(`/api/chat/stream?${params.toString()}`);
    app.activeStream = source;

    source.addEventListener("message", (event) => {
        Object.assign(streamState, applyStreamEvent(streamState, "message", event.data));
        updateAssistantMessage(assistantMessageId, {
            content: streamState.answerParts.join(" "),
            status: "streaming"
        });
        renderDialogue();
    });

    source.addEventListener("done", (event) => {
        const payload = parseEventData(event.data);
        Object.assign(streamState, applyStreamEvent(streamState, "done", payload));
        updateAssistantMessage(assistantMessageId, {
            content: payload.answer || streamState.answerParts.join(" "),
            status: "completed"
        });
        app.currentAnswer = {
            ...(app.currentAnswer || {}),
            status: "completed",
            outputMode: payload.answerMode,
            citationCount: Array.isArray(payload.citations) ? payload.citations.length : 0
        };
        app.statusMessage = "兼容回答已完成。";
        closeStream();
        render();
    });

    source.onerror = () => {
        updateAssistantMessage(assistantMessageId, {
            content: "兼容聊天接口暂不可用。请先创建 F002 项目后再提问。",
            status: "error"
        });
        app.statusMessage = "兼容聊天接口连接失败。";
        closeStream();
        render();
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

async function postForm(url, formData) {
    const response = await fetch(url, {
        method: "POST",
        body: formData
    });
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
    return (app.knowledgeBoard?.sections || []).reduce((sum, section) => sum + (section.entries?.length || 0), 0);
}

function sourceTypeLabel(type) {
    return SOURCE_LABELS[String(type || "").toLowerCase()] || type || "资料";
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
    const evidenceStatusOptions = [
        { value: "confirmed", label: "已确认" },
        { value: "unverified", label: "待验证" }
    ];
    for (const status of evidenceStatusOptions) {
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

function escapeHtml(value) {
    return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
}

function nextId(prefix) {
    if (window.crypto?.randomUUID) {
        return `${prefix}-${window.crypto.randomUUID()}`;
    }
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
