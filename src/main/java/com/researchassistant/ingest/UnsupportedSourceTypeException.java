package com.researchassistant.ingest;

public class UnsupportedSourceTypeException extends RuntimeException {

    public UnsupportedSourceTypeException(String type) {
        super("Unsupported source type: " + type);
    }
}
