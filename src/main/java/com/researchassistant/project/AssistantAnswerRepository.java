package com.researchassistant.project;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssistantAnswerRepository {

    private final JdbcTemplate jdbcTemplate;

    public AssistantAnswerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
            String answerId,
            String projectId,
            String sessionId,
            String question,
            String answer,
            String answerMode,
            String evidenceState) {
        insert(
                answerId,
                projectId,
                sessionId,
                null,
                question,
                answer,
                answerMode,
                evidenceState
        );
    }

    public void insert(
            String answerId,
            String projectId,
            String sessionId,
            String runId,
            String question,
            String answer,
            String answerMode,
            String evidenceState) {
        jdbcTemplate.update("""
                insert into assistant_answer(
                    id, project_id, session_id, run_id, question, answer, answer_mode, evidence_state
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                answerId,
                projectId,
                sessionId,
                runId,
                question,
                answer,
                answerMode,
                evidenceState
        );
    }

    public Optional<AssistantAnswerContext> findContext(String projectId, String answerId) {
        return jdbcTemplate.query("""
                select id, session_id, run_id
                from assistant_answer
                where project_id = ?
                  and id = ?
                limit 1
                """, (resultSet, rowNum) -> new AssistantAnswerContext(
                        resultSet.getString("id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("run_id")
                ), projectId, answerId)
                .stream()
                .findFirst();
    }

    public Optional<AssistantAnswerContext> findContextForSessionAnswer(String projectId, String sessionId, String answer) {
        return jdbcTemplate.query("""
                select id, session_id, run_id
                from assistant_answer
                where project_id = ?
                  and session_id = ?
                  and answer = ?
                order by created_at desc
                limit 1
                """, (resultSet, rowNum) -> new AssistantAnswerContext(
                        resultSet.getString("id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("run_id")
                ), projectId, sessionId, answer)
                .stream()
                .findFirst();
    }

    public record AssistantAnswerContext(String answerId, String sessionId, String runId) {
    }
}
