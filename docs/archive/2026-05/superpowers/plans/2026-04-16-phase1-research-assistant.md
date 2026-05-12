---
id: ARCHIVE-PLAN-2026-04-16-PHASE1-RESEARCH-ASSISTANT
doc_kind: plan
status: archived
archived: 2026-05-10
feature_ids: []
---
# Research Assistant Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Phase 1 research assistant that supports paper upload, parsing, indexing, Paper RAG question answering, evidence-boundary-based responses, session-level L1 memory, and HTTP/SSE interaction.

**Architecture:** Start with a single Spring Boot application and modular package boundaries (`chat / orchestrator / ingest / rag / evidence / memory / common`). Keep one `SupervisorService` as the orchestration center, persist chunk/source metadata in PostgreSQL, store vectors in pgvector-backed Spring AI vector storage, and preserve explicit extension seams for later `plan-execute`, `L2/L3`, memory recall, and feedback.

**Tech Stack:** Java 17, Spring Boot 3.5.13, Spring AI 1.1.2, Spring AI Alibaba DashScope starter 1.1.2.1, PostgreSQL + pgvector, Flyway, Spring JDBC, PDFBox, Testcontainers, JUnit 5, SSE, static HTML/JS.

---

If this folder is still a plain directory, run `git init` once before the first commit step so the commit commands below work.

## Planned File Structure

- `pom.xml`
  Maven build, dependency management, plugins.
- `compose.yaml`
  Local pgvector database for manual development.
- `src/main/java/com/researchassistant/ResearchAssistantApplication.java`
  Spring Boot entry point.
- `src/main/java/com/researchassistant/common/config/AsyncConfig.java`
  Async executor for indexing jobs.
- `src/main/java/com/researchassistant/common/config/AiConfig.java`
  Shared AI beans and clients.
- `src/main/java/com/researchassistant/common/storage/FileStoragePort.java`
  Storage abstraction.
- `src/main/java/com/researchassistant/common/storage/LocalFileStorage.java`
  Local-disk file storage implementation.
- `src/main/java/com/researchassistant/chat/ChatController.java`
  Synchronous chat endpoint.
- `src/main/java/com/researchassistant/chat/ChatStreamController.java`
  SSE chat endpoint.
- `src/main/java/com/researchassistant/chat/dto/ChatRequest.java`
  Chat request payload.
- `src/main/java/com/researchassistant/chat/dto/ChatResponse.java`
  Chat response payload.
- `src/main/java/com/researchassistant/chat/dto/CitationDto.java`
  Citation payload returned to the UI.
- `src/main/java/com/researchassistant/orchestrator/RetrievalMode.java`
  Retrieval route enum.
- `src/main/java/com/researchassistant/orchestrator/TaskRouter.java`
  Route selection between `NO_RETRIEVAL` and `PAPER_RAG_ONLY`.
- `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
  Main request orchestration service.
- `src/main/java/com/researchassistant/orchestrator/PlanExecuteFacade.java`
  Phase 1 interface-only extension seam.
- `src/main/java/com/researchassistant/orchestrator/MemoryRecallPort.java`
  Phase 1 interface-only extension seam.
- `src/main/java/com/researchassistant/orchestrator/FeedbackPort.java`
  Phase 1 interface-only extension seam.
- `src/main/java/com/researchassistant/ingest/DocumentController.java`
  Upload and document status endpoints.
- `src/main/java/com/researchassistant/ingest/DocumentIngestService.java`
  Register uploads and trigger processing jobs.
- `src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java`
  Async parse/chunk/index pipeline.
- `src/main/java/com/researchassistant/ingest/DocumentRepository.java`
  JDBC repository for documents.
- `src/main/java/com/researchassistant/ingest/DocumentChunkRepository.java`
  JDBC repository for chunk persistence and keyword search source rows.
- `src/main/java/com/researchassistant/ingest/PdfTextExtractor.java`
  PDF parsing.
- `src/main/java/com/researchassistant/ingest/OverlapTextChunker.java`
  Simple overlap-aware chunker.
- `src/main/java/com/researchassistant/ingest/model/DocumentStatus.java`
  Upload/index lifecycle enum.
- `src/main/java/com/researchassistant/ingest/model/FailureStage.java`
  Failure stage enum.
- `src/main/java/com/researchassistant/ingest/model/ResearchDocument.java`
  Document aggregate.
- `src/main/java/com/researchassistant/ingest/model/ChunkRecord.java`
  Stored chunk row model.
- `src/main/java/com/researchassistant/rag/KeywordSearchRepository.java`
  PostgreSQL full-text search adapter.
- `src/main/java/com/researchassistant/rag/VectorSearchPort.java`
  Vector lookup abstraction.
- `src/main/java/com/researchassistant/rag/PgVectorSearchPort.java`
  Spring AI pgvector implementation.
- `src/main/java/com/researchassistant/rag/PaperRagService.java`
  Hybrid retrieval + rerank + trace.
- `src/main/java/com/researchassistant/rag/RagChunk.java`
  Unified retrieval item.
- `src/main/java/com/researchassistant/rag/RagResult.java`
  Retrieval result aggregate.
- `src/main/java/com/researchassistant/rag/RetrievalTraceRepository.java`
  Trace persistence.
- `src/main/java/com/researchassistant/evidence/EvidenceLevel.java`
  `SUFFICIENT / WEAK / NONE`.
- `src/main/java/com/researchassistant/evidence/AnswerMode.java`
  `LOCAL_EVIDENCE / LOCAL_WEAK_EVIDENCE / REFUSAL`.
- `src/main/java/com/researchassistant/evidence/EvidenceBoundaryService.java`
  Explicit evidence scoring and output mode selection.
- `src/main/java/com/researchassistant/memory/ChatSessionRepository.java`
  Session persistence.
- `src/main/java/com/researchassistant/memory/ChatMessageRepository.java`
  Message persistence.
- `src/main/java/com/researchassistant/memory/WorkingMemory.java`
  Session-level L1 working memory object.
- `src/main/java/com/researchassistant/memory/WorkingMemoryService.java`
  L1 load/update logic.
- `src/main/resources/application.yml`
  Shared runtime configuration.
- `src/main/resources/db/migration/V1__phase1_schema.sql`
  Core Phase 1 schema.
- `src/main/resources/static/index.html`
  Minimal UI.
- `src/test/java/com/researchassistant/support/PostgresIntegrationTest.java`
  Shared pgvector Testcontainers bootstrap.
- `src/test/java/...`
  Unit, integration, and happy-path tests.

### Task 1: Bootstrap the Spring Boot Skeleton

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/researchassistant/ResearchAssistantApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/researchassistant/chat/SystemController.java`
- Test: `src/test/java/com/researchassistant/chat/SystemControllerTest.java`

- [ ] **Step 1: Create the minimal Maven build and app entry point**

```xml
<!-- pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.13</version>
        <relativePath/>
    </parent>

    <groupId>com.researchassistant</groupId>
    <artifactId>research-assistant-phase1</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>research-assistant-phase1</name>
    <description>Phase 1 runnable research assistant</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

```java
// src/main/java/com/researchassistant/ResearchAssistantApplication.java
package com.researchassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResearchAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchAssistantApplication.class, args);
    }
}
```

```yaml
# src/main/resources/application.yml
server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info

app:
  storage:
    root: ./storage
