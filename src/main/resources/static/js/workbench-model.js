const STATUS_TONE = {
    UPLOADED: "queued",
    PARSING: "processing",
    INDEXING: "processing",
    INDEXED: "ready",
    FAILED: "failed",
    UNKNOWN: "idle"
};

const SIDEBAR_VIEWS = new Set(["knowledge-board", "evidence-sources", "candidate-confirmation"]);
const WORKSPACES = new Set(["session", "sources", "knowledge", "project", "observability"]);
const KNOWLEDGE_SECTIONS = [
    { section: "current_candidates", title: "本轮候选" },
    { section: "core_concept", title: "核心概念" },
    { section: "method_route", title: "方法路线" },
    { section: "confirmed_finding", title: "已确认结论" },
    { section: "open_question", title: "待验证问题" }
];

export function selectSession(state, sessionId) {
    return {
        ...state,
        activeSessionId: sessionId,
        activeAnswerContext: null,
        currentAnswer: {
            ...(state.currentAnswer || {}),
            text: state.activeSessionId === sessionId ? state.currentAnswer?.text || "" : "",
            status: "idle"
        }
    };
}

export function selectSidebarView(state, view) {
    const nextView = SIDEBAR_VIEWS.has(view) ? view : "knowledge-board";
    return {
        ...state,
        selectedSidebarView: nextView
    };
}

export function selectWorkspace(state, workspace) {
    const nextWorkspace = WORKSPACES.has(workspace) ? workspace : "session";
    return {
        ...state,
        activeWorkspace: nextWorkspace
    };
}

export function getWorkspaceVisibility(state) {
    const activeWorkspace = WORKSPACES.has(state?.activeWorkspace) ? state.activeWorkspace : "session";
    return {
        session: activeWorkspace === "session",
        sources: activeWorkspace === "sources",
        knowledge: activeWorkspace === "knowledge",
        project: activeWorkspace === "project",
        observability: activeWorkspace === "observability",
        showChatComposer: activeWorkspace === "session",
        showSessionInspector: activeWorkspace === "session"
    };
}

export function requireProjectChatContext(state) {
    if (!state || state.sampleMode || !state.activeProjectId || !state.activeSessionId) {
        throw new Error("Project session context is required for workbench chat.");
    }
    return {
        projectId: String(state.activeProjectId),
        sessionId: String(state.activeSessionId)
    };
}

export function buildProjectMessageUrl(context) {
    return `/api/projects/${encodeURIComponent(context.projectId)}/sessions/${encodeURIComponent(context.sessionId)}/messages`;
}

export function buildProjectSessionMessagesUrl(context) {
    return `/api/projects/${encodeURIComponent(context.projectId)}/sessions/${encodeURIComponent(context.sessionId)}/messages`;
}

export function buildProjectSessionRenameUrl(context) {
    return `/api/projects/${encodeURIComponent(context.projectId)}/sessions/${encodeURIComponent(context.sessionId)}`;
}

export function buildProjectSessionDeleteUrl(context) {
    return `/api/projects/${encodeURIComponent(context.projectId)}/sessions/${encodeURIComponent(context.sessionId)}`;
}

export function renameSessionTitle(state, sessionId, title) {
    const normalizedTitle = String(title || "").trim();
    if (!normalizedTitle) {
        return state;
    }
    return {
        ...state,
        sessions: (state.sessions || []).map((session) => {
            if (session.id !== sessionId) {
                return session;
            }
            return {
                ...session,
                title: normalizedTitle
            };
        })
    };
}

export function deleteSessionFromState(state, sessionId) {
    const sessions = (state.sessions || []).filter((session) => session.id !== sessionId);
    const deletedActive = state.activeSessionId === sessionId;
    const activeSessionId = deletedActive ? sessions[0]?.id || null : state.activeSessionId || null;
    return {
        ...state,
        sessions,
        activeSessionId,
        activeAnswerContext: deletedActive ? null : state.activeAnswerContext || null,
        currentAnswer: deletedActive
                ? { answerId: null, text: "", status: "idle", evidenceState: null, outputMode: null, citationCount: 0 }
                : state.currentAnswer,
        messages: deletedActive ? [] : state.messages || [],
        evidenceSources: deletedActive ? [] : state.evidenceSources || [],
        candidates: deletedActive
                ? (state.candidates || []).filter((candidate) => candidate.sessionId !== sessionId)
                : state.candidates || [],
        agentTraces: deletedActive ? {} : state.agentTraces || {},
        processedEventIds: deletedActive ? [] : state.processedEventIds || []
    };
}

