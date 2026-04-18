package com.researchassistant.memory;

public enum GlobalKnowledgeNoteType {
    USER("USER"),
    SOUL("SOUL"),
    RESEARCH_STATE("Research_state");

    private final String fileStem;

    GlobalKnowledgeNoteType(String fileStem) {
        this.fileStem = fileStem;
    }

    public String fileName() {
        return fileStem + ".md";
    }
}
