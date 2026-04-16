package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.ingest.model.ResearchDocument;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    private static final String FIND_BY_ID_SQL = """
            select id, title, original_file_name, storage_path, status, failure_stage, parse_error,
                   created_at, updated_at
            from research_document
            where id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String title, String originalFileName, String storagePath) {
        return jdbcTemplate.execute(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title);
            statement.setString(2, originalFileName);
            statement.setString(3, storagePath);
            statement.setString(4, DocumentStatus.UPLOADED.name());
            return statement;
        }, statement -> {
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new IllegalStateException("Failed to insert research document");
        });
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
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        ), documentId);
        return documents.stream().findFirst();
    }

    private FailureStage mapFailureStage(String value) {
        return value == null ? null : FailureStage.valueOf(value);
    }

}
