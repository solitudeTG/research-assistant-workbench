package com.researchassistant.orchestrator;

import java.util.List;
import java.util.Objects;

public record DocumentDraft(
        String format,
        String title,
        String body,
        List<Section> sections
) {

    public DocumentDraft {
        format = Objects.requireNonNull(format, "format");
        title = Objects.requireNonNull(title, "title");
        body = Objects.requireNonNull(body, "body");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    }

    public record Section(String heading, String body) {
    }
}
