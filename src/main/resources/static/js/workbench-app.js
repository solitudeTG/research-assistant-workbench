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
    statusMessage: "Booting workspace...",
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
        const session = createSession("New research session");
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
        state.statusMessage = `Backend ping failed: ${error.message}`;
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
        elements.documentCount.textContent = `${state.documents.length} indexed workspace assets`;
        syncSelectedDocument();
        if (state.selectedDocumentId) {
            await loadAnalysis(state.selectedDocumentId);
        }
        if (state.documents.length === 0) {
            state.statusMessage = "Workspace is empty. Upload a paper to begin.";
        }
    } catch (error) {
        state.documents = [];
        state.analysis = null;
        elements.documentCount.textContent = "Document library unavailable";
        state.statusMessage = `Document workspace unavailable: ${error.message}`;
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
        state.statusMessage = `Structured analysis unavailable for document ${documentId}: ${error.message}`;
    }
    renderInspector();
}

async function uploadDocument() {
    if (!elements.fileInput.files.length) {
        state.statusMessage = "Choose a paper before uploading.";
        renderStatus();
        return;
    }

    const formData = new FormData();
    formData.append("file", elements.fileInput.files[0]);
    state.statusMessage = `Uploading ${elements.fileInput.files[0].name}...`;
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

        state.statusMessage = `Upload accepted. Document ${payload.documentId} entered ${payload.status}.`;
        await refreshDocuments();
        state.selectedDocumentId = Number(payload.documentId);
        await pollDocumentUntilReady(Number(payload.documentId));
        elements.fileInput.value = "";
    } catch (error) {
        state.statusMessage = `Upload failed: ${error.message}`;
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
                state.statusMessage = `Document ${documentId} is indexed and ready.`;
                await loadAnalysis(documentId);
                return;
            }

            if (payload.status === "FAILED") {
                state.statusMessage = `Document ${documentId} failed during ${payload.failureStage || "processing"}.`;
                return;
            }
        } catch (error) {
            state.statusMessage = `Status polling failed for document ${documentId}: ${error.message}`;
            return;
        }
    }

    state.statusMessage = `Document ${documentId} is still processing. You can continue later.`;
}

async function askQuestion() {
    const question = elements.composer.value.trim();
    if (!question) {
        state.statusMessage = "Write a research question first.";
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
        answerMode: "PENDING",
        createdAt: now,
        citations: [],
        trace: [
            {
                label: "Request accepted",
                detail: state.selectedDocumentId
                        ? `Grounding against document ${state.selectedDocumentId}.`
                        : "No document pinned, running with available local context only."
            },
            {
                label: "Awaiting retrieval events",
                detail: "Current backend emits coarse SSE tokens only. Fine-grained trace steps will appear automatically when available."
            }
        ],
        telemetry: {
            status: "connecting",
            answerMode: "PENDING",
            latency: "Streaming...",
            evidenceCount: "0 citations",
            contextWindow: state.selectedDocumentId ? `doc:${state.selectedDocumentId}` : "local-only"
        }
    };

    if (session.title === "New research session") {
        session.title = question.slice(0, 42);
    }
    session.messages.push(userMessage, assistantMessage);
    session.messageCount = session.messages.length;
    touchSession(session);
    state.focusedMessageId = assistantMessage.id;
    state.statusMessage = "Streaming answer...";
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
                label: "Session hydrated",
                detail: "Backend accepted the SSE request and initialized the answer pipeline."
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
                latency: "Receiving tokens..."
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
            answerMode: payload.answerMode || "UNKNOWN",
            citations: Array.isArray(payload.citations) ? payload.citations : [],
            trace: streamState.trace,
            telemetry: normalizeTelemetry({
                ...streamState.telemetry,
                status: "done",
                answerMode: payload.answerMode || streamState.telemetry.answerMode,
                evidenceCount: `${Array.isArray(payload.citations) ? payload.citations.length : 0} citations`,
                latency: "Completed"
            })
        });
        state.statusMessage = `Completed with ${payload.answerMode || "UNKNOWN"}.`;
        closeStream();
        renderAll();
        scrollConversationToBottom();
    });

    source.onerror = () => {
        updateAssistantMessage(assistantMessage.id, {
            content: assistantMessage.content || "Streaming failed before a complete answer was received.",
            telemetry: normalizeTelemetry({
                ...streamState.telemetry,
                status: "error",
                latency: "Interrupted"
            }),
            trace: [
                {
                    label: "Stream interruption",
                    detail: "The browser lost the SSE connection. You can retry the same question once the backend is healthy."
                },
                ...streamState.trace
            ].slice(0, 8)
        });
        state.statusMessage = "Streaming failed.";
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
            ? "system-online"
            : state.systemState === "error"
                    ? "system-degraded"
                    : "system-checking";
    elements.statusBanner.textContent = state.statusMessage;
}

