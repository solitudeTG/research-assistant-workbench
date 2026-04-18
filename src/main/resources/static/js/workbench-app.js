import {
    applyStreamEvent,
    buildAnalysisViewModel,
    createLocalSession,
    formatRelativeTime,
    normalizeDocument
} from "./workbench-model.js";

const STORAGE_KEYS = {
    sessions: "research-assistant.workbench.sessions",
    activeSession: "research-assistant.workbench.active-session"
};

const state = {
    sessions: [],
    documents: [],
    selectedDocumentId: null,
    analysis: null,
    systemState: "checking",
    statusMessage: "正在启动工作台...",
    focusedMessageId: null,
    activeStream: null
};

const elements = {
    systemStatus: document.getElementById("system-status"),
    statusBanner: document.getElementById("status-banner"),
    sessionList: document.getElementById("session-list"),
    documentList: document.getElementById("document-list"),
    documentCount: document.getElementById("document-count"),
    uploadButton: document.getElementById("upload-button"),
    fileInput: document.getElementById("file-input"),
    refreshButton: document.getElementById("refresh-documents"),
    newSessionButton: document.getElementById("new-session"),
    activeSessionTitle: document.getElementById("active-session-title"),
    activeSessionMeta: document.getElementById("active-session-meta"),
    documentChips: document.getElementById("document-chips"),
    conversation: document.getElementById("conversation"),
    composer: document.getElementById("composer"),
    sendButton: document.getElementById("send-button"),
    deepResearchToggle: document.getElementById("deep-research"),
    autoVerifyToggle: document.getElementById("auto-verify"),
    selectedDocumentLabel: document.getElementById("selected-document-label"),
    analysisSummary: document.getElementById("analysis-summary"),
    analysisAbstract: document.getElementById("analysis-abstract"),
    analysisMethods: document.getElementById("analysis-methods"),
    analysisContributions: document.getElementById("analysis-contributions"),
    analysisKeywords: document.getElementById("analysis-keywords"),
    analysisOutline: document.getElementById("analysis-outline"),
    citationList: document.getElementById("citation-list"),
    traceList: document.getElementById("trace-list"),
    telemetryAnswerMode: document.getElementById("telemetry-answer-mode"),
    telemetryLatency: document.getElementById("telemetry-latency"),
    telemetryEvidence: document.getElementById("telemetry-evidence"),
    telemetryContext: document.getElementById("telemetry-context"),
    fallbackCapabilities: document.getElementById("fallback-capabilities")
};

hydrateSessions();
wireEvents();
renderAll();
void bootstrap();

function wireEvents() {
    elements.newSessionButton.addEventListener("click", () => {
        const session = createSession("新研究会话");
        setActiveSession(session.sessionKey);
        renderAll();
    });

    elements.refreshButton.addEventListener("click", () => {
        void refreshDocuments();
    });

    elements.uploadButton.addEventListener("click", () => {
        void uploadDocument();
    });

    elements.sendButton.addEventListener("click", () => {
        void askQuestion();
    });

    elements.composer.addEventListener("keydown", (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
            event.preventDefault();
            void askQuestion();
        }
    });

    elements.sessionList.addEventListener("click", (event) => {
        const trigger = event.target.closest("[data-session-key]");
        if (!trigger) {
            return;
        }
        setActiveSession(trigger.dataset.sessionKey);
        renderAll();
    });

    elements.documentList.addEventListener("click", (event) => {
        const trigger = event.target.closest("[data-document-id]");
        if (!trigger) {
            return;
        }
        const documentId = Number(trigger.dataset.documentId);
        state.selectedDocumentId = documentId;
        const session = getActiveSession();
        if (session && !session.pinnedDocumentIds.includes(documentId)) {
            session.pinnedDocumentIds = [documentId];
            touchSession(session);
        }
        void loadAnalysis(documentId);
        renderAll();
    });

    elements.conversation.addEventListener("click", (event) => {
        const trigger = event.target.closest("[data-message-id]");
        if (!trigger) {
            return;
        }
        state.focusedMessageId = trigger.dataset.messageId;
        renderInspector();
    });
}

