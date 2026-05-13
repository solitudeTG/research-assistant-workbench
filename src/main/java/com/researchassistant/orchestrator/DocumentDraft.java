package com.researchassistant.orchestrator;

import java.util.List;

public record DocumentDraft(
        String format,
        String title,
        String body,
        List<Section> sections
) {

    public DocumentDraft {
        sections = List.copyOf(sections);
    }

    public record Section(String heading, String body) {
    }
}
