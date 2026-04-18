package com.researchassistant.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.ingest.model.DocumentAnalysis;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentAnalysisRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DocumentAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void upsert(long documentId, DocumentAnalysisDraft analysis) {
        jdbcTemplate.update("""
                insert into document_analysis (
                    document_id,
                    abstract_text,
                    structured_summary,
                    methods_json,
                    contributions_json,
                    keywords_json,
                    outline_json
                ) values (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
                on conflict (document_id)
                do update set
                    abstract_text = excluded.abstract_text,
                    structured_summary = excluded.structured_summary,
                    methods_json = excluded.methods_json,
                    contributions_json = excluded.contributions_json,
                    keywords_json = excluded.keywords_json,
                    outline_json = excluded.outline_json,
                    generated_at = now(),
                    updated_at = now()
                """,
                documentId,
                emptyToNull(analysis.abstractText()),
                emptyToNull(analysis.summary()),
                toJson(analysis.methods()),
                toJson(analysis.contributions()),
                toJson(analysis.keywords()),
                toJson(analysis.outline())
        );
    }

    public Optional<DocumentAnalysis> findByDocumentId(long documentId) {
        return jdbcTemplate.query("""
                        select document_id, abstract_text, structured_summary, methods_json, contributions_json,
                               keywords_json, outline_json, generated_at, updated_at
                        from document_analysis
                        where document_id = ?
                        """,
                rs -> rs.next()
                        ? Optional.of(new DocumentAnalysis(
                        rs.getLong("document_id"),
                        rs.getString("abstract_text"),
                        rs.getString("structured_summary"),
                        fromJson(rs.getString("methods_json")),
                        fromJson(rs.getString("contributions_json")),
                        fromJson(rs.getString("keywords_json")),
                        fromJson(rs.getString("outline_json")),
                        rs.getObject("generated_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ))
                        : Optional.empty(),
                documentId
        );
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize document analysis", e);
        }
    }

    private List<String> fromJson(String values) {
        try {
            return values == null || values.isBlank() ? List.of() : objectMapper.readValue(values, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize document analysis", e);
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
