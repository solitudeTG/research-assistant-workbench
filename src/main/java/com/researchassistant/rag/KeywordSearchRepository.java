package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KeywordSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public KeywordSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        String sql = """
                select id, document_id, chunk_index, content,
                       ts_rank(to_tsvector('simple', content), websearch_to_tsquery('simple', :query)) as relevance_score,
                       coalesce(feedback_score, 0) as feedback_score
                from document_chunk
                where (:documentIdsEmpty = true or document_id in (:documentIds))
                  and to_tsvector('simple', content) @@ websearch_to_tsquery('simple', :query)
                order by (
                    ts_rank(to_tsvector('simple', content), websearch_to_tsquery('simple', :query))
                    + least(0.2, greatest(-0.2, coalesce(feedback_score, 0) * 0.05))
                ) desc
                limit :limit
                """;

        Map<String, Object> params = Map.of(
                "query", query,
                "documentIdsEmpty", allowedDocumentIds == null || allowedDocumentIds.isEmpty(),
                "documentIds", allowedDocumentIds == null || allowedDocumentIds.isEmpty() ? List.of(-1L) : allowedDocumentIds,
                "limit", limit
        );

        return jdbcTemplate.query(sql, params, (resultSet, rowNum) -> new RagChunk(
                resultSet.getLong("id"),
                resultSet.getLong("document_id"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("content"),
                RetrievalFeedbackScoring.finalScore(
                        resultSet.getDouble("relevance_score"),
                        resultSet.getDouble("feedback_score")),
                resultSet.getDouble("feedback_score")
        ));
    }
}
