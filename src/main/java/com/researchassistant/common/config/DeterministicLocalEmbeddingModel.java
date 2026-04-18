package com.researchassistant.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public class DeterministicLocalEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 1536;
    private static final Pattern SPLITTER = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+");

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request == null ? List.of() : request.getInstructions();
        for (int index = 0; index < instructions.size(); index++) {
            embeddings.add(new Embedding(embed(instructions.get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document == null ? "" : document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return vector;
        }

        for (String token : tokens) {
            addSignal(vector, token, 1.0f);
            if (token.length() >= 3) {
                for (int i = 0; i <= token.length() - 3; i++) {
                    addSignal(vector, token.substring(i, i + 3), 0.45f);
                }
            }
        }
        addSignal(vector, normalize(text), 0.75f);
        normalizeVector(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private List<String> tokenize(String text) {
        return SPLITTER.splitAsStream(normalize(text))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private void addSignal(float[] vector, String token, float weight) {
        int seed = fnv1a(token);
        for (int round = 0; round < 4; round++) {
            seed = mix(seed + round * 0x9E3779B9);
            int index = Math.floorMod(seed, DIMENSIONS);
            float signedWeight = ((seed & 1) == 0 ? 1.0f : -1.0f) * weight / (round + 1);
            vector[index] += signedWeight;
        }
    }

    private int fnv1a(String token) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < token.length(); i++) {
            hash ^= token.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private int mix(int value) {
        value ^= (value >>> 16);
        value *= 0x7feb352d;
        value ^= (value >>> 15);
        value *= 0x846ca68b;
        value ^= (value >>> 16);
        return value;
    }

    private void normalizeVector(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }
}
