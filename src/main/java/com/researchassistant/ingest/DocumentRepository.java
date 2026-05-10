package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.ingest.model.ResearchDocument;
import com.researchassistant.ingest.model.SourceDocument;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final String INSERT_SQL = """
            insert into research_document (title, original_file_name, storage_path, status)
            values (?, ?, ?, ?)
            """;

    private static final String UPDATE_STATUS_SQL = """
            update research_document
            set status = ?,
                failure_stage = ?,
                parse_error = ?
            where id = ?
            """;

    private static final String UPDATE_STATS_SQL = """
            update research_document
            set total_chunks = ?,
                total_tokens = ?
            where id = ?
            """;

    private static final String FIND_BY_ID_SQL = """
            select id, title, original_file_name, storage_path, status, failure_stage, parse_error,
                   total_chunks, total_tokens,
                   created_at, updated_at
            from research_document
            where id = ?
            """;

    private static final String FIND_ALL_SQL = """
            select id, title, original_file_name, storage_path, status, failure_stage, parse_error,
                   total_chunks, total_tokens,
                   created_at, updated_at
            from research_document
            order by updated_at desc, id desc
            """;

    private static final String INSERT_SOURCE_SQL = """
            insert into source_document (id, project_id, type, title, uri, status)
            values (?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SOURCE_STATUS_SQL = """
            update source_document
            set status = ?,
                failure_stage = ?,
                error_message = ?
            where project_id = ?
              and id = ?
            """;

    private static final String FIND_SOURCE_SQL = """
            select id, project_id, type, title, uri, status, failure_stage, error_message,
                   deposited_knowledge_count, created_at, updated_at
            from source_document
            where project_id = ?
              and id = ?
            """;

    private static final String LIST_SOURCES_SQL = """
            select id, project_id, type, title, uri, status, failure_stage, error_message,
                   deposited_knowledge_count, created_at, updated_at
            from source_document
            where project_id = ?
            order by updated_at desc, id desc
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String title, String originalFileName, String storagePath) {
        PreparedStatementCreator creator = connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title);
            statement.setString(2, originalFileName);
            statement.setString(3, storagePath);
            statement.setString(4, DocumentStatus.UPLOADED.name());
            return statement;
        };
        PreparedStatementCallback<Long> callback = statement -> {
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new IllegalStateException("Failed to insert research document");
        };
        return jdbcTemplate.execute(creator, callback);
    }

    public void updateStatus(long documentId, DocumentStatus status, FailureStage failureStage, String parseError) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(UPDATE_STATUS_SQL);
            statement.setString(1, status == null ? null : status.name());
            statement.setString(2, failureStage == null ? null : failureStage.name());
            statement.setString(3, parseError);
            statement.setLong(4, documentId);
            return statement;
        });
    }

    public Optional<ResearchDocument> findById(long documentId) {
        List<ResearchDocument> documents = jdbcTemplate.query(FIND_BY_ID_SQL, (resultSet, rowNum) -> new ResearchDocument(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("original_file_name"),
                resultSet.getString("storage_path"),
                DocumentStatus.valueOf(resultSet.getString("status")),
                mapFailureStage(resultSet.getString("failure_stage")),
                resultSet.getString("parse_error"),
                resultSet.getInt("total_chunks"),
                resultSet.getInt("total_tokens"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        ), documentId);
        return documents.stream().findFirst();
    }

    public List<ResearchDocument> findAll() {
        return jdbcTemplate.query(FIND_ALL_SQL, (resultSet, rowNum) -> new ResearchDocument(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("original_file_name"),
                resultSet.getString("storage_path"),
                DocumentStatus.valueOf(resultSet.getString("status")),
                mapFailureStage(resultSet.getString("failure_stage")),
                resultSet.getString("parse_error"),
                resultSet.getInt("total_chunks"),
                resultSet.getInt("total_tokens"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        ));
    }

    public void updateStats(long documentId, int totalChunks, int totalTokens) {
        jdbcTemplate.update(UPDATE_STATS_SQL, totalChunks, totalTokens, documentId);
    }

    public SourceDocument insertSource(String projectId, String type, String title, String uri, String status) {
        String sourceId = UUID.randomUUID().toString();
        jdbcTemplate.update(INSERT_SOURCE_SQL, sourceId, projectId, type, title, uri, status);
        return findSource(projectId, sourceId)
                .orElseThrow(() -> new IllegalStateException("Failed to insert source document"));
    }

    public void updateSourceStatus(
            String projectId,
            String sourceId,
            String status,
            String failureStage,
            String errorMessage) {
        jdbcTemplate.update(UPDATE_SOURCE_STATUS_SQL, status, failureStage, errorMessage, projectId, sourceId);
    }

    public Optional<SourceDocument> findSource(String projectId, String sourceId) {
        return jdbcTemplate.query(FIND_SOURCE_SQL, (resultSet, rowNum) -> mapSource(resultSet), projectId, sourceId)
                .stream()
                .findFirst();
    }

    public List<SourceDocument> listSources(String projectId) {
        return jdbcTemplate.query(LIST_SOURCES_SQL, (resultSet, rowNum) -> mapSource(resultSet), projectId);
    }

    private FailureStage mapFailureStage(String value) {
        return value == null ? null : FailureStage.valueOf(value);
    }

    private SourceDocument mapSource(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new SourceDocument(
                resultSet.getString("id"),
                resultSet.getString("project_id"),
                resultSet.getString("type"),
                resultSet.getString("title"),
                resultSet.getString("uri"),
                resultSet.getString("status"),
                resultSet.getString("failure_stage"),
                resultSet.getString("error_message"),
                resultSet.getInt("deposited_knowledge_count"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

}
