package com.researchassistant.ingest;

import java.util.List;

public record DocumentAnalysisDraft(
        String abstractText,
        String summary,
        List<String> methods,
        List<String> contributions,
        List<String> keywords,
        List<String> outline
) {
}