```

- [ ] **Step 2: Write the failing health endpoint test**

```java
// src/test/java/com/researchassistant/chat/SystemControllerTest.java
package com.researchassistant.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsOkPayload() throws Exception {
        mockMvc.perform(get("/api/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q -Dtest=SystemControllerTest test`

Expected: FAIL with `Status expected:<200> but was:<404>`

- [ ] **Step 4: Implement the controller**

```java
// src/main/java/com/researchassistant/chat/SystemController.java
package com.researchassistant.chat;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=SystemControllerTest test`

Expected: PASS with `Tests run: 1, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/researchassistant/ResearchAssistantApplication.java src/main/resources/application.yml src/main/java/com/researchassistant/chat/SystemController.java src/test/java/com/researchassistant/chat/SystemControllerTest.java
git commit -m "feat: bootstrap spring application skeleton"
```

### Task 2: Add PostgreSQL, Flyway, and the Core Phase 1 Schema

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `compose.yaml`
- Create: `src/main/resources/db/migration/V1__phase1_schema.sql`
- Create: `src/test/java/com/researchassistant/support/PostgresIntegrationTest.java`
- Test: `src/test/java/com/researchassistant/ingest/SchemaSmokeTest.java`

- [ ] **Step 1: Write the failing schema smoke test**

```java
// src/test/java/com/researchassistant/support/PostgresIntegrationTest.java
package com.researchassistant.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17"))
                    .withDatabaseName("research_assistant")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

```java
// src/test/java/com/researchassistant/ingest/SchemaSmokeTest.java
package com.researchassistant.ingest;

import com.researchassistant.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSmokeTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void phaseOneTablesArePresent() {
        List<String> tableNames = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                """, String.class);

        assertThat(tableNames).contains(
                "research_document",
                "document_chunk",
                "chat_session",
                "chat_message",
                "retrieval_trace"
        );
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=SchemaSmokeTest test`

Expected: FAIL with missing `JdbcTemplate`/datasource configuration or missing tables

- [ ] **Step 3: Add DB dependencies, config, local compose, and the initial migration**

```xml
<!-- Add to pom.xml dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

```yaml
# compose.yaml
services:
  db:
    image: pgvector/pgvector:pg17
    container_name: research-assistant-db
    environment:
      POSTGRES_DB: research_assistant
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d research_assistant"]
      interval: 5s
      timeout: 5s
      retries: 20
```

```yaml
# src/main/resources/application.yml
server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:research_assistant}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  storage:
    root: ./storage
```

```sql
-- src/main/resources/db/migration/V1__phase1_schema.sql
create extension if not exists vector;

create table if not exists research_document (
    id bigserial primary key,
    title varchar(500) not null,
    original_file_name varchar(500) not null,
    storage_path varchar(1000) not null,
    status varchar(32) not null,
    failure_stage varchar(32),
    parse_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists document_chunk (
    id bigserial primary key,
    document_id bigint not null references research_document(id) on delete cascade,
    chunk_index integer not null,
    content text not null,
    token_count integer not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_document_chunk_document_id on document_chunk(document_id);
create index if not exists idx_document_chunk_fts
    on document_chunk using gin (to_tsvector('simple', content));

create table if not exists chat_session (
    id bigserial primary key,
    session_key varchar(128) not null unique,
    current_task text,
    rolling_summary text,
    salient_facts_json jsonb not null default '[]'::jsonb,
    compressed_rounds_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists chat_message (
    id bigserial primary key,
    session_id bigint not null references chat_session(id) on delete cascade,
    role varchar(32) not null,
    content text not null,
    answer_mode varchar(32),
    created_at timestamptz not null default now()
);

create index if not exists idx_chat_message_session_id on chat_message(session_id);

create table if not exists retrieval_trace (
    id bigserial primary key,
    session_id bigint references chat_session(id) on delete set null,
    query_text text not null,
    filters_json jsonb not null default '{}'::jsonb,
    top_chunks_json jsonb not null default '[]'::jsonb,
    rerank_result_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now()
);
```

- [ ] **Step 4: Run the schema test to verify it passes**

Run: `mvn -q -Dtest=SchemaSmokeTest test`

Expected: PASS with all five tables present

- [ ] **Step 5: Commit**

```bash
git add pom.xml compose.yaml src/main/resources/application.yml src/main/resources/db/migration/V1__phase1_schema.sql src/test/java/com/researchassistant/support/PostgresIntegrationTest.java src/test/java/com/researchassistant/ingest/SchemaSmokeTest.java
git commit -m "feat: add postgres and phase1 schema"
```

### Task 3: Implement Upload Registration and Local File Storage

**Files:**
- Create: `src/main/java/com/researchassistant/common/storage/FileStoragePort.java`
- Create: `src/main/java/com/researchassistant/common/storage/LocalFileStorage.java`
- Create: `src/main/java/com/researchassistant/ingest/model/DocumentStatus.java`
- Create: `src/main/java/com/researchassistant/ingest/model/FailureStage.java`
- Create: `src/main/java/com/researchassistant/ingest/model/ResearchDocument.java`
- Create: `src/main/java/com/researchassistant/ingest/DocumentRepository.java`
- Create: `src/main/java/com/researchassistant/ingest/DocumentIngestService.java`
- Create: `src/main/java/com/researchassistant/ingest/DocumentController.java`
- Test: `src/test/java/com/researchassistant/ingest/DocumentControllerTest.java`

- [ ] **Step 1: Write the failing upload controller test**

```java
// src/test/java/com/researchassistant/ingest/DocumentControllerTest.java
package com.researchassistant.ingest;

import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class DocumentControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadReturnsAcceptedDocumentRecord() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "paper.txt",
                "text/plain",
                "transformer architecture summary".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.documentId").isNumber());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=DocumentControllerTest test`

Expected: FAIL with `404` for `/api/documents/upload`

- [ ] **Step 3: Implement file storage, document repository, and upload registration**

```java
// src/main/java/com/researchassistant/common/storage/FileStoragePort.java
package com.researchassistant.common.storage;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface FileStoragePort {

    String save(MultipartFile file) throws IOException;

    Path resolve(String storagePath);
}
```

```java
// src/main/java/com/researchassistant/common/storage/LocalFileStorage.java
package com.researchassistant.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileStorage implements FileStoragePort {

    private final Path rootDirectory;

    public LocalFileStorage(@Value("${app.storage.root}") String rootDirectory) {
        this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String save(MultipartFile file) throws IOException {
        Files.createDirectories(rootDirectory.resolve("uploads"));
        String fileName = UUID.randomUUID() + "-" + file.getOriginalFilename();
        Path target = rootDirectory.resolve("uploads").resolve(fileName);
        file.transferTo(target);
        return target.toString();
    }

    @Override
    public Path resolve(String storagePath) {
        return Path.of(storagePath);
    }
}
```

```java
// src/main/java/com/researchassistant/ingest/model/DocumentStatus.java
package com.researchassistant.ingest.model;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    INDEXING,
    INDEXED,
    FAILED
}
```

```java
// src/main/java/com/researchassistant/ingest/model/FailureStage.java
package com.researchassistant.ingest.model;

public enum FailureStage {
    STORAGE,
    PARSING,
    INDEXING
}
```

```java
// src/main/java/com/researchassistant/ingest/model/ResearchDocument.java
package com.researchassistant.ingest.model;

import java.time.OffsetDateTime;

public record ResearchDocument(
        long id,
        String title,
        String originalFileName,
        String storagePath,
        DocumentStatus status,
        FailureStage failureStage,
        String parseError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
```

```java
// src/main/java/com/researchassistant/ingest/DocumentRepository.java
package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.ingest.model.ResearchDocument;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String title, String originalFileName, String storagePath) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into research_document(title, original_file_name, storage_path, status)
                    values (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title);
            statement.setString(2, originalFileName);
            statement.setString(3, storagePath);
            statement.setString(4, DocumentStatus.UPLOADED.name());
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateStatus(long documentId, DocumentStatus status, FailureStage failureStage, String parseError) {
        jdbcTemplate.update("""
                update research_document
                set status = ?, failure_stage = ?, parse_error = ?, updated_at = now()
                where id = ?
                """,
                status.name(),
                failureStage == null ? null : failureStage.name(),
                parseError,
                documentId
        );
    }

    public Optional<ResearchDocument> findById(long documentId) {
        return jdbcTemplate.query("""
                        select id, title, original_file_name, storage_path, status, failure_stage, parse_error, created_at, updated_at
                        from research_document
                        where id = ?
                        """,
                rs -> rs.next()
                        ? Optional.of(new ResearchDocument(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("original_file_name"),
                        rs.getString("storage_path"),
                        DocumentStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_stage") == null ? null : FailureStage.valueOf(rs.getString("failure_stage")),
                        rs.getString("parse_error"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class),
                        rs.getObject("updated_at", java.time.OffsetDateTime.class)
                ))
                        : Optional.empty(),
                documentId
        );
    }
}
```

```java
// src/main/java/com/researchassistant/ingest/DocumentIngestService.java
package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestService {

    private final FileStoragePort fileStoragePort;
    private final DocumentRepository documentRepository;

    public DocumentIngestService(FileStoragePort fileStoragePort, DocumentRepository documentRepository) {
        this.fileStoragePort = fileStoragePort;
        this.documentRepository = documentRepository;
    }

    public Map<String, Object> registerUpload(MultipartFile file) throws IOException {
        String storagePath = fileStoragePort.save(file);
        long documentId = documentRepository.insert(file.getOriginalFilename(), file.getOriginalFilename(), storagePath);
        return Map.of(
                "documentId", documentId,
                "status", "UPLOADED",
                "title", file.getOriginalFilename()
        );
    }
}
```

```java
// src/main/java/com/researchassistant/ingest/DocumentController.java
package com.researchassistant.ingest;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestService documentIngestService;

    public DocumentController(DocumentIngestService documentIngestService) {
        this.documentIngestService = documentIngestService;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        return documentIngestService.registerUpload(file);
    }
}
```

- [ ] **Step 4: Run the upload test to verify it passes**

Run: `mvn -q -Dtest=DocumentControllerTest test`

Expected: PASS with `status=UPLOADED` and numeric `documentId`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/researchassistant/common/storage/FileStoragePort.java src/main/java/com/researchassistant/common/storage/LocalFileStorage.java src/main/java/com/researchassistant/ingest/model/DocumentStatus.java src/main/java/com/researchassistant/ingest/model/FailureStage.java src/main/java/com/researchassistant/ingest/model/ResearchDocument.java src/main/java/com/researchassistant/ingest/DocumentRepository.java src/main/java/com/researchassistant/ingest/DocumentIngestService.java src/main/java/com/researchassistant/ingest/DocumentController.java src/test/java/com/researchassistant/ingest/DocumentControllerTest.java
git commit -m "feat: register uploads and persist document records"
```

### Task 4: Parse PDFs, Create Chunks, and Complete the Async Indexing Pipeline

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/researchassistant/common/config/AsyncConfig.java`
- Create: `src/main/java/com/researchassistant/ingest/model/ChunkRecord.java`
- Create: `src/main/java/com/researchassistant/ingest/DocumentChunkRepository.java`
- Create: `src/main/java/com/researchassistant/ingest/PdfTextExtractor.java`
- Create: `src/main/java/com/researchassistant/ingest/OverlapTextChunker.java`
- Create: `src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java`
- Modify: `src/main/java/com/researchassistant/ingest/DocumentIngestService.java`
- Test: `src/test/java/com/researchassistant/ingest/DocumentProcessingJobTest.java`

- [ ] **Step 1: Write the failing document processing test**

```java
// src/test/java/com/researchassistant/ingest/DocumentProcessingJobTest.java
package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.support.PostgresIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.storage.root=target/test-storage")
class DocumentProcessingJobTest extends PostgresIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentProcessingJob documentProcessingJob;

    @Test
    void processDocumentCreatesChunksAndMarksDocumentIndexed() throws Exception {
        Path pdfPath = Path.of("target/test-storage/manual.pdf");
        Files.createDirectories(pdfPath.getParent());
        writePdf(pdfPath, "Attention is all you need. Multi-head attention improves sequence modeling.");

        long documentId = documentRepository.insert("manual.pdf", "manual.pdf", pdfPath.toString());

        documentProcessingJob.processDocument(documentId).join();

        assertThat(documentRepository.findById(documentId)).get()
                .extracting(document -> document.status())
                .isEqualTo(DocumentStatus.INDEXED);
        assertThat(documentChunkRepository.findByDocumentId(documentId)).isNotEmpty();
    }

    private void writePdf(Path path, String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            try (PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(0))) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(path.toFile());
        }
    }
}
```

- [ ] **Step 2: Run the processing test to verify it fails**

Run: `mvn -q -Dtest=DocumentProcessingJobTest test`

Expected: FAIL because `DocumentProcessingJob` / `DocumentChunkRepository` / PDF parsing code does not exist

- [ ] **Step 3: Add PDFBox, async config, chunk persistence, and the processing job**

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.5</version>
</dependency>
```

```java
// src/main/java/com/researchassistant/common/config/AsyncConfig.java
package com.researchassistant.common.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "indexingExecutor")
    public Executor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("indexing-");
        executor.initialize();
        return executor;
    }
}
```

```java
// src/main/java/com/researchassistant/ingest/model/ChunkRecord.java
package com.researchassistant.ingest.model;

public record ChunkRecord(
        long id,
        long documentId,
        int chunkIndex,
        String content,
        int tokenCount,
        String metadataJson
) {
}
```

```java
// src/main/java/com/researchassistant/ingest/DocumentChunkRepository.java
package com.researchassistant.ingest;

import com.researchassistant.ingest.model.ChunkRecord;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceChunks(long documentId, List<String> chunks) {
        jdbcTemplate.update("delete from document_chunk where document_id = ?", documentId);
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            jdbcTemplate.update("""
                    insert into document_chunk(document_id, chunk_index, content, token_count, metadata_json)
                    values (?, ?, ?, ?, ?::jsonb)
                    """,
                    documentId,
                    i,
                    content,
                    estimateTokenCount(content),
                    "{\"chunkIndex\":" + i + "}"
            );
        }
    }

    public List<ChunkRecord> findByDocumentId(long documentId) {
        return jdbcTemplate.query("""
                        select id, document_id, chunk_index, content, token_count, metadata_json::text as metadata_json
                        from document_chunk
                        where document_id = ?
                        order by chunk_index asc
                        """,
                (rs, rowNum) -> new ChunkRecord(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getInt("token_count"),
                        rs.getString("metadata_json")
                ),
                documentId
        );
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, content.length() / 4);
    }
}
```

```java
// src/main/java/com/researchassistant/ingest/PdfTextExtractor.java
package com.researchassistant.ingest;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    public String extract(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document).replace('\u0000', ' ').trim();
        }
    }
}
```

```java
// src/main/java/com/researchassistant/ingest/OverlapTextChunker.java
package com.researchassistant.ingest;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OverlapTextChunker {

    private static final int MAX_CHARS = 1200;
    private static final int OVERLAP_CHARS = 200;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return chunks;
        }

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHARS, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(0, end - OVERLAP_CHARS);
        }
        return chunks;
    }
}
```

```java
// src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java
package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingJob {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final OverlapTextChunker overlapTextChunker;

    public DocumentProcessingJob(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            PdfTextExtractor pdfTextExtractor,
            OverlapTextChunker overlapTextChunker
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.overlapTextChunker = overlapTextChunker;
    }

    @Async("indexingExecutor")
    public CompletableFuture<Void> processDocument(long documentId) {
        try {
            documentRepository.updateStatus(documentId, DocumentStatus.PARSING, null, null);
            var document = documentRepository.findById(documentId).orElseThrow();
            String text = pdfTextExtractor.extract(java.nio.file.Path.of(document.storagePath()));
            List<String> chunks = overlapTextChunker.chunk(text);

            documentRepository.updateStatus(documentId, DocumentStatus.INDEXING, null, null);
            documentChunkRepository.replaceChunks(documentId, chunks);
            documentRepository.updateStatus(documentId, DocumentStatus.INDEXED, null, null);
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            documentRepository.updateStatus(documentId, DocumentStatus.FAILED, FailureStage.PARSING, exception.getMessage());
            return CompletableFuture.failedFuture(exception);
        }
    }
}
```

```java
// Modify src/main/java/com/researchassistant/ingest/DocumentIngestService.java
package com.researchassistant.ingest;

import com.researchassistant.common.storage.FileStoragePort;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestService {

    private final FileStoragePort fileStoragePort;
    private final DocumentRepository documentRepository;
    private final DocumentProcessingJob documentProcessingJob;

    public DocumentIngestService(
            FileStoragePort fileStoragePort,
            DocumentRepository documentRepository,
            DocumentProcessingJob documentProcessingJob
    ) {
        this.fileStoragePort = fileStoragePort;
        this.documentRepository = documentRepository;
        this.documentProcessingJob = documentProcessingJob;
    }

    public Map<String, Object> registerUpload(MultipartFile file) throws IOException {
        String storagePath = fileStoragePort.save(file);
        long documentId = documentRepository.insert(file.getOriginalFilename(), file.getOriginalFilename(), storagePath);
        documentProcessingJob.processDocument(documentId);
        return Map.of(
                "documentId", documentId,
                "status", "UPLOADED",
                "title", file.getOriginalFilename()
        );
    }
}
```

- [ ] **Step 4: Run the processing test to verify it passes**

Run: `mvn -q -Dtest=DocumentProcessingJobTest test`

Expected: PASS with document status `INDEXED` and one or more saved chunks

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/researchassistant/common/config/AsyncConfig.java src/main/java/com/researchassistant/ingest/model/ChunkRecord.java src/main/java/com/researchassistant/ingest/DocumentChunkRepository.java src/main/java/com/researchassistant/ingest/PdfTextExtractor.java src/main/java/com/researchassistant/ingest/OverlapTextChunker.java src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java src/main/java/com/researchassistant/ingest/DocumentIngestService.java src/test/java/com/researchassistant/ingest/DocumentProcessingJobTest.java
git commit -m "feat: parse and chunk uploaded documents"
```

### Task 5: Add Hybrid Paper RAG and Retrieval Trace Persistence

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/researchassistant/common/config/AiConfig.java`
- Create: `src/main/java/com/researchassistant/rag/VectorSearchPort.java`
- Create: `src/main/java/com/researchassistant/rag/PgVectorSearchPort.java`
- Create: `src/main/java/com/researchassistant/rag/KeywordSearchRepository.java`
- Create: `src/main/java/com/researchassistant/rag/RagChunk.java`
- Create: `src/main/java/com/researchassistant/rag/RagResult.java`
- Create: `src/main/java/com/researchassistant/rag/RetrievalTraceRepository.java`
- Create: `src/main/java/com/researchassistant/rag/PaperRagService.java`
- Modify: `src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java`
- Test: `src/test/java/com/researchassistant/rag/PaperRagServiceTest.java`

- [ ] **Step 1: Write the failing Paper RAG test**

```java
// src/test/java/com/researchassistant/rag/PaperRagServiceTest.java
package com.researchassistant.rag;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaperRagServiceTest {

    @Test
    void hybridRetrievalMergesKeywordAndVectorSignals() {
        KeywordSearchRepository keywordSearchRepository = new KeywordSearchRepositoryStub(
                List.of(new RagChunk(11L, 1L, 0, "Attention improves sequence modeling", 0.75))
        );
        VectorSearchPort vectorSearchPort = new VectorSearchPort() {
            @Override
            public void reindexDocument(long documentId, List<RagChunk> chunks) {
            }

            @Override
            public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
                return List.of(new RagChunk(11L, 1L, 0, "Attention improves sequence modeling", 0.82));
            }
        };

        RetrievalTraceRepository traceRepository = new RetrievalTraceRepositoryStub();
        PaperRagService paperRagService = new PaperRagService(keywordSearchRepository, vectorSearchPort, traceRepository);

        RagResult result = paperRagService.retrieve(42L, "How does attention help sequence modeling?", List.of(1L), 5);

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).finalScore()).isGreaterThan(0.80);
    }

    private static class KeywordSearchRepositoryStub extends KeywordSearchRepository {
        private final List<RagChunk> chunks;

        KeywordSearchRepositoryStub(List<RagChunk> chunks) {
            super(null);
            this.chunks = chunks;
        }

        @Override
        public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
            return chunks;
        }
    }

    private static class RetrievalTraceRepositoryStub extends RetrievalTraceRepository {
        RetrievalTraceRepositoryStub() {
            super(null);
        }

        @Override
        public void save(long sessionId, String query, String filtersJson, String topChunksJson, String rerankResultJson) {
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=PaperRagServiceTest test`

Expected: FAIL because the RAG classes do not exist

- [ ] **Step 3: Add Spring AI dependencies, vector indexing, keyword search, and hybrid merge**

```xml
<!-- Add to pom.xml -->
<properties>
    <java.version>17</java.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-ai-alibaba.version>1.1.2.1</spring-ai-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
        <version>${spring-ai-alibaba.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    </dependency>
</dependencies>
```

```yaml
# Add to src/main/resources/application.yml
spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY:dummy-key}
      chat:
        options:
          model: qwen-max
      embedding:
        options:
          model: text-embedding-v3
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1536
        table-name: vector_store
```

```java
// src/main/java/com/researchassistant/common/config/AiConfig.java
package com.researchassistant.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
```

```java
// src/main/java/com/researchassistant/rag/RagChunk.java
package com.researchassistant.rag;

public record RagChunk(
        long chunkId,
        long documentId,
        int chunkIndex,
        String content,
        double finalScore
) {
}
```

```java
// src/main/java/com/researchassistant/rag/RagResult.java
package com.researchassistant.rag;

import java.util.List;

public record RagResult(
        String query,
        List<Long> allowedDocumentIds,
        List<RagChunk> chunks
) {
}
```

```java
// src/main/java/com/researchassistant/rag/VectorSearchPort.java
package com.researchassistant.rag;

import java.util.List;

public interface VectorSearchPort {

    void reindexDocument(long documentId, List<RagChunk> chunks);

    List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit);
}
```

```java
// src/main/java/com/researchassistant/rag/PgVectorSearchPort.java
package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class PgVectorSearchPort implements VectorSearchPort {

    private final VectorStore vectorStore;

    public PgVectorSearchPort(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void reindexDocument(long documentId, List<RagChunk> chunks) {
        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(
                        chunk.content(),
                        Map.of(
                                "documentId", Long.toString(chunk.documentId()),
                                "chunkId", Long.toString(chunk.chunkId()),
                                "chunkIndex", Integer.toString(chunk.chunkIndex())
                        )
                ))
                .toList();
        vectorStore.add(documents);
    }

    @Override
    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(limit * 3).build())
                .stream()
                .map(document -> new RagChunk(
                        Long.parseLong(document.getMetadata().get("chunkId").toString()),
                        Long.parseLong(document.getMetadata().get("documentId").toString()),
                        Integer.parseInt(document.getMetadata().get("chunkIndex").toString()),
                        document.getText(),
                        document.getScore() == null ? 0.0 : document.getScore()
                ))
                .filter(chunk -> allowedDocumentIds == null || allowedDocumentIds.isEmpty() || allowedDocumentIds.contains(chunk.documentId()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
```

```java
// src/main/java/com/researchassistant/rag/KeywordSearchRepository.java
package com.researchassistant.rag;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KeywordSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public KeywordSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RagChunk> search(String query, List<Long> allowedDocumentIds, int limit) {
        String sql = """
                select id, document_id, chunk_index, content,
                       ts_rank(to_tsvector('simple', content), websearch_to_tsquery('simple', :query)) as score
                from document_chunk
                where (:documentIdsEmpty = true or document_id in (:documentIds))
                  and to_tsvector('simple', content) @@ websearch_to_tsquery('simple', :query)
                order by score desc
                limit :limit
                """;

        Map<String, Object> params = Map.of(
                "query", query,
                "documentIdsEmpty", allowedDocumentIds == null || allowedDocumentIds.isEmpty(),
                "documentIds", allowedDocumentIds == null || allowedDocumentIds.isEmpty() ? List.of(-1L) : allowedDocumentIds,
                "limit", limit
        );

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new RagChunk(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("score")
        ));
    }
}
```

```java
// src/main/java/com/researchassistant/rag/RetrievalTraceRepository.java
package com.researchassistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RetrievalTraceRepository {

    private final JdbcTemplate jdbcTemplate;

    public RetrievalTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(long sessionId, String query, String filtersJson, String topChunksJson, String rerankResultJson) {
        jdbcTemplate.update("""
                insert into retrieval_trace(session_id, query_text, filters_json, top_chunks_json, rerank_result_json)
                values (?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                """,
                sessionId,
                query,
                filtersJson,
                topChunksJson,
                rerankResultJson
        );
    }
}
```

```java
// src/main/java/com/researchassistant/rag/PaperRagService.java
package com.researchassistant.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaperRagService {

    private final KeywordSearchRepository keywordSearchRepository;
    private final VectorSearchPort vectorSearchPort;
    private final RetrievalTraceRepository retrievalTraceRepository;

    public PaperRagService(
            KeywordSearchRepository keywordSearchRepository,
            VectorSearchPort vectorSearchPort,
            RetrievalTraceRepository retrievalTraceRepository
    ) {
        this.keywordSearchRepository = keywordSearchRepository;
        this.vectorSearchPort = vectorSearchPort;
        this.retrievalTraceRepository = retrievalTraceRepository;
    }

    public RagResult retrieve(long sessionId, String query, List<Long> allowedDocumentIds, int limit) {
        List<RagChunk> keywordHits = keywordSearchRepository.search(query, allowedDocumentIds, limit);
        List<RagChunk> vectorHits = vectorSearchPort.search(query, allowedDocumentIds, limit);

        Map<Long, RagChunk> merged = new LinkedHashMap<>();
        mergeInto(merged, keywordHits, 0.45);
        mergeInto(merged, vectorHits, 0.55);

        List<RagChunk> reranked = new ArrayList<>(merged.values());
        reranked.sort(Comparator.comparingDouble(RagChunk::finalScore).reversed());
        if (reranked.size() > limit) {
            reranked = reranked.subList(0, limit);
        }

        retrievalTraceRepository.save(
                sessionId,
                query,
                "{\"documentIds\":" + (allowedDocumentIds == null ? "[]" : allowedDocumentIds.toString()) + "}",
                toChunkJson(keywordHits),
                toChunkJson(reranked)
        );

        return new RagResult(query, allowedDocumentIds, reranked);
    }

    private void mergeInto(Map<Long, RagChunk> merged, List<RagChunk> chunks, double weight) {
        for (RagChunk chunk : chunks) {
            merged.merge(
                    chunk.chunkId(),
                    new RagChunk(chunk.chunkId(), chunk.documentId(), chunk.chunkIndex(), chunk.content(), chunk.finalScore() * weight),
                    (existing, incoming) -> new RagChunk(
                            existing.chunkId(),
                            existing.documentId(),
                            existing.chunkIndex(),
                            existing.content(),
                            existing.finalScore() + incoming.finalScore()
                    )
            );
        }
    }

    private String toChunkJson(List<RagChunk> chunks) {
        return chunks.stream()
                .map(chunk -> "{\"chunkId\":" + chunk.chunkId() + ",\"documentId\":" + chunk.documentId() + ",\"score\":" + chunk.finalScore() + "}")
                .reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]")
                .orElse("[]");
    }
}
```

```java
// Modify src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java
package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.ingest.model.FailureStage;
import com.researchassistant.rag.PgVectorSearchPort;
import com.researchassistant.rag.RagChunk;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingJob {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final OverlapTextChunker overlapTextChunker;
    private final PgVectorSearchPort vectorSearchPort;

    public DocumentProcessingJob(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            PdfTextExtractor pdfTextExtractor,
            OverlapTextChunker overlapTextChunker,
            PgVectorSearchPort vectorSearchPort
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.overlapTextChunker = overlapTextChunker;
        this.vectorSearchPort = vectorSearchPort;
    }

    @Async("indexingExecutor")
    public CompletableFuture<Void> processDocument(long documentId) {
        try {
            documentRepository.updateStatus(documentId, DocumentStatus.PARSING, null, null);
            var document = documentRepository.findById(documentId).orElseThrow();
            String text = pdfTextExtractor.extract(java.nio.file.Path.of(document.storagePath()));
            List<String> chunks = overlapTextChunker.chunk(text);

            documentRepository.updateStatus(documentId, DocumentStatus.INDEXING, null, null);
            documentChunkRepository.replaceChunks(documentId, chunks);

            List<RagChunk> ragChunks = documentChunkRepository.findByDocumentId(documentId).stream()
                    .map(row -> new RagChunk(row.id(), row.documentId(), row.chunkIndex(), row.content(), 0.0))
                    .toList();
            vectorSearchPort.reindexDocument(documentId, ragChunks);

            documentRepository.updateStatus(documentId, DocumentStatus.INDEXED, null, null);
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            documentRepository.updateStatus(documentId, DocumentStatus.FAILED, FailureStage.INDEXING, exception.getMessage());
            return CompletableFuture.failedFuture(exception);
        }
    }
}
```

- [ ] **Step 4: Run the Paper RAG test to verify it passes**

Run: `mvn -q -Dtest=PaperRagServiceTest test`

Expected: PASS with a merged retrieval score greater than `0.80`

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/java/com/researchassistant/common/config/AiConfig.java src/main/java/com/researchassistant/rag/VectorSearchPort.java src/main/java/com/researchassistant/rag/PgVectorSearchPort.java src/main/java/com/researchassistant/rag/KeywordSearchRepository.java src/main/java/com/researchassistant/rag/RagChunk.java src/main/java/com/researchassistant/rag/RagResult.java src/main/java/com/researchassistant/rag/RetrievalTraceRepository.java src/main/java/com/researchassistant/rag/PaperRagService.java src/main/java/com/researchassistant/ingest/DocumentProcessingJob.java src/test/java/com/researchassistant/rag/PaperRagServiceTest.java
git commit -m "feat: add hybrid paper rag and retrieval trace"
```

### Task 6: Implement L1 Working Memory and Session Persistence

**Files:**
- Create: `src/main/java/com/researchassistant/memory/WorkingMemory.java`
- Create: `src/main/java/com/researchassistant/memory/ChatSessionRepository.java`
- Create: `src/main/java/com/researchassistant/memory/ChatMessageRepository.java`
- Create: `src/main/java/com/researchassistant/memory/WorkingMemoryService.java`
- Test: `src/test/java/com/researchassistant/memory/WorkingMemoryServiceTest.java`

- [ ] **Step 1: Write the failing working memory test**

```java
// src/test/java/com/researchassistant/memory/WorkingMemoryServiceTest.java
package com.researchassistant.memory;

import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingMemoryServiceTest extends PostgresIntegrationTest {

    @Autowired
    private WorkingMemoryService workingMemoryService;

    @Test
    void updateMemoryTracksCurrentTaskAndSummary() {
        workingMemoryService.appendExchange("session-1", "Summarize attention", "Attention uses weighted context.", "LOCAL_EVIDENCE");
        WorkingMemory second = workingMemoryService.appendExchange("session-1", "What is multi-head attention?", "It splits projections across heads.", "LOCAL_EVIDENCE");

        assertThat(second.currentTask()).isEqualTo("What is multi-head attention?");
        assertThat(second.rollingSummary()).contains("multi-head attention");
        assertThat(second.messageCount()).isEqualTo(4);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=WorkingMemoryServiceTest test`

Expected: FAIL because the memory classes do not exist

- [ ] **Step 3: Implement session/message repositories and working memory service**

```java
// src/main/java/com/researchassistant/memory/WorkingMemory.java
package com.researchassistant.memory;

public record WorkingMemory(
        long sessionId,
        String sessionKey,
        String currentTask,
        String rollingSummary,
        int messageCount
) {
}
```

```java
// src/main/java/com/researchassistant/memory/ChatSessionRepository.java
package com.researchassistant.memory;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WorkingMemory findOrCreate(String sessionKey) {
        Optional<WorkingMemory> existing = jdbcTemplate.query("""
                        select id, session_key, current_task, rolling_summary
                        from chat_session
                        where session_key = ?
                        """,
                rs -> rs.next()
                        ? Optional.of(new WorkingMemory(
                        rs.getLong("id"),
                        rs.getString("session_key"),
                        rs.getString("current_task"),
                        rs.getString("rolling_summary"),
                        countMessages(rs.getLong("id"))
                ))
                        : Optional.empty(),
                sessionKey
        );

        if (existing.isPresent()) {
            return existing.get();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into chat_session(session_key)
                    values (?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, sessionKey);
            return statement;
        }, keyHolder);

        long sessionId = keyHolder.getKey().longValue();
        return new WorkingMemory(sessionId, sessionKey, null, null, 0);
    }

    public void updateSummary(long sessionId, String currentTask, String rollingSummary) {
        jdbcTemplate.update("""
                update chat_session
                set current_task = ?, rolling_summary = ?, updated_at = now()
                where id = ?
                """, currentTask, rollingSummary, sessionId);
    }

    private int countMessages(long sessionId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from chat_message where session_id = ?
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
    }
}
```

```java
// src/main/java/com/researchassistant/memory/ChatMessageRepository.java
package com.researchassistant.memory;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(long sessionId, String role, String content, String answerMode) {
        jdbcTemplate.update("""
                insert into chat_message(session_id, role, content, answer_mode)
                values (?, ?, ?, ?)
                """, sessionId, role, content, answerMode);
    }

    public List<String> latestContents(long sessionId, int limit) {
        return jdbcTemplate.queryForList("""
                select content
                from chat_message
                where session_id = ?
                order by id desc
                limit ?
                """, String.class, sessionId, limit);
    }

    public int count(long sessionId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from chat_message where session_id = ?
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
    }
}
```

```java
// src/main/java/com/researchassistant/memory/WorkingMemoryService.java
package com.researchassistant.memory;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkingMemoryService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public WorkingMemoryService(ChatSessionRepository chatSessionRepository, ChatMessageRepository chatMessageRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public WorkingMemory appendExchange(String sessionKey, String userQuestion, String assistantAnswer, String answerMode) {
        WorkingMemory current = chatSessionRepository.findOrCreate(sessionKey);
        chatMessageRepository.append(current.sessionId(), "USER", userQuestion, null);
        chatMessageRepository.append(current.sessionId(), "ASSISTANT", assistantAnswer, answerMode);

        List<String> latest = chatMessageRepository.latestContents(current.sessionId(), 4);
        Collections.reverse(latest);
        String summary = String.join(" | ", latest);

        chatSessionRepository.updateSummary(current.sessionId(), userQuestion, summary);
        return new WorkingMemory(
                current.sessionId(),
                sessionKey,
                userQuestion,
                summary,
                chatMessageRepository.count(current.sessionId())
        );
    }

    public WorkingMemory load(String sessionKey) {
        return chatSessionRepository.findOrCreate(sessionKey);
    }
}
```

- [ ] **Step 4: Run the working memory test to verify it passes**

Run: `mvn -q -Dtest=WorkingMemoryServiceTest test`

Expected: PASS with `currentTask` matching the latest user request and `messageCount=4`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/researchassistant/memory/WorkingMemory.java src/main/java/com/researchassistant/memory/ChatSessionRepository.java src/main/java/com/researchassistant/memory/ChatMessageRepository.java src/main/java/com/researchassistant/memory/WorkingMemoryService.java src/test/java/com/researchassistant/memory/WorkingMemoryServiceTest.java
git commit -m "feat: add l1 working memory persistence"
```

### Task 7: Implement Evidence Boundary, Supervisor Orchestration, and the Chat API

**Files:**
- Create: `src/main/java/com/researchassistant/evidence/EvidenceLevel.java`
- Create: `src/main/java/com/researchassistant/evidence/AnswerMode.java`
- Create: `src/main/java/com/researchassistant/evidence/EvidenceBoundaryService.java`
- Create: `src/main/java/com/researchassistant/orchestrator/RetrievalMode.java`
- Create: `src/main/java/com/researchassistant/orchestrator/TaskRouter.java`
- Create: `src/main/java/com/researchassistant/orchestrator/PlanExecuteFacade.java`
- Create: `src/main/java/com/researchassistant/orchestrator/MemoryRecallPort.java`
- Create: `src/main/java/com/researchassistant/orchestrator/FeedbackPort.java`
- Create: `src/main/java/com/researchassistant/orchestrator/SupervisorService.java`
- Create: `src/main/java/com/researchassistant/chat/dto/ChatRequest.java`
- Create: `src/main/java/com/researchassistant/chat/dto/CitationDto.java`
- Create: `src/main/java/com/researchassistant/chat/dto/ChatResponse.java`
- Create: `src/main/java/com/researchassistant/chat/ChatController.java`
- Test: `src/test/java/com/researchassistant/chat/ChatControllerTest.java`

- [ ] **Step 1: Write the failing chat controller integration test**

```java
// src/test/java/com/researchassistant/chat/ChatControllerTest.java
package com.researchassistant.chat;

import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class ChatControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedChunks() {
        jdbcTemplate.update("insert into research_document(title, original_file_name, storage_path, status) values ('paper.pdf', 'paper.pdf', 'ignored', 'INDEXED')");
        jdbcTemplate.update("""
                insert into document_chunk(document_id, chunk_index, content, token_count, metadata_json)
                values (1, 0, 'Attention computes weighted token interactions for sequence modeling.', 12, '{}'::jsonb)
                """);
    }

    @Test
    void chatReturnsEvidenceBackedAnswerWithCitations() throws Exception {
        String requestBody = """
                {
                  "sessionKey": "session-42",
                  "question": "What does attention do?",
                  "documentIds": [1]
                }
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerMode").value("LOCAL_EVIDENCE"))
                .andExpect(jsonPath("$.citations[0].documentId").value(1));
    }
}
```

- [ ] **Step 2: Run the chat test to verify it fails**

Run: `mvn -q -Dtest=ChatControllerTest test`

Expected: FAIL because the orchestration and chat classes do not exist

- [ ] **Step 3: Implement evidence scoring, route selection, supervisor orchestration, and the chat endpoint**

```java
// src/main/java/com/researchassistant/evidence/EvidenceLevel.java
package com.researchassistant.evidence;

public enum EvidenceLevel {
    SUFFICIENT,
    WEAK,
    NONE
}
```

```java
// src/main/java/com/researchassistant/evidence/AnswerMode.java
package com.researchassistant.evidence;

public enum AnswerMode {
    LOCAL_EVIDENCE,
    LOCAL_WEAK_EVIDENCE,
    REFUSAL
}
```

```java
// src/main/java/com/researchassistant/evidence/EvidenceBoundaryService.java
package com.researchassistant.evidence;

import com.researchassistant.rag.RagResult;
import org.springframework.stereotype.Service;

@Service
public class EvidenceBoundaryService {

    public EvidenceLevel assess(RagResult ragResult) {
        if (ragResult.chunks().isEmpty()) {
            return EvidenceLevel.NONE;
        }

        double topScore = ragResult.chunks().get(0).finalScore();
        if (topScore >= 0.75 && ragResult.chunks().size() >= 1) {
            return EvidenceLevel.SUFFICIENT;
        }
        if (topScore >= 0.35) {
            return EvidenceLevel.WEAK;
        }
        return EvidenceLevel.NONE;
    }

    public AnswerMode toAnswerMode(EvidenceLevel evidenceLevel) {
        return switch (evidenceLevel) {
            case SUFFICIENT -> AnswerMode.LOCAL_EVIDENCE;
            case WEAK -> AnswerMode.LOCAL_WEAK_EVIDENCE;
            case NONE -> AnswerMode.REFUSAL;
        };
    }
}
```

```java
// src/main/java/com/researchassistant/orchestrator/RetrievalMode.java
package com.researchassistant.orchestrator;

public enum RetrievalMode {
    NO_RETRIEVAL,
    PAPER_RAG_ONLY
}
```

```java
// src/main/java/com/researchassistant/orchestrator/TaskRouter.java
package com.researchassistant.orchestrator;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskRouter {

    public RetrievalMode route(String question, List<Long> documentIds) {
        if (documentIds != null && !documentIds.isEmpty()) {
            return RetrievalMode.PAPER_RAG_ONLY;
        }

        String normalized = question.toLowerCase();
        if (normalized.contains("paper") || normalized.contains("attention") || normalized.contains("method")) {
            return RetrievalMode.PAPER_RAG_ONLY;
        }
        return RetrievalMode.NO_RETRIEVAL;
    }
}
```

```java
// src/main/java/com/researchassistant/orchestrator/PlanExecuteFacade.java
package com.researchassistant.orchestrator;

public interface PlanExecuteFacade {
}
```

```java
// src/main/java/com/researchassistant/orchestrator/MemoryRecallPort.java
package com.researchassistant.orchestrator;

public interface MemoryRecallPort {
}
```

```java
// src/main/java/com/researchassistant/orchestrator/FeedbackPort.java
package com.researchassistant.orchestrator;

public interface FeedbackPort {
}
```

```java
// src/main/java/com/researchassistant/chat/dto/ChatRequest.java
package com.researchassistant.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChatRequest(
        @NotBlank String sessionKey,
        @NotBlank String question,
        List<Long> documentIds
) {
}
```

```java
// src/main/java/com/researchassistant/chat/dto/CitationDto.java
package com.researchassistant.chat.dto;

public record CitationDto(
        long chunkId,
        long documentId,
        int chunkIndex,
        String excerpt
) {
}
```

```java
// src/main/java/com/researchassistant/chat/dto/ChatResponse.java
package com.researchassistant.chat.dto;

import java.util.List;

public record ChatResponse(
        String sessionKey,
        String answerMode,
        String answer,
        List<CitationDto> citations
) {
}
```

```java
// src/main/java/com/researchassistant/orchestrator/SupervisorService.java
package com.researchassistant.orchestrator;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.chat.dto.CitationDto;
import com.researchassistant.evidence.AnswerMode;
import com.researchassistant.evidence.EvidenceBoundaryService;
import com.researchassistant.memory.WorkingMemoryService;
import com.researchassistant.rag.PaperRagService;
import com.researchassistant.rag.RagResult;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SupervisorService {

    private final TaskRouter taskRouter;
    private final PaperRagService paperRagService;
    private final EvidenceBoundaryService evidenceBoundaryService;
    private final WorkingMemoryService workingMemoryService;
    private final ChatClient chatClient;

    public SupervisorService(
            TaskRouter taskRouter,
            PaperRagService paperRagService,
            EvidenceBoundaryService evidenceBoundaryService,
            WorkingMemoryService workingMemoryService,
            ChatClient chatClient
    ) {
        this.taskRouter = taskRouter;
        this.paperRagService = paperRagService;
        this.evidenceBoundaryService = evidenceBoundaryService;
        this.workingMemoryService = workingMemoryService;
        this.chatClient = chatClient;
    }

    public ChatResponse answer(ChatRequest request) {
        var memory = workingMemoryService.load(request.sessionKey());
        RetrievalMode retrievalMode = taskRouter.route(request.question(), request.documentIds());

        if (retrievalMode == RetrievalMode.NO_RETRIEVAL) {
            String answer = "I need indexed local papers or a paper-grounded question to answer reliably.";
            workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, AnswerMode.LOCAL_WEAK_EVIDENCE.name());
            return new ChatResponse(request.sessionKey(), AnswerMode.LOCAL_WEAK_EVIDENCE.name(), answer, List.of());
        }

        RagResult ragResult = paperRagService.retrieve(memory.sessionId(), request.question(), request.documentIds(), 5);
        AnswerMode answerMode = evidenceBoundaryService.toAnswerMode(evidenceBoundaryService.assess(ragResult));

        String answer;
        if (answerMode == AnswerMode.REFUSAL) {
            answer = "Local indexed evidence is not strong enough for a grounded answer yet.";
        } else {
            String context = ragResult.chunks().stream()
                    .map(chunk -> "[doc=" + chunk.documentId() + ",chunk=" + chunk.chunkIndex() + "] " + chunk.content())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            answer = chatClient.prompt()
                    .system("Answer only from the provided context. If the context is weak, stay conservative.")
                    .user("Question: " + request.question() + "\n\nContext:\n" + context)
                    .call()
                    .content();
        }

        workingMemoryService.appendExchange(request.sessionKey(), request.question(), answer, answerMode.name());

        List<CitationDto> citations = ragResult.chunks().stream()
                .map(chunk -> new CitationDto(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.content().substring(0, Math.min(160, chunk.content().length()))
                ))
                .toList();

        return new ChatResponse(request.sessionKey(), answerMode.name(), answer, citations);
    }
}
```

```java
// src/main/java/com/researchassistant/chat/ChatController.java
package com.researchassistant.chat;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.orchestrator.SupervisorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final SupervisorService supervisorService;

    public ChatController(SupervisorService supervisorService) {
        this.supervisorService = supervisorService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return supervisorService.answer(request);
    }
}
```

- [ ] **Step 4: Run the chat controller test to verify it passes**

Run: `mvn -q -Dtest=ChatControllerTest test`

Expected: PASS with `answerMode=LOCAL_EVIDENCE` and at least one citation

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/researchassistant/evidence/EvidenceLevel.java src/main/java/com/researchassistant/evidence/AnswerMode.java src/main/java/com/researchassistant/evidence/EvidenceBoundaryService.java src/main/java/com/researchassistant/orchestrator/RetrievalMode.java src/main/java/com/researchassistant/orchestrator/TaskRouter.java src/main/java/com/researchassistant/orchestrator/PlanExecuteFacade.java src/main/java/com/researchassistant/orchestrator/MemoryRecallPort.java src/main/java/com/researchassistant/orchestrator/FeedbackPort.java src/main/java/com/researchassistant/orchestrator/SupervisorService.java src/main/java/com/researchassistant/chat/dto/ChatRequest.java src/main/java/com/researchassistant/chat/dto/CitationDto.java src/main/java/com/researchassistant/chat/dto/ChatResponse.java src/main/java/com/researchassistant/chat/ChatController.java src/test/java/com/researchassistant/chat/ChatControllerTest.java
git commit -m "feat: add supervisor orchestration and chat api"
```

### Task 8: Add SSE Streaming and a Minimal Web UI

**Files:**
- Create: `src/main/java/com/researchassistant/chat/ChatStreamController.java`
- Create: `src/main/resources/static/index.html`
- Test: `src/test/java/com/researchassistant/chat/ChatStreamControllerTest.java`

- [ ] **Step 1: Write the failing SSE test**

```java
// src/test/java/com/researchassistant/chat/ChatStreamControllerTest.java
package com.researchassistant.chat;

import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ChatStreamControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void streamEndpointUsesEventStreamContentType() throws Exception {
        mockMvc.perform(get("/api/chat/stream")
                        .queryParam("sessionKey", "sse-1")
                        .queryParam("question", "hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ChatStreamControllerTest test`

Expected: FAIL because `/api/chat/stream` does not exist

- [ ] **Step 3: Implement a simple tokenized SSE endpoint and the minimal UI**

```java
// src/main/java/com/researchassistant/chat/ChatStreamController.java
package com.researchassistant.chat;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.orchestrator.SupervisorService;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private final SupervisorService supervisorService;

    public ChatStreamController(SupervisorService supervisorService) {
        this.supervisorService = supervisorService;
    }

    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestParam String sessionKey,
            @RequestParam String question,
            @RequestParam(required = false) Long documentId
    ) {
        SseEmitter emitter = new SseEmitter(30_000L);

        Thread.startVirtualThread(() -> {
            try {
                ChatResponse response = supervisorService.answer(new ChatRequest(
                        sessionKey,
                        question,
                        documentId == null ? Collections.emptyList() : List.of(documentId)
                ));
                for (String token : Arrays.asList(response.answer().split(" "))) {
                    emitter.send(SseEmitter.event().name("message").data(token));
                }
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            }
        });

        return emitter;
    }
}
```

```html
<!-- src/main/resources/static/index.html -->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Research Assistant Phase 1</title>
    <style>
        body { font-family: Georgia, serif; max-width: 880px; margin: 40px auto; line-height: 1.5; }
        textarea, input, button { width: 100%; margin-top: 8px; padding: 10px; }
        #answer { white-space: pre-wrap; border: 1px solid #ddd; padding: 16px; min-height: 120px; margin-top: 16px; }
        #citations { margin-top: 16px; }
    </style>
</head>
<body>
<h1>Research Assistant Phase 1</h1>

<label>Upload a paper</label>
<input id="file" type="file">
<button id="upload">Upload</button>

<label>Document ID</label>
<input id="documentId" type="text">

<label>Question</label>
<textarea id="question" rows="4"></textarea>
<button id="ask">Ask via SSE</button>

<div id="answer"></div>
<ul id="citations"></ul>

<script>
    const uploadButton = document.getElementById('upload');
    const askButton = document.getElementById('ask');
    const answer = document.getElementById('answer');
    const citations = document.getElementById('citations');
    const documentIdInput = document.getElementById('documentId');

    uploadButton.addEventListener('click', async () => {
        const fileInput = document.getElementById('file');
        if (!fileInput.files.length) return;
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        const response = await fetch('/api/documents/upload', { method: 'POST', body: formData });
        const payload = await response.json();
        documentIdInput.value = payload.documentId;
    });

    askButton.addEventListener('click', () => {
        answer.textContent = '';
        citations.innerHTML = '';
        const question = document.getElementById('question').value;
        const documentId = documentIdInput.value;
        const url = `/api/chat/stream?sessionKey=browser-session&question=${encodeURIComponent(question)}&documentId=${encodeURIComponent(documentId)}`;
        const source = new EventSource(url);

        source.addEventListener('message', (event) => {
            answer.textContent += `${event.data} `;
        });

        source.addEventListener('done', (event) => {
            const payload = JSON.parse(event.data);
            payload.citations.forEach((citation) => {
                const li = document.createElement('li');
                li.textContent = `doc ${citation.documentId}, chunk ${citation.chunkIndex}: ${citation.excerpt}`;
                citations.appendChild(li);
            });
            source.close();
        });
    });
</script>
</body>
</html>
```

- [ ] **Step 4: Run the SSE test to verify it passes**

Run: `mvn -q -Dtest=ChatStreamControllerTest test`

Expected: PASS with `text/event-stream` response type

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/researchassistant/chat/ChatStreamController.java src/main/resources/static/index.html src/test/java/com/researchassistant/chat/ChatStreamControllerTest.java
git commit -m "feat: add sse chat stream and minimal web ui"
```

### Task 9: Add the Phase 1 Happy Path Test and Run Full Verification

**Files:**
- Create: `README.md`
- Create: `.env.example`
- Test: `src/test/java/com/researchassistant/Phase1HappyPathTest.java`

- [ ] **Step 1: Write the failing happy-path integration test**

```java
// src/test/java/com/researchassistant/Phase1HappyPathTest.java
package com.researchassistant;

import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.root=target/test-storage")
class Phase1HappyPathTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void uploadThenAskGroundedQuestion() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "paper.txt",
                "text/plain",
                "Attention computes weighted token interactions. Multi-head attention improves representation capacity."
                        .getBytes()
        );

        String body = mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long documentId = Long.parseLong(body.replaceAll(".*\"documentId\":(\\d+).*", "$1"));

        for (int i = 0; i < 25; i++) {
            var document = documentRepository.findById(documentId).orElseThrow();
            if (document.status() == DocumentStatus.INDEXED) {
                break;
            }
            Thread.sleep(200);
        }

        assertThat(documentRepository.findById(documentId)).get()
                .extracting(document -> document.status())
                .isEqualTo(DocumentStatus.INDEXED);

        String requestBody = """
                {
                  "sessionKey": "happy-path",
                  "question": "What does multi-head attention improve?",
                  "documentIds": [%d]
                }
                """.formatted(documentId);

        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].documentId").value(documentId));
    }
}
```

- [ ] **Step 2: Run the happy-path test to verify it fails**

Run: `mvn -q -Dtest=Phase1HappyPathTest test`

Expected: FAIL because at least one link in the upload-to-chat pipeline is still incomplete

- [ ] **Step 3: Implement any missing glue and add developer docs**

```dotenv
# .env.example
DB_HOST=localhost
DB_PORT=5432
DB_NAME=research_assistant
DB_USERNAME=postgres
DB_PASSWORD=postgres
AI_DASHSCOPE_API_KEY=replace-with-your-real-key
```

```md
# README.md

