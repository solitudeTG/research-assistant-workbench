package com.researchassistant.feedback;

import com.researchassistant.evidence.EvidenceSourceRecord;
import com.researchassistant.evidence.EvidenceSourceRepository;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import com.researchassistant.ingest.DocumentChunkRepository;
import com.researchassistant.ingest.DocumentRepository;
import com.researchassistant.ingest.model.ChunkRecord;
import com.researchassistant.project.AssistantAnswerRepository;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ProjectRecord;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.rag.RagChunk;
import com.researchassistant.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class ProjectFeedbackServiceTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AssistantAnswerRepository assistantAnswerRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private EvidenceSourceRepository evidenceSourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private WorkbenchEventPublisher eventPublisher;

    @Test
    void upFeedbackIncreasesOnlySameProjectLinkedEvidenceAndChunkScoresAndPublishesEvent() {
        FeedbackFixture target = createFixture("target project");
        FeedbackFixture otherProject = createFixture("other project");

        ProjectAnswerFeedbackResult result = feedbackService.recordProjectAnswerFeedback(
                target.projectId(),
                target.answerId(),
                new ProjectAnswerFeedbackRequest("up", null, "helpful", List.of(
                        target.evidenceId(),
                        otherProject.evidenceId()
                ))
        );

        assertThat(result.rating()).isEqualTo("up");
        assertThat(result.updatedEvidenceSourceCount()).isEqualTo(1);
        assertThat(result.updatedChunkCount()).isEqualTo(1);
        assertThat(answerFeedbackScores(target.answerId())).containsExactly(1);
        assertThat(answerFeedbackNote(target.answerId())).isEqualTo("helpful");
        assertThat(evidenceFeedbackScore(target.evidenceId())).isEqualTo(1.0);
        assertThat(chunkFeedbackScore(target.chunkId())).isEqualTo(1.0);
        assertThat(evidenceFeedbackScore(otherProject.evidenceId())).isZero();
        assertThat(chunkFeedbackScore(otherProject.chunkId())).isZero();
        verify(vectorSearchPort).applyChunkFeedback(List.of(target.chunkId()), 1.0);

        ArgumentCaptor<WorkbenchEvent> eventCaptor = ArgumentCaptor.forClass(WorkbenchEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        WorkbenchEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(WorkbenchEventType.FEEDBACK_APPLIED);
        assertThat(event.projectId()).isEqualTo(target.projectId());
        assertThat(event.answerId()).isEqualTo(target.answerId());
        assertThat(event.payload()).containsEntry("rating", "up")
                .containsEntry("evidenceSourceIds", List.of(target.evidenceId()))
                .containsEntry("updatedEvidenceSourceCount", 1)
                .containsEntry("updatedChunkCount", 1);
    }

    @Test
    void downFeedbackDecreasesLinkedEvidenceAndChunkScoresAndStoresReason() {
        FeedbackFixture target = createFixture("down project");

        feedbackService.recordProjectAnswerFeedback(
                target.projectId(),
                target.answerId(),
                new ProjectAnswerFeedbackRequest("down", "wrong citation", null, List.of(target.evidenceId()))
        );

        assertThat(answerFeedbackScores(target.answerId())).containsExactly(-1);
        assertThat(answerFeedbackNote(target.answerId())).isEqualTo("wrong citation");
        assertThat(evidenceFeedbackScore(target.evidenceId())).isEqualTo(-1.0);
        assertThat(chunkFeedbackScore(target.chunkId())).isEqualTo(-1.0);
    }

    @Test
    void feedbackWithoutEvidenceSourceIdsRecordsAnswerLevelOnly() {
        FeedbackFixture target = createFixture("answer only project");

        ProjectAnswerFeedbackResult result = feedbackService.recordProjectAnswerFeedback(
                target.projectId(),
                target.answerId(),
                new ProjectAnswerFeedbackRequest("up", null, "overall useful", List.of())
        );

        assertThat(result.updatedEvidenceSourceCount()).isZero();
        assertThat(result.updatedChunkCount()).isZero();
        assertThat(answerFeedbackScores(target.answerId())).containsExactly(1);
        assertThat(evidenceFeedbackScore(target.evidenceId())).isZero();
        assertThat(chunkFeedbackScore(target.chunkId())).isZero();
    }

    @Test
    void legacyMessageFeedbackAlsoRefreshesLocalVectorFeedback() {
        FeedbackFixture target = createFixture("legacy feedback project");

        feedbackService.recordMessageFeedback(42L, List.of(target.chunkId()), 1, "legacy helpful");

        assertThat(chunkFeedbackScore(target.chunkId())).isEqualTo(1.0);
        verify(vectorSearchPort).applyChunkFeedback(List.of(target.chunkId()), 1.0);
    }

    @Test
    void malformedEvidenceChunkIdDoesNotBreakAnswerFeedbackApplication() {
        FeedbackFixture target = createFixture("malformed citation project");
        jdbcTemplate.update("""
                update evidence_source
                set citation_meta_json = jsonb_set(citation_meta_json, '{chunkId}', '"999999999999999999999999999999"'::jsonb)
                where id = ?
                """, target.evidenceId());

        ProjectAnswerFeedbackResult result = feedbackService.recordProjectAnswerFeedback(
                target.projectId(),
                target.answerId(),
                new ProjectAnswerFeedbackRequest("up", null, "citation metadata is stale", List.of(target.evidenceId()))
        );

        assertThat(result.updatedEvidenceSourceCount()).isEqualTo(1);
        assertThat(result.updatedChunkCount()).isZero();
        assertThat(answerFeedbackScores(target.answerId())).containsExactly(1);
        assertThat(evidenceFeedbackScore(target.evidenceId())).isEqualTo(1.0);
        assertThat(chunkFeedbackScore(target.chunkId())).isZero();
    }

    @Test
    void projectAnswerFeedbackEndpointRejectsUnknownRating() throws Exception {
        FeedbackFixture target = createFixture("controller validation project");

        mockMvc.perform(post("/api/projects/{projectId}/answers/{answerId}/feedback", target.projectId(), target.answerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"meh","note":"unclear"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_RATING"));
    }

    @Test
    void projectAnswerFeedbackEndpointAppliesValidRating() throws Exception {
        FeedbackFixture target = createFixture("controller project");

        mockMvc.perform(post("/api/projects/{projectId}/answers/{answerId}/feedback", target.projectId(), target.answerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"up","evidenceSourceIds":["%s"],"note":"good source"}
                                """.formatted(target.evidenceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.rating").value("up"))
                .andExpect(jsonPath("$.updatedEvidenceSourceCount").value(1))
                .andExpect(jsonPath("$.updatedChunkCount").value(1));
    }

    @Test
    void projectAnswerFeedbackEndpointIgnoresNullEvidenceIds() throws Exception {
        FeedbackFixture target = createFixture("controller null evidence project");

        mockMvc.perform(post("/api/projects/{projectId}/answers/{answerId}/feedback", target.projectId(), target.answerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"up","evidenceSourceIds":[null,"%s"],"note":"good source"}
                                """.formatted(target.evidenceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedEvidenceSourceCount").value(1))
                .andExpect(jsonPath("$.updatedChunkCount").value(1));
    }

    private FeedbackFixture createFixture(String topic) {
        ProjectRecord project = projectRepository.createProject(topic, "");
        ResearchSessionRecord session = projectRepository.createSession(project.id(), topic + " session");
        String answerId = "ans-" + java.util.UUID.randomUUID();
        assistantAnswerRepository.insert(
                answerId,
                project.id(),
                session.id(),
                "question",
                "answer",
                "LOCAL_EVIDENCE",
                "SUFFICIENT"
        );

        long documentId = documentRepository.insert(topic + " document", topic + ".pdf", "/tmp/" + topic + ".pdf");
        documentChunkRepository.replaceChunks(documentId, List.of("Feedback linked paper evidence chunk."));
        ChunkRecord chunk = documentChunkRepository.findByDocumentId(documentId).get(0);
        String sourceId = documentRepository.insertSource(project.id(), "pdf", topic + " source", "/tmp/" + topic + ".pdf", "indexed").id();
        documentRepository.linkSourceIndexedDocument(project.id(), sourceId, documentId);

        EvidenceSourceRecord evidence = evidenceSourceRepository.insertPaperSources(
                project.id(),
                answerId,
                List.of(new RagChunk(chunk.id(), documentId, chunk.chunkIndex(), chunk.content(), 0.84)),
                Map.of(documentId, sourceId)
        ).get(0);
        return new FeedbackFixture(project.id(), answerId, evidence.id(), chunk.id());
    }

    private List<Integer> answerFeedbackScores(String answerId) {
        return jdbcTemplate.queryForList(
                "select feedback_score from answer_feedback where answer_id = ? order by created_at",
                Integer.class,
                answerId
        );
    }

    private String answerFeedbackNote(String answerId) {
        return jdbcTemplate.queryForObject(
                "select note from answer_feedback where answer_id = ? order by created_at desc limit 1",
                String.class,
                answerId
        );
    }

    private double evidenceFeedbackScore(String evidenceId) {
        return jdbcTemplate.queryForObject(
                "select feedback_score from evidence_source where id = ?",
                Double.class,
                evidenceId
        );
    }

    private double chunkFeedbackScore(long chunkId) {
        return jdbcTemplate.queryForObject(
                "select feedback_score from document_chunk where id = ?",
                Double.class,
                chunkId
        );
    }

    private record FeedbackFixture(String projectId, String answerId, String evidenceId, long chunkId) {
    }
}
