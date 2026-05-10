package com.researchassistant.ingest.model;

public enum FailureStage {
    STORAGE,
    PARSING,
    FETCHING,
    INDEXING,
    EXTRACTING,
    DEPOSITING
}
