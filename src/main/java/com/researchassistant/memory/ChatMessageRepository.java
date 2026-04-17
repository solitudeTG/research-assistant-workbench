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
                select count(*) from chat_message
                where session_id = ?
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
    }
}
