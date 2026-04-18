package com.researchassistant.memory;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long append(long sessionId, String role, String content, String answerMode) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into chat_message(session_id, role, content, answer_mode)
                    values (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, sessionId);
            statement.setString(2, role);
            statement.setString(3, content);
            statement.setString(4, answerMode);
            return statement;
        }, keyHolder);

        Number key = extractGeneratedId(keyHolder);
        if (key == null) {
            throw new IllegalStateException("Failed to create chat message");
        }
        return key.longValue();
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
                select count(*) from chat_message
                where session_id = ?
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    public List<ChatMessageRecord> latestMessages(long sessionId, int limit) {
        return jdbcTemplate.query("""
                select id, session_id, role, content, answer_mode, created_at
                from chat_message
                where session_id = ?
                order by id desc
                limit ?
                """,
                (resultSet, rowNum) -> new ChatMessageRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getString("role"),
                        resultSet.getString("content"),
                        resultSet.getString("answer_mode"),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class)
                ),
                sessionId,
                limit
        );
    }

    public List<ChatMessageRecord> findSinceId(long sessionId, long lastMessageIdExclusive) {
        return jdbcTemplate.query("""
                select id, session_id, role, content, answer_mode, created_at
                from chat_message
                where session_id = ?
                  and id > ?
                order by id asc
                """,
                (resultSet, rowNum) -> new ChatMessageRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getString("role"),
                        resultSet.getString("content"),
                        resultSet.getString("answer_mode"),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class)
                ),
                sessionId,
                lastMessageIdExclusive
        );
    }

    public List<ChatMessageRecord> findBySessionId(long sessionId) {
        return jdbcTemplate.query("""
                select id, session_id, role, content, answer_mode, created_at
                from chat_message
                where session_id = ?
                order by id asc
                """,
                (resultSet, rowNum) -> new ChatMessageRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getString("role"),
                        resultSet.getString("content"),
                        resultSet.getString("answer_mode"),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class)
                ),
                sessionId
        );
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
