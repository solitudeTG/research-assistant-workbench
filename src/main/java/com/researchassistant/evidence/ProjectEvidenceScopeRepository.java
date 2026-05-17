package com.researchassistant.evidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectEvidenceScopeRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectEvidenceScopeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProjectEvidenceScope load(String projectId) {
        Map<Long, String> sourceByIndexedDocument = new LinkedHashMap<>();
        jdbcTemplate.query("""
                select id, indexed_document_id
                from source_document
                where project_id = ?
                  and indexed_document_id is not null
                  and status in ('indexed', 'deposited')
                  and type <> 'web_page'
                order by created_at, id
                """, resultSet -> {
            sourceByIndexedDocument.putIfAbsent(
                    resultSet.getLong("indexed_document_id"),
                    resultSet.getString("id")
            );
        }, projectId);
        return new ProjectEvidenceScope(
                List.copyOf(sourceByIndexedDocument.keySet()),
                Map.copyOf(sourceByIndexedDocument)
        );
    }
}
