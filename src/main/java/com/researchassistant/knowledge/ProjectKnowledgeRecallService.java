package com.researchassistant.knowledge;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class ProjectKnowledgeRecallService {

    private static final int CANDIDATE_LIMIT = 50;

    private final KnowledgeBoardRepository knowledgeBoardRepository;
    private final EmbeddingModel embeddingModel;

    public ProjectKnowledgeRecallService(KnowledgeBoardRepository knowledgeBoardRepository,
                                         EmbeddingModel embeddingModel) {
        this.knowledgeBoardRepository = knowledgeBoardRepository;
        this.embeddingModel = embeddingModel;
    }

    public List<ProjectKnowledgeRecallHit> recall(String projectId, String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        List<KnowledgeEntryRecord> candidates = knowledgeBoardRepository.listConfirmedProjectKnowledgeCandidates(
                projectId,
                Math.max(CANDIDATE_LIMIT, safeLimit)
        );
        if (candidates.isEmpty()) {
            return List.of();
        }
        float[] queryVector = embeddingModel.embed(query == null ? "" : query);
        return IntStream.range(0, candidates.size())
                .mapToObj(index -> score(candidates.get(index), queryVector, recencyScore(index, candidates.size())))
                .sorted(Comparator.comparingDouble(ProjectKnowledgeRecallHit::finalScore).reversed()
                        .thenComparing(hit -> hit.entry().updatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(hit -> hit.entry().id()))
                .limit(safeLimit)
                .toList();
    }

    private ProjectKnowledgeRecallHit score(KnowledgeEntryRecord entry, float[] queryVector, double recencyScore) {
        String text = entry.title() + "\n" + entry.content();
        double semanticScore = cosine(queryVector, embeddingModel.embed(text));
        double evidenceScore = "confirmed".equals(entry.evidenceStatus()) ? 1.0 : 0.0;
        double finalScore = semanticScore * 0.85 + evidenceScore * 0.05 + recencyScore * 0.10;
        return new ProjectKnowledgeRecallHit(
                entry,
                round(finalScore),
                round(semanticScore),
                round(evidenceScore),
                round(recencyScore),
                "semantic_confirmed_project_knowledge"
        );
    }

    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0;
        }
        int limit = Math.min(left.length, right.length);
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < limit; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return Math.max(0.0, dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private double recencyScore(int index, int candidateCount) {
        if (candidateCount <= 1) {
            return 1.0;
        }
        double normalizedIndex = (double) index / (candidateCount - 1);
        return Math.max(0.0, 1.0 - normalizedIndex);
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