export function calculateRestoredScrollTop(previous, nextScrollHeight) {
    const scrollTop = Number(previous?.scrollTop ?? 0);
    const scrollHeight = Number(previous?.scrollHeight ?? 0);
    const clientHeight = Number(previous?.clientHeight ?? 0);
    const nextHeight = Number(nextScrollHeight ?? 0);
    const distanceFromBottom = Math.max(0, scrollHeight - scrollTop - clientHeight);
    return Math.max(0, nextHeight - clientHeight - distanceFromBottom);
}

export function normalizeProjectMessage(raw) {
    const id = raw?.id ?? raw?.messageId ?? "";
    const role = String(raw?.role || "assistant").toLowerCase();
    return {
        id: String(id),
        sessionId: raw?.sessionId == null ? null : String(raw.sessionId),
        role: role === "user" ? "user" : "assistant",
        content: String(raw?.content || raw?.text || ""),
        answerMode: raw?.answerMode || null,
        createdAt: raw?.createdAt || raw?.updatedAt || ""
    };
}

export function startEditingCandidate(state, candidateId) {
    const exists = (state.candidates || []).some((candidate) => candidate.id === candidateId);
    return {
        ...state,
        editingCandidateId: exists ? candidateId : state.editingCandidateId || null
    };
}

export function applyCandidateAction(state, candidateId, action) {
    const statusByAction = {
        accept: "accepted",
        "edit-and-accept": "edited_accepted",
        "mark-unverified": "marked_unverified",
        ignore: "ignored",
        cancel: "pending"
    };
    const nextStatus = statusByAction[action] || action;
    return {
        ...state,
        editingCandidateId: state.editingCandidateId === candidateId ? null : state.editingCandidateId || null,
        candidates: (state.candidates || []).map((candidate) => {
            if (candidate.id !== candidateId) {
                return candidate;
            }
            return {
                ...candidate,
                status: nextStatus
            };
        })
    };
}

export function applySseEvent(state, event) {
    if (!event) {
        return state;
    }

    const eventId = event.eventId || event.id || null;
    const processedEventIds = Array.isArray(state.processedEventIds) ? state.processedEventIds : [];
    if (eventId && processedEventIds.includes(eventId)) {
        return state;
    }

    const next = {
        ...state,
        processedEventIds: eventId ? [...processedEventIds, eventId] : processedEventIds
    };
    const eventType = normalizeEventType(event.eventType || event.type || event.eventName);
    const payload = event.payload && typeof event.payload === "object" ? event.payload : {};
    const traced = applyAgentTraceEvent(next, event, eventType, payload);

    if (eventType === "source.status.changed") {
        return applySourceStatusChanged(traced, event, payload);
    }

    if (eventType === "answer.delta") {
        return applyAnswerDelta(traced, event, traceDataOf(payload));
    }

    if (eventType === "answer.completed") {
        return applyAnswerCompleted(traced, event, payload);
    }

    if (eventType === "evidence.evaluated") {
        return applyEvidenceEvaluated(traced, event, traceDataOf(payload));
    }

    if (eventType === "candidate.created") {
        return applyCandidateCreated(traced, event, payload);
    }

    if (eventType === "knowledge.entry.created") {
        return applyKnowledgeEntryCreated(traced, event, payload);
    }

    return traced;
}

export function createWorkbenchState(seed = {}) {
    return {
        activeWorkspace: WORKSPACES.has(seed.activeWorkspace) ? seed.activeWorkspace : "session",
        activeProjectId: seed.activeProjectId || null,
        activeSessionId: seed.activeSessionId || null,
        activeAnswerContext: seed.activeAnswerContext || null,
        selectedSidebarView: seed.selectedSidebarView || "knowledge-board",
        sources: Array.isArray(seed.sources) ? seed.sources : [],
        sessions: Array.isArray(seed.sessions) ? seed.sessions : [],
        messages: Array.isArray(seed.messages) ? seed.messages : [],
        currentAnswer: seed.currentAnswer || {
            answerId: null,
            text: "",
            status: "idle",
            evidenceState: null,
            outputMode: null,
            citationCount: 0
        },
        evidenceSources: Array.isArray(seed.evidenceSources) ? seed.evidenceSources : [],
        candidates: Array.isArray(seed.candidates) ? seed.candidates : [],
        editingCandidateId: seed.editingCandidateId || null,
        knowledgeBoard: normalizeKnowledgeBoard(seed.knowledgeBoard),
        agentTraces: seed.agentTraces && typeof seed.agentTraces === "object" ? seed.agentTraces : {},
        processedEventIds: Array.isArray(seed.processedEventIds) ? seed.processedEventIds : []
    };
}

