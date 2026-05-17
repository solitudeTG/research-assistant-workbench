package com.researchassistant.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchassistant.evidence.EvidenceSourceRecord;
import com.researchassistant.evidence.EvidenceSourceRepository;
import com.researchassistant.events.WorkbenchEvent;
import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.events.WorkbenchEventType;
import com.researchassistant.ingest.DocumentChunkRepository;
import com.researchassistant.orchestrator.FeedbackPort;
import com.researchassistant.project.AssistantAnswerRepository;
import com.researchassistant.rag.VectorSearchPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService implements FeedbackPort {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentChunkRepository documentChunkRepository;
    private final EvidenceSourceRepository evidenceSourceRepository;
    private final AssistantAnswerRepository assistantAnswerRepository;
    private final WorkbenchEventPublisher eventPublisher;
    private final VectorSearchPort vectorSearchPort;
    private final ObjectMapper objectMapper;

    public FeedbackService(JdbcTemplate jdbcTemplate,
                           DocumentChunkRepository documentChunkRepository,
                           EvidenceSourceRepository evidenceSourceRepository,
                           AssistantAnswerRepository assistantAnswerRepository,
                           WorkbenchEventPublisher eventPublisher,
                           VectorSearchPort vectorSearchPort,
                           ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentChunkRepository = documentChunkRepository;
        this.evidenceSourceRepository = evidenceSourceRepository;
        this.assistantAnswerRepository = assistantAnswerRepository;
        this.eventPublisher = eventPublisher;
        this.vectorSearchPort = vectorSearchPort;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void recordMessageFeedback(long messageId, List<Long> chunkIds, int score, String note) {
        List<Long> safeChunkIds = chunkIds == null ? List.of() : chunkIds;
        jdbcTemplate.update("""
                insert into message_feedback(message_id, feedback_score, note, chunk_ids_json)
                values (?, ?, ?, ?::jsonb)
                """,
                messageId,
                score,
                note,
                toJson(safeChunkIds)
        );
        for (Long chunkId : safeChunkIds) {
            documentChunkRepository.applyFeedback(chunkId, score);
        }
        vectorSearchPort.applyChunkFeedback(safeChunkIds, score);
    }

    @Transactional
    public ProjectAnswerFeedbackResult recordProjectAnswerFeedback(
            String projectId,
            String answerId,
            ProjectAnswerFeedbackRequest request) {
        int feedbackScore = feedbackScore(request == null ? null : request.rating());
        String rating = feedbackScore > 0 ? "up" : "down";
        String reason = reason(request, rating);
        List<String> evidenceSourceIds = evidenceSourceIdsForFeedback(projectId, answerId, request, rating, reason);

        jdbcTemplate.update("""
                insert into answer_feedback(id, project_id, answer_id, feedback_score, note)
                values (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                projectId,
                answerId,
                feedbackScore,
                request == null ? null : request.effectiveNote()
        );

        EvidenceSourceRepository.FeedbackApplication application =
                evidenceSourceRepository.applyFeedbackToProjectEvidence(
                        projectId,
                        answerId,
                        evidenceSourceIds,
                        feedbackScore
                );

        ProjectAnswerFeedbackResult result = new ProjectAnswerFeedbackResult(
                projectId,
                answerId,
                rating,
                reason,
                feedbackScore,
                application.updatedEvidenceSourceCount(),
                application.updatedChunkCount(),
                "APPLIED"
        );
        vectorSearchPort.applyChunkFeedback(application.appliedChunkIds(), feedbackScore);
        AssistantAnswerRepository.AssistantAnswerContext answerContext =
                assistantAnswerRepository.findContext(projectId, answerId).orElse(null);
        publishFeedbackApplied(result, application.appliedEvidenceSourceIds(), answerContext);
        return result;
    }

    private int feedbackScore(String rating) {
        if ("up".equals(rating)) {
            return 1;
        }
        if ("down".equals(rating)) {
            return -1;
        }
        throw new InvalidFeedbackRatingException(rating);
    }

    private void publishFeedbackApplied(
            ProjectAnswerFeedbackResult result,
            List<String> evidenceSourceIds,
            AssistantAnswerRepository.AssistantAnswerContext answerContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rating", result.rating());
        payload.put("reason", result.reason());
        payload.put("feedbackScore", result.feedbackScore());
        payload.put("appliedEvidenceSourceIds", evidenceSourceIds == null ? List.of() : evidenceSourceIds);
        payload.put("evidenceSourceIds", evidenceSourceIds == null ? List.of() : evidenceSourceIds);
        payload.put("updatedEvidenceSourceCount", result.updatedEvidenceSourceCount());
        payload.put("updatedChunkCount", result.updatedChunkCount());

        eventPublisher.publish(new WorkbenchEvent(
                null,
                WorkbenchEventType.FEEDBACK_APPLIED,
                result.projectId(),
                answerContext == null ? null : answerContext.sessionId(),
                answerContext == null ? null : answerContext.runId(),
                "feedback-service",
                0,
                null,
                result.answerId(),
                null,
                null,
                payload
        ));
    }

    private List<String> evidenceSourceIdsForFeedback(
            String projectId,
            String answerId,
            ProjectAnswerFeedbackRequest request,
            String rating,
            String reason) {
        List<String> requestedEvidenceSourceIds = request == null ? List.of() : request.evidenceSourceIds();
        if (!requestedEvidenceSourceIds.isEmpty()) {
            return requestedEvidenceSourceIds;
        }
        if (!shouldInternallyAttributeEvidence(rating, reason)) {
            return List.of();
        }
        return evidenceSourceRepository.findByAnswer(projectId, answerId).stream()
                .map(EvidenceSourceRecord::id)
                .toList();
    }

    private boolean shouldInternallyAttributeEvidence(String rating, String reason) {
        if ("up".equals(rating)) {
            return true;
        }
        return "citation_wrong".equals(reason)
                || "evidence_not_relevant".equals(reason)
                || "not_relevant".equals(reason);
    }

    private String reason(ProjectAnswerFeedbackRequest request, String rating) {
        if (request != null && request.reason() != null && !request.reason().isBlank()) {
            return request.reason();
        }
        return "up".equals(rating) ? "helpful" : "needs_correction";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize feedback payload", e);
        }
    }
}
