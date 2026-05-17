package com.researchassistant.ingest.model;

public enum DocumentStatus {
    UPLOADED,
    SUBMITTED,
    PARSING,
    FETCHING,
    INDEXING,
    EXTRACTING,
    INDEXED,
    DEPOSITING,
    DEPOSITED,
    FAILED
}
