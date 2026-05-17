package com.researchassistant.websearch;

public class NoopWebSearchClient implements WebSearchPort {

    @Override
    public WebSearchResult search(String query, int maxResults) {
        return WebSearchResult.degraded(query, "none", "Web search is not configured.");
    }
}
