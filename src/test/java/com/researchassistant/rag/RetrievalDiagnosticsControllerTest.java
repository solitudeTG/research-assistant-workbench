package com.researchassistant.rag;

import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RetrievalDiagnosticsControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void aggregatesRetrievalTraceObservationsForProjectSession() throws Exception {
        ResearchSessionRecord session = createSession();
        long legacySessionId = insertLegacyChatSession(session.id());

        insertTrace(
                legacySessionId,
                "attention mechanisms",
                """
                        {
                          "documentIds": [10],
                          "runId": "run-attention",
                          "messageId": "msg-attention",
                          "answerId": "ans-attention",
                          "answerQuestion": "How does attention support the answer?",
                          "toolCallIndex": 1,
                          "rewriteStrategy": "cjk_llm_rewrite",
                          "rewrittenQueries": ["attention mechanisms", "transformer attention"],
                          "keywords": ["attention", "transformer"]
                        }
                        """,
                """
                        [
                          {
                            "chunkId": 101,
                            "documentId": 10,
                            "chunkIndex": 3,
                            "content": "Attention aligns tokens across a sequence with bounded evidence.",
                            "finalScore": 0.91,
                            "feedbackScore": 3.0
                          }
                        ]
                        """,
                """
                        {
                          "chunks": [],
                          "observation": {
                            "rewriteStrategy": "cjk_llm_rewrite",
                            "backendStats": {
                              "keyword": {"queryCount": 2, "preScopeHits": 3, "postScopeHits": 2, "durationMs": 4},
                              "vector": {"queryCount": 2, "preScopeHits": 5, "postScopeHits": 1, "durationMs": 7},
                              "metadata": {"queryCount": 2, "preScopeHits": 1, "postScopeHits": 1, "durationMs": 2}
                            },
                            "returnedScopedChunkCount": 2,
                            "zeroHitReason": null
                          }
                        }
                        """
        );
        insertTrace(
                legacySessionId,
                "out of scope query",
                """
                        {
                          "documentIds": [10],
                          "runId": "run-scope",
                          "messageId": "msg-scope",
                          "answerId": "ans-scope",
                          "answerQuestion": "Why is this query out of scope?",
                          "toolCallIndex": 1,
                          "rewriteStrategy": "original_only",
                          "rewrittenQueries": ["out of scope query"],
                          "keywords": []
                        }
                        """,
                "[]",
                """
                        {
                          "chunks": [],
                          "observation": {
                            "rewriteStrategy": "original_only",
                            "backendStats": {
                              "keyword": {"queryCount": 1, "preScopeHits": 0, "postScopeHits": 0, "durationMs": 1},
                              "vector": {"queryCount": 1, "preScopeHits": 4, "postScopeHits": 0, "durationMs": 3},
                              "metadata": {"queryCount": 1, "preScopeHits": 0, "postScopeHits": 0, "durationMs": 1}
                            },
                            "returnedScopedChunkCount": 0,
                            "zeroHitReason": "SCOPE_FILTERED_EMPTY"
                          }
                        }
                        """
        );

        mockMvc.perform(get(
                        "/api/projects/{projectId}/sessions/{sessionId}/retrieval-diagnostics",
                        session.projectId(),
                        session.id()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(session.projectId()))
                .andExpect(jsonPath("$.sessionId").value(session.id()))
                .andExpect(jsonPath("$.summary.retrievalCalls").value(2))
                .andExpect(jsonPath("$.summary.zeroHitCalls").value(1))
                .andExpect(jsonPath("$.summary.returnedScopedChunks").value(2))
                .andExpect(jsonPath("$.summary.avgReturnedScopedChunks").value(1.0))
                .andExpect(jsonPath("$.summary.answerRunCount").value(2))
                .andExpect(jsonPath("$.summary.backendTotals.keyword.preScopeHits").value(3))
                .andExpect(jsonPath("$.summary.backendTotals.keyword.postScopeHits").value(2))
                .andExpect(jsonPath("$.summary.backendTotals.vector.preScopeHits").value(9))
                .andExpect(jsonPath("$.summary.backendTotals.vector.postScopeHits").value(1))
                .andExpect(jsonPath("$.summary.zeroHitReasonCounts.SCOPE_FILTERED_EMPTY").value(1))
                .andExpect(jsonPath("$.summary.strategyCounts.cjk_llm_rewrite").value(1))
                .andExpect(jsonPath("$.summary.strategyCounts.original_only").value(1))
                .andExpect(jsonPath("$.retrievals", isA(java.util.List.class)))
                .andExpect(jsonPath("$.retrievals[*].queryText", hasItem("attention mechanisms")))
                .andExpect(jsonPath("$.retrievals[*].queryText", hasItem("out of scope query")))
                .andExpect(jsonPath("$.retrievals[0].answerRunKey")
                        .value("run-scope"))
                .andExpect(jsonPath("$.retrievals[0].runId")
                        .value("run-scope"))
                .andExpect(jsonPath("$.retrievals[0].answerId")
                        .value("ans-scope"))
                .andExpect(jsonPath("$.retrievals[0].messageId")
                        .value("msg-scope"))
                .andExpect(jsonPath("$.retrievals[0].question")
                        .value("Why is this query out of scope?"))
                .andExpect(jsonPath("$.retrievals[0].toolCallIndex")
                        .value(1))
                .andExpect(jsonPath("$.retrievals[1].retrievalQueries[0]")
                        .value("attention mechanisms"))
                .andExpect(jsonPath("$.retrievals[1].keywords[0]")
                        .value("attention"))
                .andExpect(jsonPath("$.retrievals[1].returnedScopedChunkCount")
                        .value(2))
                .andExpect(jsonPath("$.retrievals[1].topChunks[0].snippet")
                        .value("Attention aligns tokens across a sequence with bounded evidence."))
                .andExpect(jsonPath("$.retrievals[1].topChunks[0].feedbackScore")
                        .value(3.0))
                .andExpect(jsonPath("$.retrievals[1].topChunks[0].feedbackScoreAdjustment")
                        .value(0.15))
                .andExpect(jsonPath("$.retrievals[0].zeroHitReason")
                        .value("SCOPE_FILTERED_EMPTY"))
                .andExpect(jsonPath("$.answerRuns[0].answerRunKey")
                        .value("run-scope"))
                .andExpect(jsonPath("$.answerRuns[0].question")
                        .value("Why is this query out of scope?"))
                .andExpect(jsonPath("$.answerRuns[0].retrievalCalls")
                        .value(1))
                .andExpect(jsonPath("$.answerRuns[0].zeroHitCalls")
                        .value(1))
                .andExpect(jsonPath("$.answerRuns[0].returnedScopedChunks")
                        .value(0))
                .andExpect(jsonPath("$.answerRuns[1].answerRunKey")
                        .value("run-attention"))
                .andExpect(jsonPath("$.answerRuns[1].returnedScopedChunks")
                        .value(2));
    }

    @Test
    void returnsNotFoundWhenSessionDoesNotBelongToProject() throws Exception {
        ResearchSessionRecord session = createSession();
        ProjectRecord otherProject = projectRepository.createProject("Other diagnostics project", "summary");

        mockMvc.perform(get(
                        "/api/projects/{projectId}/sessions/{sessionId}/retrieval-diagnostics",
                        otherProject.id(),
                        session.id()
                ))
                .andExpect(status().isNotFound());
    }

    private ResearchSessionRecord createSession() {
        ProjectRecord project = projectRepository.createProject("Diagnostics project", "RAG traces");
        return projectRepository.createSession(project.id(), "Diagnostics session");
    }

    private long insertLegacyChatSession(String sessionKey) {
        return jdbcTemplate.queryForObject("""
                insert into chat_session(session_key)
                values (?)
                returning id
                """, Long.class, sessionKey);
    }

    private void insertTrace(
            long sessionId,
            String queryText,
            String filtersJson,
            String topChunksJson,
            String rerankResultJson
    ) {
        jdbcTemplate.update("""
                insert into retrieval_trace(session_id, query_text, filters_json, top_chunks_json, rerank_result_json)
                values (?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                """, sessionId, queryText, filtersJson, topChunksJson, rerankResultJson);
    }
}