async function bootstrap() {
    await Promise.allSettled([pingSystem(), refreshDocuments()]);
    renderAll();
}

async function pingSystem() {
    try {
        const response = await fetch("/api/system/ping");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        state.systemState = "ok";
    } catch (error) {
        state.systemState = "error";
        state.statusMessage = `后端健康检查失败：${error.message}`;
    }
}

async function refreshDocuments() {
    try {
        const response = await fetch("/api/documents");
        const payload = await readJsonSafely(response);
        if (!response.ok) {
            throw new Error(payload?.error || `HTTP ${response.status}`);
        }
        state.documents = Array.isArray(payload?.documents)
                ? payload.documents.map(normalizeDocument)
                : [];
        elements.documentCount.textContent = `当前共有 ${state.documents.length} 份工作台文档`;
        syncSelectedDocument();
        if (state.selectedDocumentId) {
            await loadAnalysis(state.selectedDocumentId);
        }
        if (state.documents.length === 0) {
            state.statusMessage = "当前还没有文档，请先上传论文。";
        }
    } catch (error) {
        state.documents = [];
        state.analysis = null;
        elements.documentCount.textContent = "文档库暂不可用";
        state.statusMessage = `文档工作台暂不可用：${error.message}`;
    }
    renderAll();
}

async function loadAnalysis(documentId) {
    if (!documentId) {
        state.analysis = null;
        renderInspector();
        return;
    }

    try {
        const response = await fetch(`/api/documents/${documentId}/analysis`);
        if (response.status === 404) {
            state.analysis = null;
            renderInspector();
            return;
        }
        const payload = await readJsonSafely(response);
        if (!response.ok) {
            throw new Error(payload?.error || `HTTP ${response.status}`);
        }
        state.analysis = payload;
    } catch (error) {
        state.analysis = null;
        state.statusMessage = `文档 ${documentId} 的结构化分析暂不可用：${error.message}`;
    }
    renderInspector();
}

async function uploadDocument() {
    if (!elements.fileInput.files.length) {
        state.statusMessage = "请先选择要上传的论文。";
        renderStatus();
        return;
    }

    const formData = new FormData();
    formData.append("file", elements.fileInput.files[0]);
    state.statusMessage = `正在上传 ${elements.fileInput.files[0].name}...`;
    renderStatus();

    try {
        const response = await fetch("/api/documents/upload", {
            method: "POST",
            body: formData
        });
        const payload = await readJsonSafely(response);
        if (!response.ok) {
            throw new Error(payload?.error || `HTTP ${response.status}`);
        }

        state.statusMessage = `上传已接收，文档 ${payload.documentId} 当前状态：${payload.status}。`;
        await refreshDocuments();
        state.selectedDocumentId = Number(payload.documentId);
        await pollDocumentUntilReady(Number(payload.documentId));
        elements.fileInput.value = "";
    } catch (error) {
        state.statusMessage = `上传失败：${error.message}`;
        renderStatus();
    }
}

async function pollDocumentUntilReady(documentId) {
    for (let attempt = 0; attempt < 60; attempt += 1) {
        await delay(1000);
        try {
            const response = await fetch(`/api/documents/${documentId}`);
            const payload = await readJsonSafely(response);
            if (!response.ok) {
                throw new Error(payload?.error || `HTTP ${response.status}`);
            }
            upsertDocument(payload);
            renderAll();

            if (payload.status === "INDEXED") {
                state.statusMessage = `文档 ${documentId} 已索引完成，可以开始提问。`;
                await loadAnalysis(documentId);
                return;
            }

            if (payload.status === "FAILED") {
                state.statusMessage = `文档 ${documentId} 在 ${payload.failureStage || "处理阶段"} 失败。`;
                return;
            }
        } catch (error) {
            state.statusMessage = `轮询文档 ${documentId} 状态失败：${error.message}`;
            return;
        }
    }

    state.statusMessage = `文档 ${documentId} 仍在处理中，你可以稍后继续。`;
}

