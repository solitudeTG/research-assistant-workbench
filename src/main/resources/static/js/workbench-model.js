const STATUS_TONE = {
    UPLOADED: "queued",
    PARSING: "processing",
    INDEXING: "processing",
    INDEXED: "ready",
    FAILED: "failed",
    UNKNOWN: "idle"
};

export function normalizeDocument(rawDocument = {}) {
    const fallbackTitle = rawDocument.title || rawDocument.originalFileName || `文档 ${rawDocument.documentId ?? "?"}`;
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

export function buildAnalysisViewModel(analysis, documentTitle = "当前文档") {
    if (!analysis) {
        return {
            summary: "索引完成后，这里会展示论文的结构化总结。",
            abstractText: "暂未抽取到摘要内容。",
            methods: [],
            contributions: [],
            keywords: [documentTitle],
            outline: ["正在等待结构化大纲"]
        };
    }

    return {
        summary: analysis.summary || "当前文档暂未生成结构化总结。",
        abstractText: analysis.abstractText || "暂未抽取到摘要内容。",
        methods: Array.isArray(analysis.methods) ? analysis.methods : [],
        contributions: Array.isArray(analysis.contributions) ? analysis.contributions : [],
        keywords: Array.isArray(analysis.keywords) && analysis.keywords.length > 0 ? analysis.keywords : [documentTitle],
        outline: Array.isArray(analysis.outline) && analysis.outline.length > 0 ? analysis.outline : ["暂无法提供大纲"]
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
            label: data?.label || "开始检索",
            detail: data?.detail || "正在准备候选证据片段"
        });
        next.telemetry.status = "retrieving";
        return next;
    }

    if (eventName === "retrieval-step") {
        next.trace.push({
            label: data?.label || "检索步骤",
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
        next.telemetry.error = data?.message || String(data || "未知流式错误");
        return next;
    }

    return next;
}

export function createLocalSession(name) {
    const now = new Date().toISOString();
    return {
        sessionKey: `local-${Math.random().toString(36).slice(2, 10)}`,
        title: name || "新研究会话",
        createdAt: now,
        updatedAt: now,
        messageCount: 0,
        pinnedDocumentIds: []
    };
}

export function formatRelativeTime(value) {
    if (!value) {
        return "刚刚";
    }
    const milliseconds = Date.now() - new Date(value).getTime();
    const minutes = Math.max(0, Math.round(milliseconds / 60000));
    if (minutes < 1) {
        return "刚刚";
    }
    if (minutes < 60) {
        return `${minutes} 分钟前`;
    }
    const hours = Math.round(minutes / 60);
    if (hours < 24) {
        return `${hours} 小时前`;
    }
    const days = Math.round(hours / 24);
    return `${days} 天前`;
}