function applyAgentTraceEvent(state, event, eventType, payload) {
    const runId = String(event.runId || payload.runId || payload.data?.runId || "");
    if (!runId) {
        return state;
    }

    const data = traceDataOf(payload);
    const traceEntry = {
        ...data,
        eventId: event.eventId || event.id || null,
        eventType,
        runId,
        answerId: event.answerId || data.answerId || payload.answerId || null,
        sequence: event.sequence ?? data.sequence ?? payload.sequence ?? null,
        createdAt: event.createdAt || data.createdAt || payload.createdAt || ""
    };
    const trace = ensureAgentTrace(state, runId);

    if (eventType === "tool.called" || eventType === "tool.completed" || eventType === "tool.failed") {
        trace.tools.push(traceEntry);
        trace.timeline.push(traceEntry);
        trace.summary.toolCount = trace.tools.length;
    } else if (eventType === "retrieval.hit") {
        trace.retrievalHits.push(traceEntry);
        trace.summary.evidenceCount = trace.retrievalHits.length;
    } else if (eventType === "memory.hit") {
        trace.memoryHits.push(traceEntry);
        trace.summary.memoryCount = trace.memoryHits.length;
    } else if (eventType === "memory.completed") {
        trace.timeline.push(traceEntry);
        trace.memorySummary = traceEntry;
        trace.summary.memoryCount = Math.max(trace.memoryHits.length, Number(data.hitCount ?? 0));
    } else if (eventType === "evidence.evaluated" || eventType === "evidence.gap.detected") {
        trace.evidenceEvents.push(traceEntry);
        trace.summary.weakClaims += Number(data.weakClaims ?? 0);
        trace.summary.requiresConfirmation = trace.summary.requiresConfirmation || Boolean(data.requiresUserConfirmation);
    } else if (eventType === "answer.delta") {
        trace.answerDeltas.push(traceEntry);
    } else if (eventType === "agent.plan.created") {
        applyPlanTrace(trace, traceEntry, data);
    } else if (eventType === "agent.step.started"
            || eventType === "agent.step.completed"
            || eventType === "agent.step.failed") {
        applyAgentStepTrace(trace, traceEntry, eventType, payload, data);
    } else {
        return state;
    }

    return {
        ...state,
        agentTraces: {
            ...(state.agentTraces || {}),
            [runId]: trace
        }
    };
}

function ensureAgentTrace(state, runId) {
    const existing = state.agentTraces?.[runId] || {};
    const existingSummary = existing.summary || {};
    return {
        ...existing,
        tools: Array.isArray(existing.tools) ? [...existing.tools] : [],
        timeline: Array.isArray(existing.timeline) ? [...existing.timeline] : [],
        retrievalHits: Array.isArray(existing.retrievalHits) ? [...existing.retrievalHits] : [],
        evidenceEvents: Array.isArray(existing.evidenceEvents) ? [...existing.evidenceEvents] : [],
        memoryHits: Array.isArray(existing.memoryHits) ? [...existing.memoryHits] : [],
        answerDeltas: Array.isArray(existing.answerDeltas) ? [...existing.answerDeltas] : [],
        subagents: cloneSubagents(existing.subagents),
        plan: existing.plan ? { ...existing.plan, steps: Array.isArray(existing.plan.steps) ? [...existing.plan.steps] : [] } : null,
        mode: existing.mode || null,
        modeReason: existing.modeReason || "",
        modeSelection: existing.modeSelection ? { ...existing.modeSelection } : null,
        audit: existing.audit ? { ...existing.audit, counts: { ...(existing.audit.counts || {}) } } : null,
        document: existing.document ? { ...existing.document } : null,
        summary: {
            toolCount: Number(existingSummary.toolCount ?? 0),
            evidenceCount: Number(existingSummary.evidenceCount ?? 0),
            memoryCount: Number(existingSummary.memoryCount ?? 0),
            weakClaims: Number(existingSummary.weakClaims ?? 0),
            requiresConfirmation: Boolean(existingSummary.requiresConfirmation),
            execution: existingSummary.execution || null,
            mode: existingSummary.mode || existing.mode || null,
            auditVerdict: existingSummary.auditVerdict || existing.audit?.verdict || null,
            recommendedAnswerMode: existingSummary.recommendedAnswerMode
                    || existing.audit?.recommendedAnswerMode
                    || null,
            documentFormat: existingSummary.documentFormat || existing.document?.format || null,
            documentTitle: existingSummary.documentTitle || existing.document?.title || null,
            documentSectionCount: Number(existingSummary.documentSectionCount ?? existing.document?.sectionCount ?? 0)
        }
    };
}

