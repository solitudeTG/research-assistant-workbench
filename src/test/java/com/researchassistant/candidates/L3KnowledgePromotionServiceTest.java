package com.researchassistant.candidates;

import com.researchassistant.events.WorkbenchEventPublisher;
import com.researchassistant.knowledge.KnowledgeBoardRepository;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryEntryDraft;
import com.researchassistant.memory.MemoryEntryRepository;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.project.AssistantAnswerRepository;
import com.researchassistant.project.ProjectRepository;
import com.researchassistant.project.ResearchSessionRecord;
import com.researchassistant.support.PostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class L3KnowledgePromotionServiceTest extends PostgresIntegrationTest {

    @Autowired
    private L3KnowledgePromotionService promotionService;

    @Autowired
    private KnowledgeCandidateRepository candidateRepository;

    @Autowired
    private KnowledgeBoardRepository knowledgeBoardRepository;

    @Autowired
    private MemoryEntryRepository memoryEntryRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AssistantAnswerRepository assistantAnswerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkbenchEventPublisher eventPublisher;

    @Test
    void firstL3HitDoesNotCreateCandidateButSecondHitCreatesPendingCandidate() {
        ResearchSessionRecord session = createSession();
        MemoryEntry memory = memoryEntry("Adaptive beamforming", "Adaptive beamforming repeatedly guides antenna scheduling.");
        MemoryRecallResult recall = recall(memory, 0.72);
        String firstAnswerId = insertAnswer(session, "answer-first", "run-first");
        String secondAnswerId = insertAnswer(session, "answer-second", "run-second");

        L3KnowledgePromotionService.PromotionOutcome first = promotionService.promoteFromRecall(
                session.projectId(), session.id(), "run-first", firstAnswerId, recall);
        L3KnowledgePromotionService.PromotionOutcome second = promotionService.promoteFromRecall(
                session.projectId(), session.id(), "run-second", secondAnswerId, recall);

        assertThat(first.createdCount()).isZero();
        assertThat(second.createdCount()).isEqualTo(1);
        List<KnowledgeCandidateRecord> candidates = candidateRepository.listByProject(session.projectId());
        assertThat(candidates).hasSize(1);
        KnowledgeCandidateRecord candidate = candidates.get(0);
        assertThat(candidate.status()).isEqualTo("pending");
        assertThat(candidate.sourceKind()).isEqualTo("l3_memory");
        assertThat(candidate.sourceMemoryEntryId()).isEqualTo(memory.id());
        assertThat(candidate.promotionHitCount()).isEqualTo(2);
        assertThat(candidate.promotionLastScore()).isEqualTo(0.72);
        assertThat(candidate.promotionReason()).isEqualTo("l3_memory_repeated_hit");
        assertThat(candidate.evidenceSourceIds()).isEmpty();
        assertThat(knowledgeEntryCount(session.projectId())).isZero();
    }

    @Test
    void replayingSameRunAndAnswerDoesNotIncrementPromotionHitCount() {
        ResearchSessionRecord session = createSession();
        MemoryEntry memory = memoryEntry("Replay guard", "Repeated delivery of one answer should not promote a memory.");
        MemoryRecallResult recall = recall(memory, 0.74);
        String firstAnswerId = insertAnswer(session, "answer-replay", "run-replay");

        promotionService.promoteFromRecall(session.projectId(), session.id(), "run-replay", firstAnswerId, recall);
        L3KnowledgePromotionService.PromotionOutcome replay = promotionService.promoteFromRecall(
                session.projectId(),
                session.id(),
                "run-replay",
                firstAnswerId,
                recall
        );

        assertThat(replay.createdCount()).isZero();
        assertThat(candidateRepository.listByProject(session.projectId())).isEmpty();
        assertThat(promotionHitCount(session.projectId(), memory.id())).isEqualTo(1);

        L3KnowledgePromotionService.PromotionOutcome secondDistinctHit = promotionService.promoteFromRecall(
                session.projectId(),
                session.id(),
                "run-second",
                insertAnswer(session, "answer-second-distinct", "run-second"),
                recall
        );

        assertThat(secondDistinctHit.createdCount()).isEqualTo(1);
        assertThat(promotionHitCount(session.projectId(), memory.id())).isEqualTo(2);
    }

    @Test
    void repeatedL3HitsUpdatePendingCandidateInsteadOfCreatingDuplicates() {
        ResearchSessionRecord session = createSession();
        MemoryEntry memory = memoryEntry("Beamforming route", "Beamforming remains the strongest route.");
        promotionService.promoteFromRecall(session.projectId(), session.id(), "run-1", insertAnswer(session, "answer-1", "run-1"), recall(memory, 0.70));
        promotionService.promoteFromRecall(session.projectId(), session.id(), "run-2", insertAnswer(session, "answer-2", "run-2"), recall(memory, 0.72));

        L3KnowledgePromotionService.PromotionOutcome third = promotionService.promoteFromRecall(
                session.projectId(), session.id(), "run-3", insertAnswer(session, "answer-3", "run-3"), recall(memory, 0.81));

        assertThat(third.updatedCount()).isEqualTo(1);
        List<KnowledgeCandidateRecord> candidates = candidateRepository.listByProject(session.projectId());
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).promotionHitCount()).isEqualTo(3);
        assertThat(candidates.get(0).promotionLastScore()).isEqualTo(0.81);
    }

    @Test
    void duplicateConfirmedKnowledgeDecaysPendingL3CandidateAndDoesNotCreateKnowledgeEntry() {
        ResearchSessionRecord session = createSession();
        MemoryEntry memory = memoryEntry("Adaptive coding", "Adaptive coding should be the stable project direction.");
        promotionService.promoteFromRecall(session.projectId(), session.id(), "run-1", insertAnswer(session, "answer-1", "run-1"), recall(memory, 0.70));
        promotionService.promoteFromRecall(session.projectId(), session.id(), "run-2", insertAnswer(session, "answer-2", "run-2"), recall(memory, 0.70));
        KnowledgeCandidateRecord pending = candidateRepository.listByProject(session.projectId()).get(0);
        knowledgeBoardRepository.createEntry(
                session.projectId(),
                "confirmed_finding",
                pending.title(),
                pending.statement(),
                "confirmed",
                null,
                List.of()
        );

        L3KnowledgePromotionService.PromotionOutcome outcome = promotionService.promoteFromRecall(
                session.projectId(), session.id(), "run-3", insertAnswer(session, "answer-3", "run-3"), recall(memory, 0.75));

        assertThat(outcome.decayedCount()).isEqualTo(1);
        KnowledgeCandidateRecord decayed = candidateRepository.findByProject(session.projectId(), pending.id()).orElseThrow();
        assertThat(decayed.status()).isEqualTo("decayed");
        assertThat(decayed.decayReason()).isEqualTo("duplicate_confirmed_knowledge");
        assertThat(knowledgeEntryCount(session.projectId())).isEqualTo(1);
        assertThat(eventPublisher.readRunEventsAfter("run-3", null).stream()
                .anyMatch(event -> "candidate.decayed".equals(event.eventType().wireName()))).isTrue();
    }

    @Test
    void stalePendingL3CandidateDecaysWithoutUserIgnore() {
        ResearchSessionRecord session = createSession();
        MemoryEntry memory = memoryEntry("Stale route", "A route that was once useful but never confirmed.");
        promotionService.promoteFromRecall(session.projectId(), session.id(), "run-1", insertAnswer(session, "answer-1", "run-1"), recall(memory, 0.70));
        promotionService.promoteFromRecall(session.projectId(), session.id(), "run-2", insertAnswer(session, "answer-2", "run-2"), recall(memory, 0.70));
        KnowledgeCandidateRecord pending = candidateRepository.listByProject(session.projectId()).get(0);
        backdateCandidateUpdatedAt(pending.id(), 15);

        L3KnowledgePromotionService.PromotionOutcome outcome = promotionService.promoteFromRecall(
                session.projectId(),
                session.id(),
                "run-stale",
                null,
                new MemoryRecallResult("no current hit", List.of())
        );

        assertThat(outcome.decayedCount()).isEqualTo(1);
        KnowledgeCandidateRecord decayed = candidateRepository.findByProject(session.projectId(), pending.id()).orElseThrow();
        assertThat(decayed.status()).isEqualTo("decayed");
        assertThat(decayed.decayReason()).isEqualTo("stale_l3_candidate");
    }

    private void backdateCandidateUpdatedAt(String candidateId, int days) {
        jdbcTemplate.execute("alter table knowledge_candidate disable trigger trg_knowledge_candidate_set_updated_at");
        try {
            jdbcTemplate.update(
                    "update knowledge_candidate set updated_at = now() - (? * interval '1 day') where id = ?",
                    days,
                    candidateId
            );
        } finally {
            jdbcTemplate.execute("alter table knowledge_candidate enable trigger trg_knowledge_candidate_set_updated_at");
        }
    }

    private ResearchSessionRecord createSession() {
        var project = projectRepository.createProject("F024 project", "l3 promotion");
        return projectRepository.createSession(project.id(), "F024 session");
    }

    private MemoryEntry memoryEntry(String topic, String summary) {
        return memoryEntryRepository.append(new MemoryEntryDraft(
                null,
                "COMPACTION",
                topic,
                summary,
                List.of(summary),
                List.of(),
                List.of("adaptive", "beamforming", "coding"),
                1L,
                2L
        ));
    }

    private String insertAnswer(ResearchSessionRecord session, String answerId, String runId) {
        String uniqueAnswerId = answerId + "-" + UUID.randomUUID();
        assistantAnswerRepository.insert(
                uniqueAnswerId,
                session.projectId(),
                session.id(),
                runId,
                "question",
                "answer",
                "LOCAL_EVIDENCE",
                "SUFFICIENT"
        );
        return uniqueAnswerId;
    }

    private MemoryRecallResult recall(MemoryEntry entry, double score) {
        return new MemoryRecallResult("adaptive beamforming", List.of(new MemoryRecallHit(entry, score)));
    }

    private int knowledgeEntryCount(String projectId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from knowledge_entry
                where project_id = ?
                """, Integer.class, projectId);
        return count == null ? 0 : count;
    }

    private int promotionHitCount(String projectId, long memoryEntryId) {
        Integer count = jdbcTemplate.queryForObject("""
                select coalesce(max(hit_count), 0)
                from l3_memory_promotion_state
                where project_id = ?
                  and source_memory_entry_id = ?
                """, Integer.class, projectId, memoryEntryId);
        return count == null ? 0 : count;
    }
}
