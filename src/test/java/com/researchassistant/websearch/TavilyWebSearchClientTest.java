package com.researchassistant.websearch;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TavilyWebSearchClientTest {

    @Test
    void mapsSuccessfulTavilyResponseToWebSearchResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.tavily.test/search"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "title": "Tavily API",
                              "url": "https://docs.tavily.com",
                              "content": "Tavily search API documentation.",
                              "score": 0.91
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TavilyWebSearchClient client = new TavilyWebSearchClient(
                builder,
                "https://api.tavily.test",
                "test-key",
                "basic"
        );

        WebSearchResult result = client.search("Tavily API", 3);

        assertThat(result.degraded()).isFalse();
        assertThat(result.provider()).isEqualTo("tavily");
        assertThat(result.query()).isEqualTo("Tavily API");
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).title()).isEqualTo("Tavily API");
        assertThat(result.hits().get(0).url()).isEqualTo("https://docs.tavily.com");
        assertThat(result.hits().get(0).snippet()).isEqualTo("Tavily search API documentation.");
        assertThat(result.hits().get(0).score()).isEqualTo(0.91);
        server.verify();
    }

    @Test
    void missingApiKeyReturnsDegradedResult() {
        TavilyWebSearchClient client = new TavilyWebSearchClient(
                RestClient.builder(),
                "https://api.tavily.test",
                "",
                "basic"
        );

        WebSearchResult result = client.search("latest RAG eval", 5);

        assertThat(result.degraded()).isTrue();
        assertThat(result.provider()).isEqualTo("tavily");
        assertThat(result.hits()).isEmpty();
        assertThat(result.message()).contains("Tavily API key");
    }

    @Test
    void nonSuccessResponseReturnsDegradedResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.tavily.test/search"))
                .andRespond(withServerError());

        TavilyWebSearchClient client = new TavilyWebSearchClient(
                builder,
                "https://api.tavily.test",
                "test-key",
                "basic"
        );

        WebSearchResult result = client.search("latest RAG eval", 5);

        assertThat(result.degraded()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.message()).contains("failed");
        server.verify();
    }
}