async function askQuestion() {
    const question = elements.composer.value.trim();
    if (!question) {
        state.statusMessage = "请先输入你的问题。";
        renderStatus();
        return;
    }

    closeStream();

    const session = getActiveSession();
    const now = new Date().toISOString();
    const userMessage = {
        id: nextId("user"),
        role: "user",
        content: question,
        createdAt: now
    };

    const assistantMessage = {
        id: nextId("assistant"),
        role: "assistant",
        content: "",
        answerMode: "等待中",
        createdAt: now,
        citations: [],
        trace: [
            {
                label: "请求已接收",
                detail: state.selectedDocumentId
                        ? `当前将基于文档 ${state.selectedDocumentId} 进行证据约束回答。`
                        : "当前没有固定文档，将只使用现有本地上下文。"
            },
            {
                label: "等待检索事件",
                detail: "当前后端主要返回粗粒度 SSE 事件；更细的检索轨迹会在后端增强后自动显示。"
            }
        ],
        telemetry: {
            status: "connecting",
            answerMode: "等待中",
            latency: "正在建立流式连接...",
            evidenceCount: "0 条引用",
            contextWindow: state.selectedDocumentId ? `文档:${state.selectedDocumentId}` : "仅本地上下文"
        }
    };

    if (session.title === "新研究会话") {
        session.title = question.slice(0, 42);
    }
    session.messages.push(userMessage, assistantMessage);
    session.messageCount = session.messages.length;
    touchSession(session);
    state.focusedMessageId = assistantMessage.id;
    state.statusMessage = "正在生成回答...";
    elements.composer.value = "";
    renderAll();
    scrollConversationToBottom();

    const streamState = {
        answerParts: [],
        citations: [],
        trace: [...assistantMessage.trace],
        telemetry: { ...assistantMessage.telemetry }
    };

    const params = new URLSearchParams({
        sessionKey: session.sessionKey,
        question
    });
    if (state.selectedDocumentId) {
        params.set("documentId", String(state.selectedDocumentId));
    }

    const source = new EventSource(`/api/chat/stream?${params.toString()}`);
    state.activeStream = source;

    source.addEventListener("heartbeat", () => {
        streamState.trace = [
            {
                label: "会话已建立",
                detail: "后端已接受 SSE 请求，并初始化了回答链路。"
            },
            ...streamState.trace
        ].slice(0, 6);
        updateAssistantMessage(assistantMessage.id, {
            trace: streamState.trace
        });
    });

    source.addEventListener("message", (event) => {
        const nextState = applyStreamEvent(streamState, "message", event.data);
        Object.assign(streamState, nextState);
        updateAssistantMessage(assistantMessage.id, {
            content: streamState.answerParts.join(" "),
            telemetry: {
                ...streamState.telemetry,
                latency: "正在接收内容..."
            }
        });
    });

    for (const eventName of ["retrieval-start", "retrieval-step", "telemetry", "error"]) {
        source.addEventListener(eventName, (event) => {
            const parsed = parsePossibleJson(event.data);
            const nextState = applyStreamEvent(streamState, eventName, parsed);
            Object.assign(streamState, nextState);
            updateAssistantMessage(assistantMessage.id, {
                trace: streamState.trace,
                telemetry: normalizeTelemetry(streamState.telemetry)
            });
        });
    }

    source.addEventListener("done", (event) => {
        const payload = parsePossibleJson(event.data) || {};
        const nextState = applyStreamEvent(streamState, "done", payload);
        Object.assign(streamState, nextState);

        updateAssistantMessage(assistantMessage.id, {
            content: payload.answer || streamState.answerParts.join(" "),
            answerMode: payload.answerMode || "未知模式",
            citations: Array.isArray(payload.citations) ? payload.citations : [],
            trace: streamState.trace,
            telemetry: normalizeTelemetry({
                ...streamState.telemetry,
                status: "done",
                answerMode: payload.answerMode || streamState.telemetry.answerMode,
                evidenceCount: `${Array.isArray(payload.citations) ? payload.citations.length : 0} 条引用`,
                latency: "已完成"
            })
        });
        state.statusMessage = `回答已完成，模式：${payload.answerMode || "未知模式"}。`;
        closeStream();
        renderAll();
        scrollConversationToBottom();
    });

    source.onerror = () => {
        updateAssistantMessage(assistantMessage.id, {
            content: assistantMessage.content || "流式回答在完成前中断了，请稍后重试。",
            telemetry: normalizeTelemetry({
                ...streamState.telemetry,
                status: "error",
                latency: "已中断"
            }),
            trace: [
                {
                    label: "流式中断",
                    detail: "浏览器与 SSE 连接断开。后端恢复稳定后，你可以重新提问。"
                },
                ...streamState.trace
            ].slice(0, 8)
        });
        state.statusMessage = "流式回答失败。";
        closeStream();
        renderAll();
    };
}

