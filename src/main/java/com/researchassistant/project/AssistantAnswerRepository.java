package com.researchassistant.project;

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
        jdbcTemplate.update("""
                insert into assistant_answer(
                    id, project_id, session_id, question, answer, answer_mode, evidence_state
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                answerId,
                projectId,
                sessionId,
                question,
                answer,
                answerMode,
                evidenceState
        );
    }
}