function cloneSubagents(subagents) {
    if (!subagents || typeof subagents !== "object") {
        return {};
    }
    return Object.fromEntries(Object.entries(subagents).map(([role, subagent]) => [
        role,
        {
            ...subagent,
            timeline: Array.isArray(subagent.timeline) ? [...subagent.timeline] : [],
            latestData: subagent.latestData && typeof subagent.latestData === "object" ? { ...subagent.latestData } : {}
        }
    ]));
}

function applyPlanTrace(trace, traceEntry, data) {
    const plan = {
        eventId: traceEntry.eventId,
        mode: data.mode || trace.mode || null,
        summary: data.summary || "",
        execution: data.execution || "serial",
        steps: Array.isArray(data.steps) ? data.steps.map((step) => ({ ...step })) : []
    };
    trace.plan = plan;
    if (plan.mode) {
        trace.mode = plan.mode;
        trace.summary.mode = plan.mode;
    }
    trace.summary.execution = plan.execution;
}

function applyAgentStepTrace(trace, traceEntry, eventType, payload, data) {
    const step = payload.step && typeof payload.step === "object" ? payload.step : {};
    if (step.stepId === "mode-selection" || data.mode) {
        trace.mode = data.mode || trace.mode || null;
        trace.modeReason = data.reason || trace.modeReason || "";
        trace.modeSelection = {
            mode: trace.mode,
            reason: trace.modeReason
        };
        trace.summary.mode = trace.mode;
        return;
    }

    const actor = payload.actor && typeof payload.actor === "object" ? payload.actor : {};
    const actorRole = normalizeAgentRole(actor.agentRole || actor.role || data.actorRole || data.agentRole);
    if (!actorRole) {
        return;
    }

    const status = agentStepStatus(eventType, step.status);
    const agentEntry = {
        ...traceEntry,
        actorRole,
        actorDisplayName: actor.displayName || data.actorDisplayName || actorRole,
        stepId: step.stepId || data.stepId || traceEntry.stepId || null,
        parentStepId: step.parentStepId || data.parentStepId || null,
        label: step.label || data.label || "",
        status
    };
    trace.timeline.push(agentEntry);

    const existing = trace.subagents[actorRole] || {
        actorRole,
        displayName: agentEntry.actorDisplayName,
        status: "idle",
        timeline: [],
        latestData: {}
    };
    trace.subagents[actorRole] = {
        ...existing,
        displayName: agentEntry.actorDisplayName || existing.displayName,
        status,
        stepId: agentEntry.stepId || existing.stepId || null,
        latestData: { ...(existing.latestData || {}), ...data },
        timeline: [...(existing.timeline || []), agentEntry]
    };

    if (actorRole === "evidence_audit_agent" && status === "completed") {
        trace.audit = auditSummary(data);
        trace.summary.auditVerdict = trace.audit.verdict || null;
        trace.summary.recommendedAnswerMode = trace.audit.recommendedAnswerMode || null;
    }
    if (actorRole === "document_composer_agent" && status === "completed") {
        trace.document = documentSummary(data);
        trace.summary.documentFormat = trace.document.format || null;
        trace.summary.documentTitle = trace.document.title || null;
        trace.summary.documentSectionCount = Number(trace.document.sectionCount ?? 0);
    }
}

function normalizeAgentRole(role) {
    const normalized = String(role || "").trim();
    if (!normalized || normalized === "supervisor") {
        return "";
    }
    return normalized;
}

function agentStepStatus(eventType, stepStatus) {
    if (eventType === "agent.step.failed") {
        return "failed";
    }
    if (eventType === "agent.step.completed") {
        return "completed";
    }
    return stepStatus || "started";
}

function auditSummary(data) {
    return {
        verdict: data.verdict || null,
        recommendedAnswerMode: data.recommendedAnswerMode || null,
        counts: traceCountSummary(data)
    };
}

function documentSummary(data) {
    return {
        format: data.format || null,
        title: data.title || null,
        sectionCount: Number(data.sectionCount ?? 0)
    };
}

