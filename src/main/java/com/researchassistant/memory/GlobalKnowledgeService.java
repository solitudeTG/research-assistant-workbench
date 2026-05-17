package com.researchassistant.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GlobalKnowledgeService {

    private final JdbcTemplate jdbcTemplate;
    private final Path memoryRoot;

    public GlobalKnowledgeService(JdbcTemplate jdbcTemplate,
                                  @Value("${app.storage.root}") String storageRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryRoot = Paths.get(storageRoot).toAbsolutePath().normalize().resolve("memory");
    }

    public void append(GlobalKnowledgeNoteType noteType, String entry) {
        String trimmedEntry = entry == null ? "" : entry.trim();
        if (trimmedEntry.isBlank()) {
            return;
        }

        String existing = load(noteType);
        String updated = existing.isBlank() ? "- " + trimmedEntry : existing.stripTrailing() + System.lineSeparator() + "- " + trimmedEntry;
        jdbcTemplate.update("""
                insert into global_knowledge_note(note_type, content)
                values (?, ?)
                on conflict (note_type)
                do update set content = excluded.content, updated_at = now()
                """, noteType.name(), updated);
        writeFile(noteType, updated);
    }

    public void set(GlobalKnowledgeNoteType noteType, String content) {
        String normalizedContent = content == null ? "" : content;
        jdbcTemplate.update("""
                insert into global_knowledge_note(note_type, content)
                values (?, ?)
                on conflict (note_type)
                do update set content = excluded.content, updated_at = now()
                """, noteType.name(), normalizedContent);
        writeFile(noteType, normalizedContent);
    }

    public GlobalKnowledgeSnapshot snapshot() {
        Map<GlobalKnowledgeNoteType, String> values = new EnumMap<>(GlobalKnowledgeNoteType.class);
        for (GlobalKnowledgeNoteType noteType : GlobalKnowledgeNoteType.values()) {
            values.put(noteType, load(noteType));
        }
        return new GlobalKnowledgeSnapshot(
                values.get(GlobalKnowledgeNoteType.USER),
                values.get(GlobalKnowledgeNoteType.SOUL),
                values.get(GlobalKnowledgeNoteType.RESEARCH_STATE)
        );
    }

    public void appendDailyMemory(MemoryEntry entry) {
        String block = "## " + OffsetDateTime.now() + System.lineSeparator()
                + "- Topic: " + entry.topic() + System.lineSeparator()
                + "- Summary: " + entry.summary() + System.lineSeparator()
                + "- Findings: " + String.join("; ", entry.keyFindings()) + System.lineSeparator()
                + "- Open Questions: " + String.join("; ", entry.openQuestions()) + System.lineSeparator()
                + "- Keywords: " + String.join(", ", entry.keywords()) + System.lineSeparator();
        writeFile("每日记忆.md", readFile("每日记忆.md").stripTrailing() + System.lineSeparator() + System.lineSeparator() + block);
    }

    private String load(GlobalKnowledgeNoteType noteType) {
        String content = jdbcTemplate.query("""
                        select content
                        from global_knowledge_note
                        where note_type = ?
                        """,
                resultSet -> resultSet.next() ? resultSet.getString("content") : null,
                noteType.name()
        );
        if (content != null) {
            writeFile(noteType, content);
            return content;
        }
        return readFile(noteType.fileName());
    }

    private void writeFile(GlobalKnowledgeNoteType noteType, String content) {
        writeFile(noteType.fileName(), content);
    }

    private void writeFile(String fileName, String content) {
        try {
            Files.createDirectories(memoryRoot);
            Files.writeString(memoryRoot.resolve(fileName), content == null ? "" : content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist memory file", e);
        }
    }

    private String readFile(String fileName) {
        Path file = memoryRoot.resolve(fileName);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read memory file", e);
        }
    }
}
