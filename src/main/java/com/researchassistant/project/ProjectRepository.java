package com.researchassistant.project;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProjectRecord createProject(String topic, String summary) {
        String projectId = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject("""
                insert into research_project(id, topic, summary)
                values (?, ?, ?)
                returning id, topic, summary, created_at, updated_at
                """, (resultSet, rowNum) -> mapProject(resultSet), projectId, topic, summary);
    }

    public Optional<ProjectRecord> findProject(String projectId) {
        List<ProjectRecord> projects = jdbcTemplate.query("""
                select id, topic, summary, created_at, updated_at
                from research_project
                where id = ?
                """, (resultSet, rowNum) -> mapProject(resultSet), projectId);
        return projects.stream().findFirst();
    }

    public List<ProjectRecord> listProjects() {
        return jdbcTemplate.query("""
                select id, topic, summary, created_at, updated_at
                from research_project
                order by updated_at desc, created_at desc, id desc
                """, (resultSet, rowNum) -> mapProject(resultSet));
    }

    public ResearchSessionRecord createSession(String projectId, String title) {
        String sessionId = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject("""
                insert into research_session(id, project_id, title)
                values (?, ?, ?)
                returning id, project_id, title, status, last_message_at
                """, (resultSet, rowNum) -> mapSession(resultSet), sessionId, projectId, title);
    }

    public List<ResearchSessionRecord> listSessions(String projectId) {
        return jdbcTemplate.query("""
                select id, project_id, title, status, last_message_at
                from research_session
                where project_id = ?
                order by updated_at desc, created_at desc, id desc
                """, (resultSet, rowNum) -> mapSession(resultSet), projectId);
    }

    public Optional<ResearchSessionRecord> findSession(String projectId, String sessionId) {
        List<ResearchSessionRecord> sessions = jdbcTemplate.query("""
                select id, project_id, title, status, last_message_at
                from research_session
                where project_id = ?
                  and id = ?
                """, (resultSet, rowNum) -> mapSession(resultSet), projectId, sessionId);
        return sessions.stream().findFirst();
    }

    public Optional<ResearchSessionRecord> renameSession(String projectId, String sessionId, String title) {
        List<ResearchSessionRecord> sessions = jdbcTemplate.query("""
                update research_session
                set title = ?
                where project_id = ?
                  and id = ?
                returning id, project_id, title, status, last_message_at
                """, (resultSet, rowNum) -> mapSession(resultSet), title, projectId, sessionId);
        return sessions.stream().findFirst();
    }

    public void markSessionMessaged(String projectId, String sessionId) {
        jdbcTemplate.update("""
                update research_session
                set last_message_at = now()
                where project_id = ?
                  and id = ?
                """, projectId, sessionId);
    }

    private ProjectRecord mapProject(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        String projectId = resultSet.getString("id");
        return new ProjectRecord(
                projectId,
                resultSet.getString("topic"),
                resultSet.getString("summary"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                loadStats(projectId)
        );
    }

    private ResearchSessionRecord mapSession(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ResearchSessionRecord(
                resultSet.getString("id"),
                resultSet.getString("project_id"),
                resultSet.getString("title"),
                resultSet.getString("status"),
                resultSet.getObject("last_message_at", OffsetDateTime.class)
        );
    }

    private ProjectStats loadStats(String projectId) {
        return new ProjectStats(
                countByProject("source_document", projectId),
                countByProject("research_session", projectId),
                countByProject("knowledge_entry", projectId),
                countByProject("knowledge_candidate", projectId)
        );
    }

    private int countByProject(String tableName, String projectId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where project_id = ?",
                Integer.class,
                projectId
        );
        return count == null ? 0 : count;
    }
}
