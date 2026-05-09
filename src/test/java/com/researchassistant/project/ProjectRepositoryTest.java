package com.researchassistant.project;

import com.researchassistant.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsProjectAndSessionUnderProject() {
        ProjectRecord project = projectRepository.createProject(
                "Agentic research workbench",
                "A long-running project for grounded research workflows"
        );

        ResearchSessionRecord session = projectRepository.createSession(
                project.id(),
                "Evidence boundary design"
        );

        assertThat(project.id()).isNotBlank();
        assertThat(project.topic()).isEqualTo("Agentic research workbench");
        assertThat(project.summary()).isEqualTo("A long-running project for grounded research workflows");
        assertThat(project.createdAt()).isNotNull();
        assertThat(project.updatedAt()).isNotNull();

        assertThat(session.id()).isNotBlank();
        assertThat(session.projectId()).isEqualTo(project.id());
        assertThat(session.title()).isEqualTo("Evidence boundary design");
        assertThat(session.status()).isEqualTo("continue");
        assertThat(session.lastMessageAt()).isNull();
    }

    @Test
    void listsSessionsWithoutLeakingOtherProjects() {
        ProjectRecord projectA = projectRepository.createProject("Project A", "First research area");
        ProjectRecord projectB = projectRepository.createProject("Project B", "Second research area");

        ResearchSessionRecord sessionA = projectRepository.createSession(projectA.id(), "Project A session");
        ResearchSessionRecord sessionB = projectRepository.createSession(projectB.id(), "Project B session");

        assertThat(projectRepository.listSessions(projectA.id()))
                .extracting(ResearchSessionRecord::id)
                .contains(sessionA.id())
                .doesNotContain(sessionB.id());

        assertThat(projectRepository.listSessions(projectB.id()))
                .extracting(ResearchSessionRecord::id)
                .contains(sessionB.id())
                .doesNotContain(sessionA.id());
    }

    @Test
    void rejectsCrossProjectRelationshipsAtDatabaseBoundary() {
        ProjectRecord projectA = projectRepository.createProject("Project A", "First research area");
        ProjectRecord projectB = projectRepository.createProject("Project B", "Second research area");

        ResearchSessionRecord sessionA = projectRepository.createSession(projectA.id(), "Project A session");
        ResearchSessionRecord sessionB = projectRepository.createSession(projectB.id(), "Project B session");
        String answerA = insertAnswer(projectA.id(), sessionA.id());
        String answerB = insertAnswer(projectB.id(), sessionB.id());
        String sourceB = insertSource(projectB.id());
        String candidateB = insertKnowledgeCandidate(projectB.id(), null, null, null);

        assertThatThrownBy(() -> insertAnswer(projectA.id(), sessionB.id()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertEvidenceSource(projectA.id(), answerA, sourceB))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertKnowledgeCandidate(projectA.id(), sessionB.id(), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertKnowledgeCandidate(projectA.id(), null, answerB, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertKnowledgeCandidate(projectA.id(), null, null, sourceB))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertKnowledgeEntry(projectA.id(), candidateB))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertAnswerFeedback(projectB.id(), answerA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String insertAnswer(String projectId, String sessionId) {
        String answerId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into assistant_answer(id, project_id, session_id, question, answer, answer_mode, evidence_state)
                values (?, ?, ?, 'question', 'answer', 'chat', 'pending')
                """, answerId, projectId, sessionId);
        return answerId;
    }

    private String insertSource(String projectId) {
        String sourceId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into source_document(id, project_id, type, title, status)
                values (?, ?, 'url', 'Source', 'ready')
                """, sourceId, projectId);
        return sourceId;
    }

    private void insertEvidenceSource(String projectId, String answerId, String sourceId) {
        jdbcTemplate.update("""
                insert into evidence_source(id, project_id, answer_id, source_id, quote, confidence)
                values (?, ?, ?, ?, 'quote', 'medium')
                """, UUID.randomUUID().toString(), projectId, answerId, sourceId);
    }

    private void insertAnswerFeedback(String projectId, String answerId) {
        jdbcTemplate.update("""
                insert into answer_feedback(id, project_id, answer_id, feedback_score, note)
                values (?, ?, ?, 1, 'helpful')
                """, UUID.randomUUID().toString(), projectId, answerId);
    }

    private String insertKnowledgeCandidate(String projectId, String sessionId, String answerId, String sourceId) {
        String candidateId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into knowledge_candidate(id, project_id, session_id, answer_id, source_id, content)
                values (?, ?, ?, ?, ?, 'candidate')
                """, candidateId, projectId, sessionId, answerId, sourceId);
        return candidateId;
    }

    private void insertKnowledgeEntry(String projectId, String candidateId) {
        jdbcTemplate.update("""
                insert into knowledge_entry(id, project_id, candidate_id, title, content)
                values (?, ?, ?, 'Entry', 'content')
                """, UUID.randomUUID().toString(), projectId, candidateId);
    }
}