function traceCountSummary(data) {
    return Object.fromEntries([
        "paperEvidenceCount",
        "webEvidenceCount",
        "memoryContextCount",
        "claimCount",
        "conflictCount",
        "evidenceGapCount",
        "unsupportedClaimCount",
        "sourcePolicyIssueCount",
        "requiredRevisionCount"
    ].filter((key) => data[key] !== undefined && data[key] !== null).map((key) => [key, Number(data[key])]));
}

function traceDataOf(payload) {
    if (payload?.data && typeof payload.data === "object") {
        return payload.data;
    }
    return payload && typeof payload === "object" ? payload : {};
}

function applySourceStatusChanged(state, event, payload) {
    const sourceId = String(payload.sourceId || event.sourceId || payload.id || "");
    if (!sourceId) {
        return state;
    }
    const sources = state.sources || [];
    const existingIndex = sources.findIndex((source) => sourceMatches(source, sourceId));
    const patch = {
        id: sourceId,
        sourceId,
        ...payload,
        status: payload.status || "unknown"
    };
    if (existingIndex < 0) {
        return {
            ...state,
            sources: [patch, ...sources]
        };
    }
    return {
        ...state,
        sources: sources.map((source, index) => index === existingIndex ? { ...source, ...patch } : source)
    };
}

function applyAnswerDelta(state, event, payload) {
    const previous = state.currentAnswer || {};
    const answerId = payload.answerId || event.answerId || previous.answerId || null;
    const delta = String(payload.text || payload.delta || payload.content || "");
    const mode = payload.append === false ? "replace" : "append";
    const text = mode === "replace" ? delta : `${previous.text || ""}${delta}`;
    return {
        ...state,
        activeAnswerContext: {
            ...(state.activeAnswerContext || {}),
            answerId
        },
        currentAnswer: {
            ...previous,
            answerId,
            text,
            status: "streaming"
        }
    };
}

function applyAnswerCompleted(state, event, payload) {
    const previous = state.currentAnswer || {};
    return {
        ...state,
        currentAnswer: {
            ...previous,
            answerId: payload.answerId || event.answerId || previous.answerId || null,
            text: payload.answer || payload.text || previous.text || "",
            status: "completed",
            outputMode: payload.answerMode || payload.outputMode || previous.outputMode || null,
            evidenceState: payload.evidenceState || previous.evidenceState || null,
            citationCount: Number(payload.citationCount ?? previous.citationCount ?? 0)
        }
    };
}

function applyEvidenceEvaluated(state, event, payload) {
    const previous = state.currentAnswer || {};
    const evidenceSources = Array.isArray(payload.evidenceSources)
            ? payload.evidenceSources
            : Array.isArray(payload.sources)
                    ? payload.sources
                    : state.evidenceSources || [];
    return {
        ...state,
        activeAnswerContext: {
            ...(state.activeAnswerContext || {}),
            answerId: event.answerId || previous.answerId || null,
            citationCount: Number(payload.citationCount ?? 0)
        },
        currentAnswer: {
            ...previous,
            answerId: event.answerId || previous.answerId || null,
            evidenceState: payload.evidenceState || previous.evidenceState || null,
            outputMode: payload.outputMode || previous.outputMode || null,
            citationCount: Number(payload.citationCount ?? previous.citationCount ?? 0)
        },
        evidenceSources
    };
}

function applyCandidateCreated(state, event, payload) {
    const candidate = normalizeCandidate(payload.candidate || payload, event);
    const candidates = state.candidates || [];
    const exists = candidates.some((item) => item.id === candidate.id);
    return {
        ...state,
        selectedSidebarView: "candidate-confirmation",
        activeAnswerContext: {
            ...(state.activeAnswerContext || {}),
            answerId: candidate.answerId || state.activeAnswerContext?.answerId || event.answerId || null
        },
        candidates: exists
                ? candidates.map((item) => item.id === candidate.id ? { ...item, ...candidate } : item)
                : [candidate, ...candidates]
    };
}

