package com.researchassistant.knowledge;

import java.util.List;

public record KnowledgeBoardSection(
        String section,
        List<KnowledgeEntryRecord> entries
) {
}