function renderAll() {
    renderStatus();
    renderSessions();
    renderDocuments();
    renderConversation();
    renderInspector();
    renderHeader();
}

function renderStatus() {
    elements.systemStatus.dataset.state = state.systemState;
    elements.systemStatus.textContent = state.systemState === "ok"
            ? "系统在线"
            : state.systemState === "error"
                    ? "系统异常"
                    : "系统检查中";
    elements.statusBanner.textContent = state.statusMessage;
}

function renderHeader() {
    const session = getActiveSession();
    const selectedDocument = getSelectedDocument();

    elements.activeSessionTitle.textContent = session?.title || "研究工作区";
    elements.activeSessionMeta.textContent = session
            ? `本地已记录 ${session.messages.length} 轮对话，最近更新于 ${formatRelativeTime(session.updatedAt)}`
            : "当前没有激活会话";

    elements.documentChips.innerHTML = "";
    const chips = [];
    if (selectedDocument) {
        chips.push({
            label: selectedDocument.title,
            meta: `${selectedDocument.status} · ${selectedDocument.totalChunks} 个切块`
        });
    } else {
        chips.push({
            label: "尚未固定论文",
            meta: "请先从左侧上传或选择一篇文档"
        });
    }

    chips.push({
        label: elements.deepResearchToggle.checked ? "深度研究已开启" : "深度研究待命中",
        meta: "界面已就绪，后端能力会持续扩展"
    });
    chips.push({
        label: elements.autoVerifyToggle.checked ? "自动校验已排队" : "自动校验已关闭",
        meta: "校验链路接入后会直接在这里生效"
    });

    for (const chip of chips) {
        const element = document.createElement("div");
        element.className = "chip";
        element.innerHTML = `<strong>${escapeHtml(chip.label)}</strong><span>${escapeHtml(chip.meta)}</span>`;
        elements.documentChips.appendChild(element);
    }
}

function renderSessions() {
    elements.sessionList.innerHTML = "";
    for (const session of state.sessions) {
        const button = document.createElement("button");
        button.className = `session-item${session.sessionKey === getActiveSession()?.sessionKey ? " is-active" : ""}`;
        button.dataset.sessionKey = session.sessionKey;
        button.innerHTML = `
            <h3>${escapeHtml(session.title)}</h3>
            <p class="muted">${escapeHtml(session.messages.at(-1)?.content?.slice(0, 88) || "当前文档集合的本地优先研究会话。")}</p>
            <div class="meta-row">
                <span class="meta-token">${session.messageCount} 轮</span>
                <span class="meta-token">${formatRelativeTime(session.updatedAt)}</span>
            </div>
        `;
        elements.sessionList.appendChild(button);
    }
}

