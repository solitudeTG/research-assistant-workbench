package com.researchassistant.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatSessionRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatSessionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public WorkingMemory findOrCreate(String sessionKey) {
        Optional<WorkingMemory> existing = findBySessionKey(sessionKey);
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

        Number key = extractGeneratedId(keyHolder);
        if (key == null) {
            throw new IllegalStateException("Failed to create chat session");
        }

        return new WorkingMemory(
                key.longValue(),
                sessionKey,
                null,
                null,
                List.of(),
                List.of(),
                0L,
                null,
                0
        );
    }

    public Optional<WorkingMemory> findBySessionKey(String sessionKey) {
        return jdbcTemplate.query("""
                        select id, session_key, current_task, rolling_summary,
                               salient_facts_json, compressed_rounds_json,
                               last_deposited_message_id, last_deposit_at
                        from chat_session
                        where session_key = ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new WorkingMemory(
                        resultSet.getLong("id"),
                        resultSet.getString("session_key"),
                        resultSet.getString("current_task"),
                        resultSet.getString("rolling_summary"),
                        fromJson(resultSet.getString("salient_facts_json")),
                        fromJson(resultSet.getString("compressed_rounds_json")),
                        resultSet.getLong("last_deposited_message_id"),
                        resultSet.getObject("last_deposit_at", OffsetDateTime.class),
                        countMessages(resultSet.getLong("id"))
                ))
                        : Optional.empty(),
                sessionKey
        );
    }

    public List<WorkingMemory> listSessions() {
        return jdbcTemplate.query("""
                select id, session_key, current_task, rolling_summary,
                       salient_facts_json, compressed_rounds_json,
                       last_deposited_message_id, last_deposit_at
                from chat_session
                order by updated_at desc, id desc
                """,
                (resultSet, rowNum) -> new WorkingMemory(
                        resultSet.getLong("id"),
                        resultSet.getString("session_key"),
                        resultSet.getString("current_task"),
                        resultSet.getString("rolling_summary"),
                        fromJson(resultSet.getString("salient_facts_json")),
                        fromJson(resultSet.getString("compressed_rounds_json")),
                        resultSet.getLong("last_deposited_message_id"),
                        resultSet.getObject("last_deposit_at", OffsetDateTime.class),
                        countMessages(resultSet.getLong("id"))
                )
        );
    }

    public void saveWorkingMemory(long sessionId,
                                  String currentTask,
                                  String rollingSummary,
                                  List<String> salientFacts,
                                  List<String> compressedRounds) {
        jdbcTemplate.update("""
                update chat_session
                set current_task = ?,
                    rolling_summary = ?,
                    salient_facts_json = ?::jsonb,
                    compressed_rounds_json = ?::jsonb
                where id = ?
                """,
                currentTask,
                rollingSummary,
                toJson(salientFacts),
                toJson(compressedRounds),
                sessionId
        );
    }

    public void updateDepositWatermark(long sessionId, long lastDepositedMessageId, OffsetDateTime lastDepositAt) {
        jdbcTemplate.update("""
                update chat_session
                set last_deposited_message_id = ?,
                    last_deposit_at = ?
                where id = ?
                """,
                lastDepositedMessageId,
                lastDepositAt,
                sessionId
        );
    }

    private int countMessages(long sessionId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from chat_message where session_id = ?
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat session memory", e);
        }
    }

    private List<String> fromJson(String values) {
        try {
            return values == null || values.isBlank() ? List.of() : objectMapper.readValue(values, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize chat session memory", e);
        }
    }

    private Number extractGeneratedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            Object id = keys.get("id");
            if (id instanceof Number number) {
                return number;
            }
            if (keys.size() == 1) {
                Object onlyValue = keys.values().iterator().next();
                if (onlyValue instanceof Number number) {
                    return number;
                }
            }
        }
        return keyHolder.getKey();
    }
}
