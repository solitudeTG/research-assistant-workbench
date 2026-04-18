package com.researchassistant.memory;

import java.util.List;

public record MemoryRecallResult(
        String query,
        List<MemoryRecallHit> hits
) {

    public boolean isEmpty() {
        return hits == null || hits.isEmpty();
    }

    public String contextBlock() {
        if (isEmpty()) {
            return "";
        }
        return hits.stream()
                .map(hit -> "- Topic: " + hit.entry().topic() + "\n  Summary: " + hit.entry().summary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
