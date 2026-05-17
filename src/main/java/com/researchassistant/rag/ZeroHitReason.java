package com.researchassistant.rag;

public enum ZeroHitReason {
    NO_SCOPED_EVIDENCE,
    QUERY_EMPTY_OR_INVALID,
    NO_BACKEND_HITS,
    SCOPE_FILTERED_EMPTY,
    RERANK_EMPTY,
    TOOL_ERROR,
    UNKNOWN
}
