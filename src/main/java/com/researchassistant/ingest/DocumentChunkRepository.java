package com.researchassistant.ingest;

import com.researchassistant.ingest.model.ChunkRecord;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DocumentChunkRepository {

    private static final String DELETE_SQL = """
            delete from document_chunk
            where document_id = ?
            """;

    private static final String INSERT_SQL = """
            insert into document_chunk (document_id, chunk_index, content, token_count, metadata_json)
            values (?, ?, ?, ?, cast(? as jsonb))
            """;

    private static final String FIND_BY_DOCUMENT_ID_SQL = """
            select document_id, chunk_index, content, token_count, metadata_json
            from document_chunk
            where document_id = ?
            order by chunk_index
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void replaceChunks(long documentId, List<String> chunks) {
        jdbcTemplate.update(DELETE_SQL, documentId);

        if (chunks.isEmpty()) {
            return;
        }

        List<ChunkRecord> records = new ArrayList<>(chunks.size());
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            String content = chunks.get(chunkIndex);
            int tokenCount = estimateTokenCount(content);
            records.add(new ChunkRecord(documentId, chunkIndex, content, tokenCount, metadataJson(chunkIndex)));
        }

        for (ChunkRecord record : records) {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                statement.setLong(1, record.documentId());
                statement.setInt(2, record.chunkIndex());
                statement.setString(3, record.content());
                statement.setInt(4, record.tokenCount());
                statement.setString(5, record.metadataJson());
                return statement;
            });
        }
    }

    public List<ChunkRecord> findByDocumentId(long documentId) {
        return jdbcTemplate.query(FIND_BY_DOCUMENT_ID_SQL, (resultSet, rowNum) -> new ChunkRecord(
                resultSet.getLong("document_id"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("content"),
                resultSet.getInt("token_count"),
                resultSet.getString("metadata_json")
        ), documentId);
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, (content.length() + 3) / 4);
    }

    private String metadataJson(int chunkIndex) {
        return "{\"chunkIndex\":" + chunkIndex + "}";
    }
}
