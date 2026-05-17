package com.researchassistant.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeBoardRepository {

    public static final List<String> BOARD_SECTIONS = List.of(
            "current_candidates",
            "core_concept",
            "method_route",
            "confirmed_finding",
            "open_question"
    );
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final Set<String> EVIDENCE_STATUSES = Set.of("confirmed", "unverified", "needs_review");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkbenchEventPublisher eventPublisher;

    public KnowledgeBoardRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WorkbenchEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public List<KnowledgeBoardSection> listBoard(String projectId) {
        List<KnowledgeEntryRecord> entries = jdbcTemplate.query("""
                select id, project_id, section, title, content, evidence_status, source_candidate_id,
                       evidence_source_ids_json, archived, created_at, updated_at
                from knowledge_entry
                where project_id = ?
                  and archived = false
                order by updated_at desc, created_at desc, id desc
                """, (resultSet, rowNum) -> mapEntry(resultSet), projectId);

        return BOARD_SECTIONS.stream()
                .map(section -> new KnowledgeBoardSection(
                        section,
                        entries.stream()
                                .filter(entry -> section.equals(entry.section()))
                                .toList()
                ))
                .toList();
    }

    public List<KnowledgeEntryRecord> listConfirmedProjectKnowledge(String projectId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        return listConfirmedProjectKnowledgeCandidates(projectId, safeLimit);
    }

    public List<KnowledgeEntryRecord> listConfirmedProjectKnowledgeCandidates(String projectId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return jdbcTemplate.query("""
                select id, project_id, section, title, content, evidence_status, source_candidate_id,
                       evidence_source_ids_json, archived, created_at, updated_at
                from knowledge_entry
                where project_id = ?
                  and archived = false
                  and evidence_status = 'confirmed'
                order by updated_at desc, created_at desc, id desc
                limit ?
                """, (resultSet, rowNum) -> mapEntry(resultSet), projectId, safeLimit);
    }

    public KnowledgeEntryRecord createEntry(
            String projectId,
            String section,
            String title,
            String content,
            String evidenceStatus,
            String sourceCandidateId,
            List<String> evidenceSourceIds) {
        validateSection(section);
        validateEvidenceStatus(evidenceStatus);
        validateEvidenceSourceIds(projectId, evidenceSourceIds);
        String entryId = UUID.randomUUID().toString();
        KnowledgeEntryRecord entry = jdbcTemplate.queryForObject("""
                insert into knowledge_entry(
                    id, project_id, candidate_id, source_candidate_id, section, title, content,
                    evidence_status, evidence_source_ids_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                returning id, project_id, section, title, content, evidence_status, source_candidate_id,
                          evidence_source_ids_json, archived, created_at, updated_at
                """,
                (resultSet, rowNum) -> mapEntry(resultSet),
                entryId,
                projectId,
                sourceCandidateId,
                sourceCandidateId,
                section,
                title,
                content,
                evidenceStatus,
                toJson(evidenceSourceIds == null ? List.of() : evidenceSourceIds)
        );
        publishKnowledgeEntryCreated(entry);
        return entry;
    }

    public Optional<KnowledgeEntryRecord> patchEntry(
            String projectId,
            String entryId,
            String section,
            String title,
            String content,
            String evidenceStatus) {
        validateSection(section);
        validateEvidenceStatus(evidenceStatus);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    update knowledge_entry
                    set section = ?,
                        title = ?,
                        content = ?,
                        evidence_status = ?
                    where project_id = ?
                      and id = ?
                      and archived = false
                    returning id, project_id, section, title, content, evidence_status, source_candidate_id,
                              evidence_source_ids_json, archived, created_at, updated_at
                    """,
                    (resultSet, rowNum) -> mapEntry(resultSet),
                    section,
                    title,
                    content,
                    evidenceStatus,
                    projectId,
                    entryId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public boolean archiveEntry(String projectId, String entryId) {
        int updated = jdbcTemplate.update("""
                update knowledge_entry
                set archived = true
                where project_id = ?
                  and id = ?
                  and archived = false
                """, projectId, entryId);
        return updated > 0;
    }

    private KnowledgeEntryRecord mapEntry(ResultSet resultSet) throws SQLException {
        return new KnowledgeEntryRecord(
                resultSet.getString("id"),
                resultSet.getString("project_id"),
                resultSet.getString("section"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("evidence_status"),
                resultSet.getString("source_candidate_id"),
                fromJsonList(resultSet.getString("evidence_source_ids_json")),
                resultSet.getBoolean("archived"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private void publishKnowledgeEntryCreated(KnowledgeEntryRecord entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entry.id());
        payload.put("section", entry.section());
        payload.put("title", entry.title());
        payload.put("sourceCandidateId", entry.sourceCandidateId());
        eventPublisher.publish(new WorkbenchEvent(
                null,
                WorkbenchEventType.KNOWLEDGE_ENTRY_CREATED,
                entry.projectId(),
                null,
                null,
                "candidate-api",
                0,
                null,
                null,
                null,
                null,
                payload
        ));
    }

    private void validateSection(String section) {
        if (!BOARD_SECTIONS.contains(section)) {
            throw new IllegalArgumentException("Unsupported knowledge board section: " + section);
        }
    }

    private void validateEvidenceStatus(String evidenceStatus) {
        if (!EVIDENCE_STATUSES.contains(evidenceStatus)) {
            throw new IllegalArgumentException("Unsupported knowledge evidence status: " + evidenceStatus);
        }
    }

    private void validateEvidenceSourceIds(String projectId, List<String> evidenceSourceIds) {
        if (evidenceSourceIds == null || evidenceSourceIds.isEmpty()) {
            return;
        }
        List<String> uniqueIds = evidenceSourceIds.stream().distinct().toList();
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from evidence_source
                where project_id = ?
                  and id = any (?::varchar[])
                """, Integer.class, projectId, uniqueIds.toArray(String[]::new));
        if (count == null || count != uniqueIds.size()) {
            throw new IllegalArgumentException("evidenceSourceIds must belong to the same project");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize knowledge entry JSON", e);
        }
    }

    private List<String> fromJsonList(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize knowledge entry JSON", e);
        }
    }
}
