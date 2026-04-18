package com.researchassistant.memory;

import com.researchassistant.orchestrator.MemoryRecallPort;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MemoryRecallService implements MemoryRecallPort {

    private final MemoryEntryRepository memoryEntryRepository;

    public MemoryRecallService(MemoryEntryRepository memoryEntryRepository) {
        this.memoryEntryRepository = memoryEntryRepository;
    }

    @Override
    public MemoryRecallResult recall(long sessionId, String query, int limit) {
        List<MemoryEntry> hits = memoryEntryRepository.search(normalizeQuery(query), Math.max(limit * 2, limit));
        List<MemoryRecallHit> reranked = hits.stream()
                .map(entry -> new MemoryRecallHit(entry, score(entry, query, sessionId)))
                .sorted(Comparator.comparingDouble(MemoryRecallHit::finalScore).reversed())
                .limit(limit)
                .toList();
        return new MemoryRecallResult(query, reranked);
    }

    private double score(MemoryEntry entry, String query, long sessionId) {
        double keywordScore = overlap(entry, query);
        double recencyBoost = recencyBoost(entry.updatedAt());
        double topicBoost = entry.sessionId() != null && entry.sessionId() == sessionId ? 0.2 : 0.0;
        return keywordScore + recencyBoost + topicBoost;
    }

    private double overlap(MemoryEntry entry, String query) {
        String normalized = normalizeQuery(query);
        long matches = entry.keywords().stream()
                .filter(keyword -> normalized.contains(keyword.toLowerCase(Locale.ROOT)))
                .count();
        if (matches > 0) {
            return Math.min(0.8, 0.2 * matches);
        }
        if (normalized.contains(entry.topic().toLowerCase(Locale.ROOT))) {
            return 0.35;
        }
        return 0.15;
    }

    private double recencyBoost(OffsetDateTime updatedAt) {
        long hours = Math.max(0, Duration.between(updatedAt, OffsetDateTime.now()).toHours());
        if (hours <= 24) {
            return 0.25;
        }
        if (hours <= 72) {
            return 0.15;
        }
        return 0.05;
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.toLowerCase(Locale.ROOT);
    }
}
