package com.researchassistant.ingest;

import com.researchassistant.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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

    @Test
    void f007MigrationBackfillsHistoricalEvidenceRowsWithNullQuote() {
        String schema = "migration_smoke_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("create schema " + schema);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target("5")
                    .load()
                    .migrate();

            String projectId = java.util.UUID.randomUUID().toString();
            String answerId = java.util.UUID.randomUUID().toString();
            String evidenceId = java.util.UUID.randomUUID().toString();
            jdbcTemplate.update("set search_path to " + schema);
            jdbcTemplate.update("""
                    insert into research_project(id, topic, summary)
                    values (?, 'Historical project', '')
                    """, projectId);
            jdbcTemplate.update("""
                    insert into assistant_answer(id, project_id, question, answer)
                    values (?, ?, 'q', 'a')
                    """, answerId, projectId);
            jdbcTemplate.update("""
                    insert into evidence_source(id, project_id, answer_id, quote, confidence)
                    values (?, ?, ?, null, null)
                    """, evidenceId, projectId, answerId);
            jdbcTemplate.update("reset search_path");

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    select snippet, strength
                    from %s.evidence_source
                    where id = ?
                    """.formatted(schema), evidenceId);
            assertThat(row)
                    .containsEntry("snippet", "")
                    .containsEntry("strength", "weak");
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void f008MigrationNormalizesLegacyCandidateAndBoardEnumValues() {
        String schema = "migration_smoke_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("create schema " + schema);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target("6")
                    .load()
                    .migrate();

            String projectId = java.util.UUID.randomUUID().toString();
            String candidateId = java.util.UUID.randomUUID().toString();
            String entryId = java.util.UUID.randomUUID().toString();
            jdbcTemplate.update("set search_path to " + schema);
            jdbcTemplate.update("""
                    insert into research_project(id, topic, summary)
                    values (?, 'Historical F008 project', '')
                    """, projectId);
            jdbcTemplate.update("""
                    insert into knowledge_candidate(id, project_id, status, content)
                    values (?, ?, 'draft', 'Legacy candidate content')
                    """, candidateId, projectId);
            jdbcTemplate.update("""
                    insert into knowledge_entry(id, project_id, section, title, content)
                    values (?, ?, 'legacy_notes', 'Legacy entry', 'Legacy content')
                    """, entryId, projectId);
            jdbcTemplate.update("reset search_path");

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            Map<String, Object> candidate = jdbcTemplate.queryForMap("""
                    select status, title, statement, suggested_section
                    from %s.knowledge_candidate
                    where id = ?
                    """.formatted(schema), candidateId);
            assertThat(candidate)
                    .containsEntry("status", "pending")
                    .containsEntry("title", "Legacy candidate content")
                    .containsEntry("statement", "Legacy candidate content")
                    .containsEntry("suggested_section", "confirmed_finding");

            assertThat(jdbcTemplate.queryForObject("""
                    select section
                    from %s.knowledge_entry
                    where id = ?
                    """.formatted(schema), String.class, entryId)).isEqualTo("current_candidates");
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }
}
