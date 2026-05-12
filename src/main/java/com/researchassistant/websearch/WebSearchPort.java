package com.researchassistant.websearch;

public interface WebSearchPort {

    WebSearchResult search(String query, int maxResults);
}
