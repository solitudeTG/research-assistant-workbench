package com.researchassistant.ingest;

import com.researchassistant.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSmokeTest extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void phaseOneSchemaIsPresent() throws Exception {
        Set<String> tableNames = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
        }

        assertThat(tableNames).contains(
                "research_document",
                "document_chunk",
                "chat_session",
                "chat_message",
                "retrieval_trace"
        );

        assertThat(jdbcTemplate.queryForObject("""
                select exists (
                    select 1
                    from pg_extension
                    where extname = 'vector'
                )
                """, Boolean.class)).isTrue();

        assertThat(jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'document_chunk'
                """, String.class)).contains(
                "idx_document_chunk_document_id",
                "idx_document_chunk_fts",
                "uq_document_chunk_document_id_chunk_index"
        );

        assertThat(jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'chat_message'
                """, String.class)).contains("idx_chat_message_session_id");

        assertThat(jdbcTemplate.queryForList("""
                select tgname
                from pg_trigger
                where not tgisinternal
                  and tgrelid in ('research_document'::regclass, 'chat_session'::regclass)
                """, String.class)).contains(
                "trg_research_document_set_updated_at",
                "trg_chat_session_set_updated_at"
        );
    }
}
