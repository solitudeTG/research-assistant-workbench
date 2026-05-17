package com.researchassistant.candidates;

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
public class KnowledgeCandidateRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final Set<String> SECTIONS = Set.of(
            "core_concept",
            "method_route",
            "confirmed_finding",
            "open_question"
    );
    private static final Set<String> STATUSES = Set.of(
            "pending",
            "accepted",
            "edited_accepted",
            "marked_unverified",
            "ignored"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkbenchEventPublisher eventPublisher;

    public KnowledgeCandidateRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WorkbenchEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public KnowledgeCandidateRecord createCandidate(
            String projectId,
            String sessionId,
            String answerId,
            String title,
            String statement,
            String suggestedSection,
            List<String> sourceTypes,
            List<String> evidenceSourceIds) {
        return createCandidate(
                projectId,
                sessionId,
                answerId,
                title,
                statement,
                suggestedSection,
                sourceTypes,
                evidenceSourceIds,
                null
        );
    }

    public KnowledgeCandidateRecord createCandidate(
            String projectId,
            String sessionId,
            String answerId,
            String title,
            String statement,
            String suggestedSection,
            List<String> sourceTypes,
            List<String> evidenceSourceIds,
            String runId) {
        validateSection(suggestedSection);
        validateEvidenceSourceIds(projectId, evidenceSourceIds);
        String candidateId = UUID.randomUUID().toString();
        KnowledgeCandidateRecord candidate = jdbcTemplate.queryForObject("""
                insert into knowledge_candidate(
                    id, project_id, session_id, answer_id, status, content, metadata_json,
                    title, statement, suggested_section, source_types_json, evidence_source_ids_json
                )
                values (?, ?, ?, ?, 'pending', ?, '{}'::jsonb, ?, ?, ?, ?::jsonb, ?::jsonb)
                returning id, project_id, session_id, answer_id, title, statement, suggested_section,
                          source_types_json, evidence_source_ids_json, status, created_at, updated_at
                """,
                (resultSet, rowNum) -> mapCandidate(resultSet),
                candidateId,
                projectId,
                sessionId,
                answerId,
                statement,
                title,
                statement,
                suggestedSection,
                toJson(sourceTypes == null ? List.of() : sourceTypes),
                toJson(evidenceSourceIds == null ? List.of() : evidenceSourceIds)
        );
        publishCandidateCreated(candidate, runId);
        return candidate;
    }

    public List<KnowledgeCandidateRecord> listByAnswer(String projectId, String answerId) {
        return jdbcTemplate.query("""
                select id, project_id, session_id, answer_id, title, statement, suggested_section,
                       source_types_json, evidence_source_ids_json, status, created_at, updated_at
                from knowledge_candidate
                where project_id = ?
                  and answer_id = ?
                order by created_at desc, id desc
                """, (resultSet, rowNum) -> mapCandidate(resultSet), projectId, answerId);
    }

    public List<KnowledgeCandidateRecord> listByProject(String projectId) {
        return jdbcTemplate.query("""
                select id, project_id, session_id, answer_id, title, statement, suggested_section,
                       source_types_json, evidence_source_ids_json, status, created_at, updated_at
                from knowledge_candidate
                where project_id = ?
                order by created_at desc, id desc
                """, (resultSet, rowNum) -> mapCandidate(resultSet), projectId);
    }

    public Optional<KnowledgeCandidateRecord> findByProject(String projectId, String candidateId) {
        List<KnowledgeCandidateRecord> candidates = jdbcTemplate.query("""
                select id, project_id, session_id, answer_id, title, statement, suggested_section,
                       source_types_json, evidence_source_ids_json, status, created_at, updated_at
                from knowledge_candidate
                where project_id = ?
                  and id = ?
                """, (resultSet, rowNum) -> mapCandidate(resultSet), projectId, candidateId);
        return candidates.stream().findFirst();
    }

    public KnowledgeCandidateRecord updateStatus(String projectId, String candidateId, String status) {
        validateStatus(status);
        return jdbcTemplate.queryForObject("""
                update knowledge_candidate
                set status = ?
                where project_id = ?
                  and id = ?
                returning id, project_id, session_id, answer_id, title, statement, suggested_section,
                          source_types_json, evidence_source_ids_json, status, created_at, updated_at
                """, (resultSet, rowNum) -> mapCandidate(resultSet), status, projectId, candidateId);
    }

    public Optional<KnowledgeCandidateRecord> transitionPending(String projectId, String candidateId, String status) {
        validateStatus(status);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    update knowledge_candidate
                    set status = ?
                    where project_id = ?
                      and id = ?
                      and status = 'pending'
                    returning id, project_id, session_id, answer_id, title, statement, suggested_section,
                              source_types_json, evidence_source_ids_json, status, created_at, updated_at
                    """, (resultSet, rowNum) -> mapCandidate(resultSet), status, projectId, candidateId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private KnowledgeCandidateRecord mapCandidate(ResultSet resultSet) throws SQLException {
        return new KnowledgeCandidateRecord(
                resultSet.getString("id"),
                resultSet.getString("project_id"),
                resultSet.getString("session_id"),
                resultSet.getString("answer_id"),
                resultSet.getString("title"),
                resultSet.getString("statement"),
                resultSet.getString("suggested_section"),
                fromJsonList(resultSet.getString("source_types_json")),
                fromJsonList(resultSet.getString("evidence_source_ids_json")),
                resultSet.getString("status"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private void publishCandidateCreated(KnowledgeCandidateRecord candidate, String runId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", candidate.id());
        payload.put("answerId", candidate.answerId());
        payload.put("title", candidate.title());
        payload.put("statement", candidate.statement());
        payload.put("suggestedSection", candidate.suggestedSection());
        payload.put("sourceTypes", candidate.sourceTypes());
        payload.put("evidenceSourceIds", candidate.evidenceSourceIds());
        payload.put("status", candidate.status());
        payload.put("createdAt", candidate.createdAt());
        payload.put("updatedAt", candidate.updatedAt());
        eventPublisher.publish(new WorkbenchEvent(
                null,
                WorkbenchEventType.CANDIDATE_CREATED,
                candidate.projectId(),
                candidate.sessionId(),
                runId,
                "candidate-api",
                0,
                null,
                candidate.answerId(),
                null,
                null,
                payload
        ));
    }

    private void validateSection(String section) {
        if (!SECTIONS.contains(section)) {
            throw new IllegalArgumentException("Unsupported knowledge candidate section: " + section);
        }
    }

    private void validateStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported knowledge candidate status: " + status);
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
            throw new IllegalStateException("Failed to serialize knowledge candidate JSON", e);
        }
    }

    private List<String> fromJsonList(String value) {
        try {
            return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize knowledge candidate JSON", e);
        }
    }
}
