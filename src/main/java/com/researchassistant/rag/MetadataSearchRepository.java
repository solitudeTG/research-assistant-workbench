package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MetadataSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MetadataSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String sql = """
                select -id as chunk_id,
                       id as document_id,
                       -1 as chunk_index,
                       title as content,
                       (
                           case when lower(title) like '%' || lower(:query) || '%' then 1.0 else 0.0 end
                           + case when lower(original_file_name) like '%' || lower(:query) || '%' then 0.6 else 0.0 end
                       ) as score
                from research_document
                where (:documentIdsEmpty = true or id in (:documentIds))
                  and (
                      lower(title) like '%' || lower(:query) || '%'
                      or lower(original_file_name) like '%' || lower(:query) || '%'
                  )
                order by score desc
                limit :limit
                """;

        Map<String, Object> params = Map.of(
                "query", query,
                "documentIdsEmpty", allowedDocumentIds == null || allowedDocumentIds.isEmpty(),
                "documentIds", allowedDocumentIds == null || allowedDocumentIds.isEmpty() ? List.of(-1L) : allowedDocumentIds,
                "limit", limit
        );

        return jdbcTemplate.query(sql, params, (resultSet, rowNum) -> new RagChunk(
                resultSet.getLong("chunk_id"),
                resultSet.getLong("document_id"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("content"),
                RetrievalFeedbackScoring.finalScore(resultSet.getDouble("score"), 0.0)
        ));
    }
}
