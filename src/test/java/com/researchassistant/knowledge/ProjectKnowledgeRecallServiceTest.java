package com.researchassistant.knowledge;

import com.researchassistant.common.config.DeterministicLocalEmbeddingModel;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectKnowledgeRecallServiceTest {

    private final KnowledgeBoardRepository knowledgeBoardRepository = mock(KnowledgeBoardRepository.class);
    private final ProjectKnowledgeRecallService recallService = new ProjectKnowledgeRecallService(
            knowledgeBoardRepository,
            new DeterministicLocalEmbeddingModel()
    );

    @Test
    void olderRelevantConfirmedKnowledgeOutranksNewerUnrelatedEntries() {
        String projectId = "project-1";
        OffsetDateTime now = OffsetDateTime.now();
        List<KnowledgeEntryRecord> candidates = List.of(
                entry("new-1", projectId, "New unrelated 1", "Battery charging workflow.", now.minusHours(1)),
                entry("new-2", projectId, "New unrelated 2", "Calendar export checklist.", now.minusHours(2)),
                entry("new-3", projectId, "New unrelated 3", "UI contrast tuning notes.", now.minusHours(3)),
                entry("new-4", projectId, "New unrelated 4", "Docker rebuild troubleshooting.", now.minusHours(4)),
                entry("new-5", projectId, "New unrelated 5", "Report title formatting.", now.minusHours(5)),
                entry("old-relevant", projectId, "Adaptive beamforming strategy",
                        "Adaptive beamforming is the stable project direction for antenna scheduling.",
                        now.minusDays(40))
        );
        when(knowledgeBoardRepository.listConfirmedProjectKnowledgeCandidates(projectId, 50))
                .thenReturn(candidates);

        List<ProjectKnowledgeRecallHit> hits = recallService.recall(
                projectId,
                "adaptive beamforming project direction",
                5
        );

        assertThat(hits).hasSize(5);
        assertThat(hits.get(0).entry().id()).isEqualTo("old-relevant");
        assertThat(hits).extracting(hit -> hit.entry().id()).contains("old-relevant");
        assertThat(hits.get(0).semanticScore()).isGreaterThan(0.0);
        assertThat(hits.get(0).finalScore()).isGreaterThan(0.0);
        assertThat(hits.get(0).evidenceScore()).isEqualTo(1.0);
        assertThat(hits.get(0).reason()).isEqualTo("semantic_confirmed_project_knowledge");
    }

    private KnowledgeEntryRecord entry(
            String id,
            String projectId,
            String title,
            String content,
            OffsetDateTime updatedAt
    ) {
        return new KnowledgeEntryRecord(
                id,
                projectId,
                "confirmed_finding",
                title,
                content,
                "confirmed",
                null,
                List.of(),
                false,
                updatedAt.minusDays(1),
                updatedAt
        );
    }
}
