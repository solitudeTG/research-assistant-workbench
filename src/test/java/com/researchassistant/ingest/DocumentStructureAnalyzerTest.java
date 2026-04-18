package com.researchassistant.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStructureAnalyzerTest {

    private final DocumentStructureAnalyzer analyzer = new DocumentStructureAnalyzer();

    @Test
    void analyzeExtractsAbstractOutlineAndKeywords() {
        String text = """
                Abstract
                We propose a retrieval-guided planning framework for long-horizon research assistants.

                1 Introduction
                This paper studies how an assistant can use memory and evidence together.

                2 Method
                Our method combines memory recall, paper retrieval, and plan execution.

                3 Contributions
                Our main contributions are a multi-stage planner and a grounded evidence boundary.

                4 Conclusion
                Results show more stable answers and better traceability.
                """;

        DocumentAnalysisDraft analysis = analyzer.analyze("Research Assistant Planning.pdf", text);

        assertThat(analysis.abstractText()).contains("retrieval-guided planning framework");
        assertThat(analysis.outline()).contains("Abstract", "1 Introduction", "2 Method", "3 Contributions");
        assertThat(analysis.methods()).isNotEmpty();
        assertThat(analysis.contributions()).isNotEmpty();
        assertThat(analysis.keywords()).contains("memory", "retrieval");
        assertThat(analysis.summary()).isNotBlank();
    }
}