## Research Assistant Phase 1

### Start the database

```bash
docker compose up -d db
```

### Export environment variables

```bash
$env:AI_DASHSCOPE_API_KEY="your-real-key"
```

### Run the app

```bash
mvn spring-boot:run
```

### Open the UI

Visit `http://localhost:8080`.

### What Phase 1 supports

- upload a local paper
- parse and chunk it
- index it into PostgreSQL + pgvector
- ask a grounded question
- receive an answer with citations
- keep session-level L1 working memory
```

- [ ] **Step 4: Run the full verification suite**

Run: `mvn -q test`

Expected: PASS with `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add README.md .env.example src/test/java/com/researchassistant/Phase1HappyPathTest.java
git commit -m "test: add phase1 happy path verification"
```

## Self-Review

### Spec Coverage

- `single Supervisor` is implemented in Task 7.
- `upload state machine` is covered in Tasks 3 and 4.
- `parse / chunk / index` is covered in Task 4 and completed in Task 5.
- `Paper RAG hybrid retrieval` is covered in Task 5.
- `evidence boundary` is covered in Task 7.
- `L1 working memory` is covered in Task 6.
- `REST + SSE + minimal UI` is covered in Tasks 7 and 8.
- `Phase 2 seams` are covered in Task 7 interface files.

### Placeholder Scan

- No unfinished marker words remain in the plan body.
- Every task includes concrete file paths, commands, and expected outcomes.

### Type Consistency

- `DocumentStatus` uses `UPLOADED / PARSING / INDEXING / INDEXED / FAILED` consistently.
- `AnswerMode` uses `LOCAL_EVIDENCE / LOCAL_WEAK_EVIDENCE / REFUSAL` consistently.
- `RetrievalMode` uses `NO_RETRIEVAL / PAPER_RAG_ONLY` consistently.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-16-phase1-research-assistant.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
