package com.researchassistant.ingest;

import com.researchassistant.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSmokeTest extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void phaseOneTablesArePresent() throws Exception {
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
    }
}