function renderHeader() {
    const session = getActiveSession();
    const selectedDocument = getSelectedDocument();

    elements.activeSessionTitle.textContent = session?.title || "Research workspace";
    elements.activeSessionMeta.textContent = session
            ? `${session.messages.length} turns recorded locally · updated ${formatRelativeTime(session.updatedAt)}`
            : "No active session";

    elements.documentChips.innerHTML = "";
    const chips = [];
    if (selectedDocument) {
        chips.push({
            label: selectedDocument.title,
            meta: `${selectedDocument.status} · ${selectedDocument.totalChunks} chunks`
        });
    } else {
        chips.push({
            label: "No pinned paper",
            meta: "Upload or select a document from the left rail"
        });
    }

    chips.push({
        label: elements.deepResearchToggle.checked ? "Deep research enabled" : "Deep research standby",
        meta: "UI-ready · backend route expands when available"
    });
    chips.push({
        label: elements.autoVerifyToggle.checked ? "Auto verify queued" : "Auto verify off",
        meta: "Placeholder until verification pipeline lands"
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
            <p class="muted">${escapeHtml(session.messages.at(-1)?.content?.slice(0, 88) || "Local-first session workspace for current document set.")}</p>
            <div class="meta-row">
                <span class="meta-token">${session.messageCount} turns</span>
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
        empty.textContent = "No documents are available yet. Upload a paper and the workbench will start tracking indexing, chunk counts, and structured analysis.";
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
                <span class="meta-token">${documentRecord.totalChunks} chunks</span>
                <span class="meta-token">${documentRecord.totalTokens} tokens</span>
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
                Ask a grounded question after selecting a paper. This workbench keeps local session history even before the backend exposes full session APIs.
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
            <div class="message-avatar">${message.role === "assistant" ? "AI" : "YOU"}</div>
            <div class="message-card${message.id === state.focusedMessageId ? " is-selected" : ""}" data-message-id="${message.id}">
                <h3>${message.role === "assistant" ? "Grounded answer" : "Research prompt"}</h3>
                <p>${escapeHtml(message.content || (message.role === "assistant" ? "Waiting for streamed tokens..." : ""))}</p>
                <div class="message-foot">
                    <span>${escapeHtml(message.answerMode || "LOCAL_SESSION")}</span>
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
    const analysis = buildAnalysisViewModel(state.analysis, selectedDocument?.title || "Current document");
    const focusedAssistant = getFocusedAssistantMessage();

    elements.selectedDocumentLabel.textContent = selectedDocument
            ? `${selectedDocument.title} · ${selectedDocument.status}`
            : "No document selected";

    elements.analysisSummary.textContent = analysis.summary;
    elements.analysisAbstract.textContent = analysis.abstractText;
    renderList(elements.analysisMethods, analysis.methods, "Methods will appear once structured extraction is available.");
    renderList(elements.analysisContributions, analysis.contributions, "Contributions will appear once the backend analysis is ready.");
    renderInlineChips(elements.analysisKeywords, analysis.keywords);
    renderList(elements.analysisOutline, analysis.outline, "Outline unavailable.");

    if (!focusedAssistant) {
        elements.citationList.innerHTML = `<div class="empty-state">Evidence cards will appear after the first assistant answer with citations.</div>`;
        elements.traceList.innerHTML = `<div class="empty-state">Retrieval trace will appear here. The current backend emits coarse SSE events; richer trace events are handled automatically once added.</div>`;
        elements.telemetryAnswerMode.textContent = "PENDING";
        elements.telemetryLatency.textContent = "No request";
        elements.telemetryEvidence.textContent = selectedDocument ? `${selectedDocument.totalChunks} chunks indexed` : "0 citations";
        elements.telemetryContext.textContent = selectedDocument ? `doc:${selectedDocument.documentId}` : "workspace-idle";
    } else {
        renderCitations(focusedAssistant.citations || []);
        renderTrace(focusedAssistant.trace || []);
        const telemetry = focusedAssistant.telemetry || {};
        elements.telemetryAnswerMode.textContent = telemetry.answerMode || focusedAssistant.answerMode || "UNKNOWN";
        elements.telemetryLatency.textContent = telemetry.latency || "Streaming-compatible";
        elements.telemetryEvidence.textContent = telemetry.evidenceCount || `${(focusedAssistant.citations || []).length} citations`;
        elements.telemetryContext.textContent = telemetry.contextWindow || (selectedDocument ? `doc:${selectedDocument.documentId}` : "local-only");
    }

    elements.fallbackCapabilities.innerHTML = `
        <li>Session lists are persisted locally in the browser until backend session APIs arrive.</li>
        <li>Current SSE endpoint streams <code>message</code> and <code>done</code>; future retrieval and telemetry events are already wired for auto-upgrade.</li>
        <li>The workbench pins one active document because the current backend accepts a single <code>documentId</code> for SSE chat.</li>
    `;
}

function renderCitations(citations) {
    elements.citationList.innerHTML = "";
    if (!citations.length) {
        elements.citationList.innerHTML = `<div class="empty-state">No citations were returned for the selected answer.</div>`;
        return;
    }

    for (const citation of citations) {
        const item = document.createElement("article");
        item.className = "citation-item";
        item.innerHTML = `
            <h3>Doc ${citation.documentId} · chunk ${citation.chunkIndex}</h3>
            <p>${escapeHtml(citation.excerpt || "No excerpt returned.")}</p>
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
        elements.traceList.innerHTML = `<div class="empty-state">Retrieval trace is empty for this answer.</div>`;
        return;
    }

    for (const step of trace) {
        const item = document.createElement("article");
        item.className = "trace-item";
        item.innerHTML = `
            <h3>${escapeHtml(step.label || "Trace step")}</h3>
            <p>${escapeHtml(step.detail || "No additional detail.")}</p>
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
        answerMode: telemetry.answerMode || "UNKNOWN",
        latency: telemetry.latency || "Streaming-compatible",
        evidenceCount: telemetry.evidenceCount || "0 citations",
        contextWindow: telemetry.contextWindow || (state.selectedDocumentId ? `doc:${state.selectedDocumentId}` : "local-only")
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
        state.sessions.push(createSession("New research session"));
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
