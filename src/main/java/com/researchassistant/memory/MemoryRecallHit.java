package com.researchassistant.memory;

public record MemoryRecallHit(
        MemoryEntry entry,
        double finalScore
) {
}
