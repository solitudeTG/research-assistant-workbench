package com.researchassistant.websearch;

public record WebSearchHit(
        String title,
        String url,
        String snippet,
        double score
) {
}
