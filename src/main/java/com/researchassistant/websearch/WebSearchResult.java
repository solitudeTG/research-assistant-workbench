package com.researchassistant.websearch;

import java.util.List;

public record WebSearchResult(
        String query,
        List<WebSearchHit> hits,
        String provider,
        boolean degraded,
        String message
) {

    public static WebSearchResult degraded(String query, String provider, String message) {
        return new WebSearchResult(query, List.of(), provider, true, message);
    }
}
