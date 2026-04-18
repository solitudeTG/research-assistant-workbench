const STATUS_TONE = {
    UPLOADED: "queued",
    PARSING: "processing",
    INDEXING: "processing",
    INDEXED: "ready",
    FAILED: "failed",
    UNKNOWN: "idle"
};

export function normalizeDocument(rawDocument = {}) {
    const fallbackTitle = rawDocument.title || rawDocument.originalFileName || `Document ${rawDocument.documentId ?? "?"}`;
    const status = String(rawDocument.status || "UNKNOWN").toUpperCase();
    return {
        documentId: Number(rawDocument.documentId ?? 0),
        title: fallbackTitle,
        originalFileName: rawDocument.originalFileName || fallbackTitle,
        status,
        totalChunks: Number(rawDocument.totalChunks ?? 0),
        totalTokens: Number(rawDocument.totalTokens ?? 0),
        createdAt: rawDocument.createdAt || "",
        updatedAt: rawDocument.updatedAt || "",
        tone: STATUS_TONE[status] || "idle"
    };
}

export function buildAnalysisViewModel(analysis, documentTitle = "Current document") {
    if (!analysis) {
        return {
            summary: "Document analysis will appear here after indexing completes.",
            abstractText: "No abstract has been extracted yet.",
            methods: [],
            contributions: [],
            keywords: [documentTitle],
            outline: ["Awaiting structured outline"]
        };
    }

    return {
        summary: analysis.summary || "Structured summary is unavailable for this document.",
        abstractText: analysis.abstractText || "No abstract has been extracted yet.",
        methods: Array.isArray(analysis.methods) ? analysis.methods : [],
        contributions: Array.isArray(analysis.contributions) ? analysis.contributions : [],
        keywords: Array.isArray(analysis.keywords) && analysis.keywords.length > 0 ? analysis.keywords : [documentTitle],
        outline: Array.isArray(analysis.outline) && analysis.outline.length > 0 ? analysis.outline : ["Outline unavailable"]
    };
}

export function applyStreamEvent(state, eventName, data) {
    const next = {
        answerParts: [...(state.answerParts || [])],
        citations: [...(state.citations || [])],
        trace: [...(state.trace || [])],
        telemetry: { ...(state.telemetry || {}) }
    };

    if (eventName === "message") {
        next.answerParts.push(String(data));
        next.telemetry.status = "streaming";
        return next;
    }

    if (eventName === "retrieval-start") {
        next.trace.push({
            label: data?.label || "Retrieval started",
            detail: data?.detail || "Preparing evidence candidates"
        });
        next.telemetry.status = "retrieving";
        return next;
    }

    if (eventName === "retrieval-step") {
        next.trace.push({
            label: data?.label || "Retrieval step",
            detail: data?.detail || ""
        });
        return next;
    }

    if (eventName === "telemetry") {
        next.telemetry = {
            ...next.telemetry,
            ...(data || {})
        };
        return next;
    }

    if (eventName === "done") {
        next.citations = Array.isArray(data?.citations) ? data.citations : [];
        next.telemetry.status = "done";
        next.telemetry.answerMode = data?.answerMode || next.telemetry.answerMode || "UNKNOWN";
        return next;
    }

    if (eventName === "error") {
        next.telemetry.status = "error";
        next.telemetry.error = data?.message || String(data || "Unknown stream error");
        return next;
    }

    return next;
}

export function createLocalSession(name) {
    const now = new Date().toISOString();
    return {
        sessionKey: `local-${Math.random().toString(36).slice(2, 10)}`,
        title: name || "New research session",
        createdAt: now,
        updatedAt: now,
        messageCount: 0,
        pinnedDocumentIds: []
    };
}

export function formatRelativeTime(value) {
    if (!value) {
        return "just now";
    }
    const milliseconds = Date.now() - new Date(value).getTime();
    const minutes = Math.max(0, Math.round(milliseconds / 60000));
    if (minutes < 1) {
        return "just now";
    }
    if (minutes < 60) {
        return `${minutes} min ago`;
    }
    const hours = Math.round(minutes / 60);
    if (hours < 24) {
        return `${hours} hr ago`;
    }
    const days = Math.round(hours / 24);
    return `${days} day${days > 1 ? "s" : ""} ago`;
}
