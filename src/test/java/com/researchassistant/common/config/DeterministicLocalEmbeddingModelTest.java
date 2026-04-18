package com.researchassistant.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicLocalEmbeddingModelTest {

    private final DeterministicLocalEmbeddingModel embeddingModel = new DeterministicLocalEmbeddingModel();

    @Test
    void embedReturnsStableVectorsForSameInput() {
        float[] first = embeddingModel.embed("joint beamforming in LEO satellite networks");
        float[] second = embeddingModel.embed("joint beamforming in LEO satellite networks");

        assertThat(first).hasSize(embeddingModel.dimensions());
        assertThat(second).hasSize(embeddingModel.dimensions());
        assertThat(first).containsExactly(second);
    }

    @Test
    void embedProducesDistinctSignalsForDifferentTopics() {
        float[] satellite = embeddingModel.embed("satellite selection");
        float[] video = embeddingModel.embed("video generation");

        assertThat(satellite).isNotEqualTo(video);
    }
}