function renderDocuments() {
    elements.documentList.innerHTML = "";

    if (state.documents.length === 0) {
        const empty = document.createElement("div");
        empty.className = "empty-state";
        empty.textContent = "当前还没有文档。上传论文后，工作台会开始跟踪索引状态、切块数量和结构化分析。";
        elements.documentList.appendChild(empty);
        return;
    }

    for (const documentRecord of state.documents) {
        const button = document.createElement("button");
        button.className = `document-item${documentRecord.documentId === state.selectedDocumentId ? " is-active" : ""}`;
        button.dataset.documentId = String(documentRecord.documentId);
        button.innerHTML = `
            <h3>${escapeHtml(documentRecord.title)}</h3>
            <p class="muted">${escapeHtml(documentRecord.originalFileName)}</p>
            <div class="meta-row">
                <span class="meta-token" data-tone="${documentRecord.tone}">${documentRecord.status}</span>
                <span class="meta-token">${documentRecord.totalChunks} 个切块</span>
                <span class="meta-token">${documentRecord.totalTokens} 个 token</span>
            </div>
        `;
        elements.documentList.appendChild(button);
    }
}

function renderConversation() {
    const session = getActiveSession();
    const messages = session?.messages || [];
    elements.conversation.innerHTML = "";

    if (messages.length === 0) {
        elements.conversation.innerHTML = `
            <div class="empty-state">
                先在左侧选择一篇论文，然后在下方“对话区”输入问题。这里会保留本地会话历史，方便你连续追问。
            </div>
        `;
        return;
    }

    const timeline = document.createElement("div");
    timeline.className = "timeline";
    for (const message of messages) {
        const article = document.createElement("article");
        article.className = `message message--${message.role}`;
        article.innerHTML = `
            <div class="message-avatar">${message.role === "assistant" ? "AI" : "我"}</div>
            <div class="message-card${message.id === state.focusedMessageId ? " is-selected" : ""}" data-message-id="${message.id}">
                <h3>${message.role === "assistant" ? "证据约束回答" : "用户问题"}</h3>
                <p>${escapeHtml(message.content || (message.role === "assistant" ? "正在等待流式返回内容..." : ""))}</p>
                <div class="message-foot">
                    <span>${escapeHtml(message.answerMode || "本地会话")}</span>
                    <span>${formatRelativeTime(message.createdAt)}</span>
                </div>
            </div>
        `;
        timeline.appendChild(article);
    }
    elements.conversation.appendChild(timeline);
}

function renderInspector() {
    const selectedDocument = getSelectedDocument();
    const analysis = buildAnalysisViewModel(state.analysis, selectedDocument?.title || "当前文档");
    const focusedAssistant = getFocusedAssistantMessage();

    elements.selectedDocumentLabel.textContent = selectedDocument
            ? `${selectedDocument.title} · ${selectedDocument.status}`
            : "尚未选择文档";

    elements.analysisSummary.textContent = analysis.summary;
    elements.analysisAbstract.textContent = analysis.abstractText;
    renderList(elements.analysisMethods, analysis.methods, "待结构化抽取完成后，这里会展示论文方法。");
    renderList(elements.analysisContributions, analysis.contributions, "待后端分析完成后，这里会展示论文贡献。");
    renderInlineChips(elements.analysisKeywords, analysis.keywords);
    renderList(elements.analysisOutline, analysis.outline, "暂时无法提供大纲。");

    if (!focusedAssistant) {
        elements.citationList.innerHTML = `<div class="empty-state">当回答返回引用后，这里会展示证据卡片。</div>`;
        elements.traceList.innerHTML = `<div class="empty-state">这里会显示检索轨迹。当前后端主要返回粗粒度 SSE 事件，后续更丰富的轨迹会自动显示。</div>`;
        elements.telemetryAnswerMode.textContent = "等待中";
        elements.telemetryLatency.textContent = "暂无请求";
        elements.telemetryEvidence.textContent = selectedDocument ? `已索引 ${selectedDocument.totalChunks} 个切块` : "0 条引用";
        elements.telemetryContext.textContent = selectedDocument ? `文档:${selectedDocument.documentId}` : "工作台空闲";
    } else {
        renderCitations(focusedAssistant.citations || []);
        renderTrace(focusedAssistant.trace || []);
        const telemetry = focusedAssistant.telemetry || {};
        elements.telemetryAnswerMode.textContent = telemetry.answerMode || focusedAssistant.answerMode || "未知模式";
        elements.telemetryLatency.textContent = telemetry.latency || "支持流式返回";
        elements.telemetryEvidence.textContent = telemetry.evidenceCount || `${(focusedAssistant.citations || []).length} 条引用`;
        elements.telemetryContext.textContent = telemetry.contextWindow || (selectedDocument ? `文档:${selectedDocument.documentId}` : "仅本地上下文");
    }

    elements.fallbackCapabilities.innerHTML = `
        <li>在完整后端会话接口进一步增强前，会话列表会先保存在浏览器本地。</li>
        <li>当前 SSE 接口已经支持 <code>message</code> 和 <code>done</code> 事件，后续更细的检索与遥测事件已预留自动接入。</li>
        <li>当前工作台一次固定一篇活动文档，因为现阶段 SSE 对话接口仍以单个 <code>documentId</code> 为主。</li>
    `;
}

