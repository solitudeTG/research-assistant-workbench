package com.researchassistant.memory;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
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
                resultSet -> resultSet.next()
                        ? Optional.of(new WorkingMemory(
                        resultSet.getLong("id"),
                        resultSet.getString("session_key"),
                        resultSet.getString("current_task"),
                        resultSet.getString("rolling_summary"),
                        countMessages(resultSet.getLong("id"))
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

        Number key = extractGeneratedId(keyHolder);
        if (key == null) {
            throw new IllegalStateException("Failed to create chat session");
        }

        return new WorkingMemory(key.longValue(), sessionKey, null, null, 0);
    }

    public void updateSummary(long sessionId, String currentTask, String rollingSummary) {
        jdbcTemplate.update("""
                update chat_session
                set current_task = ?, rolling_summary = ?
                where id = ?
                """, currentTask, rollingSummary, sessionId);
    }

    private int countMessages(long sessionId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from chat_message where session_id = ?
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
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