function applyKnowledgeEntryCreated(state, event, payload) {
    const entry = normalizeKnowledgeEntry(payload.entry || payload, event);
    const board = normalizeKnowledgeBoard(state.knowledgeBoard);
    const sections = board.sections.map((section) => {
        if (section.section !== entry.section) {
            return section;
        }
        const exists = section.entries.some((item) => item.id === entry.id);
        return {
            ...section,
            entries: exists
                    ? section.entries.map((item) => item.id === entry.id ? { ...item, ...entry } : item)
                    : [entry, ...section.entries]
        };
    });
    const hasSection = sections.some((section) => section.section === entry.section);
    return {
        ...state,
        selectedSidebarView: "knowledge-board",
        candidates: (state.candidates || []).map((candidate) => {
            if (candidate.id && candidate.id === entry.sourceCandidateId) {
                return { ...candidate, status: candidate.status === "edited_accepted" ? "edited_accepted" : "accepted" };
            }
            return candidate;
        }),
        knowledgeBoard: {
            ...board,
            sections: hasSection
                    ? sections
                    : [{ section: entry.section, title: titleForSection(entry.section), entries: [entry] }, ...sections]
        }
    };
}

function normalizeEventType(eventType) {
    if (typeof eventType === "string") {
        return eventType;
    }
    if (eventType && typeof eventType.wireName === "string") {
        return eventType.wireName;
    }
    return "";
}

function normalizeCandidate(raw, event) {
    const id = raw.id || raw.candidateId || event.candidateId || stableFallbackId("candidate", event);
    return {
        id,
        candidateId: id,
        projectId: raw.projectId || event.projectId || null,
        sessionId: raw.sessionId || event.sessionId || null,
        answerId: raw.answerId || event.answerId || null,
        title: raw.title || "未命名候选",
        statement: raw.statement || raw.content || "",
        suggestedSection: raw.suggestedSection || raw.section || "open_question",
        sourceTypes: Array.isArray(raw.sourceTypes) ? raw.sourceTypes : [],
        evidenceSourceIds: Array.isArray(raw.evidenceSourceIds) ? raw.evidenceSourceIds : [],
        status: raw.status || "pending",
        createdAt: raw.createdAt || event.createdAt || "",
        updatedAt: raw.updatedAt || raw.createdAt || event.createdAt || ""
    };
}

function normalizeKnowledgeEntry(raw, event) {
    const id = raw.id || raw.entryId || event.entryId || stableFallbackId("entry", event);
    return {
        id,
        projectId: raw.projectId || event.projectId || null,
        section: raw.section || "open_question",
        title: raw.title || "未命名知识",
        content: raw.content || raw.statement || "",
        evidenceStatus: raw.evidenceStatus || "unverified",
        sourceCandidateId: raw.sourceCandidateId || raw.candidateId || null,
        evidenceSourceIds: Array.isArray(raw.evidenceSourceIds) ? raw.evidenceSourceIds : [],
        archived: Boolean(raw.archived),
        createdAt: raw.createdAt || event.createdAt || "",
        updatedAt: raw.updatedAt || raw.createdAt || event.createdAt || ""
    };
}

function normalizeKnowledgeBoard(board) {
    const existingSections = Array.isArray(board?.sections) ? board.sections : Array.isArray(board) ? board : [];
    const sectionMap = new Map(existingSections.map((section) => [
        section.section,
        {
            ...section,
            title: section.title || titleForSection(section.section),
            entries: Array.isArray(section.entries) ? section.entries : []
        }
    ]));
    for (const section of KNOWLEDGE_SECTIONS) {
        if (!sectionMap.has(section.section)) {
            sectionMap.set(section.section, { ...section, entries: [] });
        }
    }
    return {
        sections: [...sectionMap.values()]
    };
}

function titleForSection(section) {
    return KNOWLEDGE_SECTIONS.find((item) => item.section === section)?.title || section;
}

function stableFallbackId(prefix, event) {
    const eventId = event?.eventId || event?.id || event?.createdAt || "unknown";
    return `${prefix}-${eventId}`;
}

function sourceMatches(source, sourceId) {
    return String(source.id || source.sourceId || source.documentId || "") === sourceId;
}

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
            abstractText: "暂未提取到摘要内容。",
            methods: [],
            contributions: [],
            keywords: [documentTitle],
            outline: ["正在等待结构化大纲"]
        };
    }

    return {
        summary: analysis.summary || "当前文档暂未生成结构化总结。",
        abstractText: analysis.abstractText || "暂未提取到摘要内容。",
        methods: Array.isArray(analysis.methods) ? analysis.methods : [],
        contributions: Array.isArray(analysis.contributions) ? analysis.contributions : [],
        keywords: Array.isArray(analysis.keywords) && analysis.keywords.length > 0 ? analysis.keywords : [documentTitle],
        outline: Array.isArray(analysis.outline) && analysis.outline.length > 0 ? analysis.outline : ["暂无可用大纲"]
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
            detail: data?.detail || "正在准备候选证据片段。"
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