function renderCitations(citations) {
    elements.citationList.innerHTML = "";
    if (!citations.length) {
        elements.citationList.innerHTML = `<div class="empty-state">当前回答没有返回引用片段。</div>`;
        return;
    }

    for (const citation of citations) {
        const item = document.createElement("article");
        item.className = "citation-item";
        item.innerHTML = `
            <h3>文档 ${citation.documentId} · 切块 ${citation.chunkIndex}</h3>
            <p>${escapeHtml(citation.excerpt || "当前没有返回摘录内容。")}</p>
            <div class="meta-row">
                <span class="meta-token">chunkId ${citation.chunkId}</span>
            </div>
        `;
        elements.citationList.appendChild(item);
    }
}

function renderTrace(trace) {
    elements.traceList.innerHTML = "";
    if (!trace.length) {
        elements.traceList.innerHTML = `<div class="empty-state">当前回答还没有检索轨迹。</div>`;
        return;
    }

    for (const step of trace) {
        const item = document.createElement("article");
        item.className = "trace-item";
        item.innerHTML = `
            <h3>${escapeHtml(step.label || "轨迹步骤")}</h3>
            <p>${escapeHtml(step.detail || "暂无更多细节。")}</p>
        `;
        elements.traceList.appendChild(item);
    }
}

function renderList(container, items, emptyText) {
    container.innerHTML = "";
    if (!items.length) {
        const li = document.createElement("li");
        li.textContent = emptyText;
        container.appendChild(li);
        return;
    }
    for (const item of items) {
        const li = document.createElement("li");
        li.textContent = item;
        container.appendChild(li);
    }
}

function renderInlineChips(container, items) {
    container.innerHTML = "";
    for (const item of items) {
        const chip = document.createElement("span");
        chip.className = "meta-token";
        chip.textContent = item;
        container.appendChild(chip);
    }
}

function normalizeTelemetry(telemetry) {
    return {
        status: telemetry.status || "idle",
        answerMode: telemetry.answerMode || "未知模式",
        latency: telemetry.latency || "支持流式返回",
        evidenceCount: telemetry.evidenceCount || "0 条引用",
        contextWindow: telemetry.contextWindow || (state.selectedDocumentId ? `文档:${state.selectedDocumentId}` : "仅本地上下文")
    };
}

function hydrateSessions() {
    try {
        const rawSessions = JSON.parse(localStorage.getItem(STORAGE_KEYS.sessions) || "[]");
        state.sessions = Array.isArray(rawSessions) ? rawSessions : [];
    } catch (error) {
        state.sessions = [];
    }

    if (state.sessions.length === 0) {
        state.sessions.push(createSession("新研究会话"));
    }

    const preferredSessionKey = localStorage.getItem(STORAGE_KEYS.activeSession);
    const hasPreferred = state.sessions.some((session) => session.sessionKey === preferredSessionKey);
    if (!hasPreferred) {
        localStorage.setItem(STORAGE_KEYS.activeSession, state.sessions[0].sessionKey);
    }

    for (const session of state.sessions) {
        if (!Array.isArray(session.messages)) {
            session.messages = [];
        }
        if (!Array.isArray(session.pinnedDocumentIds)) {
            session.pinnedDocumentIds = [];
        }
    }
}

