package com.researchassistant.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryEntryRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    public MemoryEntryRepository(JdbcTemplate jdbcTemplate,
                                 NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                 ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public MemoryEntry append(MemoryEntryDraft draft) {
        Long id = jdbcTemplate.queryForObject("""
                insert into memory_entry(
                    session_id, source_kind, topic, summary, key_findings_json,
                    open_questions_json, keywords_json, source_message_start_id, source_message_end_id
                ) values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                returning id
                """,
                Long.class,
                draft.sessionId(),
                draft.sourceKind(),
                draft.topic(),
                draft.summary(),
                toJson(draft.keyFindings()),
                toJson(draft.openQuestions()),
                toJson(draft.keywords()),
                draft.sourceMessageStartId(),
                draft.sourceMessageEndId()
        );
        if (id == null) {
            throw new IllegalStateException("Failed to create memory entry");
        }
        return findById(id).orElseThrow();
    }

    public Optional<MemoryEntry> findLatestForSession(long sessionId) {
        return jdbcTemplate.query("""
                        select *
                        from memory_entry
                        where session_id = ?
                        order by id desc
                        limit 1
                        """,
                resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(),
                sessionId
        );
    }

    public Optional<MemoryEntry> findById(long id) {
        return jdbcTemplate.query("""
                        select *
                        from memory_entry
                        where id = ?
                        """,
                resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(),
                id
        );
    }

    public List<MemoryEntry> search(String query, int limit) {
        return namedParameterJdbcTemplate.query("""
                select *,
                       ts_rank(
                           to_tsvector('simple',
                               coalesce(topic, '') || ' ' ||
                               coalesce(summary, '') || ' ' ||
                               coalesce(key_findings_json::text, '') || ' ' ||
                               coalesce(open_questions_json::text, '')
                           ),
                           websearch_to_tsquery('simple', :query)
                       ) as keyword_score
                from memory_entry
                where to_tsvector(
                        'simple',
                        coalesce(topic, '') || ' ' ||
                        coalesce(summary, '') || ' ' ||
                        coalesce(key_findings_json::text, '') || ' ' ||
                        coalesce(open_questions_json::text, '')
                    ) @@ websearch_to_tsquery('simple', :query)
                order by keyword_score desc, updated_at desc
                limit :limit
                """,
                java.util.Map.of("query", query, "limit", limit),
                (resultSet, rowNum) -> map(resultSet)
        );
    }

    public List<MemoryEntry> listRecent(int limit) {
        return jdbcTemplate.query("""
                select *
                from memory_entry
                order by updated_at desc, id desc
                limit ?
                """,
                (resultSet, rowNum) -> map(resultSet),
                limit
        );
    }

    public MemoryEntry mergeIntoLatest(long latestEntryId, MemoryEntryDraft draft) {
        MemoryEntry latest = findById(latestEntryId).orElseThrow();
        List<String> keyFindings = mergeUnique(latest.keyFindings(), draft.keyFindings());
        List<String> openQuestions = mergeUnique(latest.openQuestions(), draft.openQuestions());
        List<String> keywords = mergeUnique(latest.keywords(), draft.keywords());
        String mergedSummary = latest.summary() + System.lineSeparator() + "- " + draft.summary();

        jdbcTemplate.update("""
                update memory_entry
                set summary = ?,
                    key_findings_json = ?::jsonb,
                    open_questions_json = ?::jsonb,
                    keywords_json = ?::jsonb,
                    source_message_end_id = ?
                where id = ?
                """,
                mergedSummary,
                toJson(keyFindings),
                toJson(openQuestions),
                toJson(keywords),
                draft.sourceMessageEndId(),
                latestEntryId
        );
        return findById(latestEntryId).orElseThrow();
    }

    private MemoryEntry map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new MemoryEntry(
                resultSet.getLong("id"),
                resultSet.getObject("session_id", Long.class),
                resultSet.getString("source_kind"),
                resultSet.getString("topic"),
                resultSet.getString("summary"),
                fromJson(resultSet.getString("key_findings_json")),
                fromJson(resultSet.getString("open_questions_json")),
                fromJson(resultSet.getString("keywords_json")),
                resultSet.getLong("source_message_start_id"),
                resultSet.getLong("source_message_end_id"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize memory entry", e);
        }
    }

    private List<String> fromJson(String values) {
        try {
            return values == null || values.isBlank() ? List.of() : objectMapper.readValue(values, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize memory entry", e);
        }
    }

    private List<String> mergeUnique(List<String> left, List<String> right) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return merged.stream().limit(8).toList();
    }
}
