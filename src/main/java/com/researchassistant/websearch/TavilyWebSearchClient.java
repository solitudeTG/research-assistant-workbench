package com.researchassistant.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TavilyWebSearchClient implements WebSearchPort {

    private final RestClient restClient;
    private final String apiKey;
    private final String searchDepth;

    public TavilyWebSearchClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.web-search.tavily.base-url:https://api.tavily.com}") String baseUrl,
            @Value("${app.web-search.tavily.api-key:}") String apiKey,
            @Value("${app.web-search.tavily.search-depth:basic}") String searchDepth) {
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.searchDepth = searchDepth == null || searchDepth.isBlank() ? "basic" : searchDepth;
    }

    @Override
    public WebSearchResult search(String query, int maxResults) {
        if (apiKey.isBlank()) {
            return WebSearchResult.degraded(query, "tavily", "Tavily API key is not configured.");
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/search")
                    .body(Map.of(
                            "api_key", apiKey,
                            "query", safeQuery(query),
                            "max_results", Math.max(1, maxResults),
                            "search_depth", searchDepth
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            return new WebSearchResult(
                    query,
                    hitsFrom(response),
                    "tavily",
                    false,
                    ""
            );
        } catch (RestClientException exception) {
            return WebSearchResult.degraded(
                    query,
                    "tavily",
                    "Tavily search failed: " + exception.getClass().getSimpleName()
            );
        }
    }

    private List<WebSearchHit> hitsFrom(JsonNode response) {
        if (response == null || !response.has("results") || !response.get("results").isArray()) {
            return List.of();
        }
        List<WebSearchHit> hits = new ArrayList<>();
        for (JsonNode result : response.get("results")) {
            hits.add(new WebSearchHit(
                    text(result, "title"),
                    text(result, "url"),
                    text(result, "content"),
                    result.has("score") ? result.get("score").asDouble() : 0.0
            ));
        }
        return hits;
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : "";
    }

    private String safeQuery(String query) {
        return query == null ? "" : query;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.tavily.com";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