function persistSessions() {
    localStorage.setItem(STORAGE_KEYS.sessions, JSON.stringify(state.sessions));
}

function createSession(title) {
    const session = createLocalSession(title);
    session.messages = [];
    state.sessions.unshift(session);
    persistSessions();
    return session;
}

function setActiveSession(sessionKey) {
    localStorage.setItem(STORAGE_KEYS.activeSession, sessionKey);
    const session = getActiveSession();
    if (session?.pinnedDocumentIds?.length) {
        state.selectedDocumentId = session.pinnedDocumentIds[0];
        void loadAnalysis(state.selectedDocumentId);
    }
}

function getActiveSession() {
    const activeKey = localStorage.getItem(STORAGE_KEYS.activeSession);
    return state.sessions.find((session) => session.sessionKey === activeKey) || state.sessions[0];
}

function touchSession(session) {
    session.updatedAt = new Date().toISOString();
    session.messageCount = session.messages.length;
    persistSessions();
}

function getSelectedDocument() {
    return state.documents.find((item) => item.documentId === state.selectedDocumentId) || null;
}

function syncSelectedDocument() {
    if (!state.documents.length) {
        state.selectedDocumentId = null;
        return;
    }

    const stillExists = state.documents.some((item) => item.documentId === state.selectedDocumentId);
    if (stillExists) {
        return;
    }

    const activeSession = getActiveSession();
    const pinnedId = activeSession?.pinnedDocumentIds?.[0];
    const pinnedExists = state.documents.some((item) => item.documentId === pinnedId);
    state.selectedDocumentId = pinnedExists
            ? pinnedId
            : state.documents.find((item) => item.status === "INDEXED")?.documentId || state.documents[0].documentId;
}

function getFocusedAssistantMessage() {
    const session = getActiveSession();
    const assistantMessages = (session?.messages || []).filter((message) => message.role === "assistant");
    return assistantMessages.find((message) => message.id === state.focusedMessageId) || assistantMessages.at(-1) || null;
}

function updateAssistantMessage(messageId, patch) {
    const session = getActiveSession();
    const message = session?.messages.find((item) => item.id === messageId);
    if (!message) {
        return;
    }
    Object.assign(message, patch);
    touchSession(session);
    renderConversation();
    renderInspector();
}

function upsertDocument(rawDocument) {
    const normalized = normalizeDocument(rawDocument);
    const existingIndex = state.documents.findIndex((item) => item.documentId === normalized.documentId);
    if (existingIndex >= 0) {
        state.documents.splice(existingIndex, 1, {
            ...state.documents[existingIndex],
            ...normalized
        });
    } else {
        state.documents.unshift(normalized);
    }
}

function closeStream() {
    if (state.activeStream) {
        state.activeStream.close();
        state.activeStream = null;
    }
}

function scrollConversationToBottom() {
    elements.conversation.scrollTop = elements.conversation.scrollHeight;
}

function nextId(prefix) {
    if (window.crypto?.randomUUID) {
        return `${prefix}-${window.crypto.randomUUID()}`;
    }
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function parsePossibleJson(raw) {
    if (typeof raw !== "string") {
        return raw;
    }
    try {
        return JSON.parse(raw);
    } catch (error) {
        return raw;
    }
}

async function readJsonSafely(response) {
    const text = await response.text();
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch (error) {
        return { raw: text };
    }
}

function escapeHtml(value) {
    return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
}

function delay(milliseconds) {
    return new Promise((resolve) => {
        window.setTimeout(resolve, milliseconds);
    });
}
