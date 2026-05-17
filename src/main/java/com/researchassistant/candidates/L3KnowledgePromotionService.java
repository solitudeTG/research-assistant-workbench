package com.researchassistant.candidates;

import com.researchassistant.knowledge.KnowledgeBoardRepository;
import com.researchassistant.memory.MemoryEntry;
import com.researchassistant.memory.MemoryRecallHit;
import com.researchassistant.memory.MemoryRecallResult;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class L3KnowledgePromotionService {

    static final int PROMOTION_HIT_THRESHOLD = 2;
    static final double MIN_PROMOTION_SCORE = 0.55;
    private static final int STALE_DAYS = 14;
    private static final String PROMOTION_REASON = "l3_memory_repeated_hit";

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeCandidateRepository candidateRepository;
    private final KnowledgeBoardRepository knowledgeBoardRepository;

    public L3KnowledgePromotionService(
            JdbcTemplate jdbcTemplate,
            KnowledgeCandidateRepository candidateRepository,
            KnowledgeBoardRepository knowledgeBoardRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.candidateRepository = candidateRepository;
        this.knowledgeBoardRepository = knowledgeBoardRepository;
    }

    @Transactional
    public PromotionOutcome promoteFromRecall(
            String projectId,
            String sessionId,
            String runId,
            String answerId,
            MemoryRecallResult memoryRecallResult) {
        if (projectId == null || projectId.isBlank()
                || memoryRecallResult == null
                || memoryRecallResult.hits() == null
                || memoryRecallResult.hits().isEmpty()) {
            return decayStaleAndDuplicate(projectId, runId);
        }

        PromotionOutcome outcome = decayStaleAndDuplicate(projectId, runId);
        Set<Long> processedMemoryIds = new HashSet<>();
        for (MemoryRecallHit hit : memoryRecallResult.hits()) {
            MemoryEntry entry = hit.entry();
            if (entry == null || !processedMemoryIds.add(entry.id())) {
                continue;
            }
            if (hit.finalScore() < MIN_PROMOTION_SCORE) {
                continue;
            }
            if (!memoryEntryExists(entry.id())) {
                continue;
            }
            String title = candidateTitle(entry);
            String statement = candidateStatement(entry);
            HitRegistration hitRegistration = recordPromotionHit(projectId, sessionId, runId, answerId, entry.id(), hit.finalScore());
            if (!hitRegistration.recorded()) {
                continue;
            }
            int hitCount = hitRegistration.hitCount();
            if (knowledgeBoardRepository.hasSimilarConfirmedProjectKnowledge(projectId, title, statement)) {
                boolean decayed = candidateRepository.findPendingL3PromotionCandidate(projectId, entry.id())
                        .flatMap(candidate -> candidateRepository.decayPendingL3PromotionCandidate(
                                projectId,
                                candidate.id(),
                                "duplicate_confirmed_knowledge",
                                runId
                        ))
                        .isPresent();
                if (decayed) {
                    outcome = outcome.withDecayed(outcome.decayedCount() + 1);
                }
                continue;
            }
            if (hitCount < PROMOTION_HIT_THRESHOLD) {
                continue;
            }
            var existing = candidateRepository.findPendingL3PromotionCandidate(projectId, entry.id());
            KnowledgeCandidateRecord candidate = existing
                    .map(value -> candidateRepository.updatePendingL3PromotionCandidate(
                            projectId,
                            value.id(),
                            hitCount,
                            hit.finalScore(),
                            PROMOTION_REASON,
                            runId
                    ))
                    .orElseGet(() -> candidateRepository.createL3PromotionCandidate(
                            projectId,
                            sessionId,
                            answerId,
                            title,
                            statement,
                            "confirmed_finding",
                            entry.id(),
                            hitCount,
                            hit.finalScore(),
                            PROMOTION_REASON,
                            runId
                    ));
            linkPromotionStateToCandidate(projectId, entry.id(), candidate.id());
            outcome = existing.isPresent()
                    ? outcome.withUpdated(outcome.updatedCount() + 1)
                    : outcome.withCreated(outcome.createdCount() + 1);
        }
        return outcome;
    }

    private boolean memoryEntryExists(long sourceMemoryEntryId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1
                    from memory_entry
                    where id = ?
                )
                """, Boolean.class, sourceMemoryEntryId);
        return Boolean.TRUE.equals(exists);
    }

    private PromotionOutcome decayStaleAndDuplicate(String projectId, String runId) {
        if (projectId == null || projectId.isBlank()) {
            return PromotionOutcome.empty();
        }
        PromotionOutcome outcome = PromotionOutcome.empty();
        OffsetDateTime staleBefore = OffsetDateTime.now().minusDays(STALE_DAYS);
        List<KnowledgeCandidateRecord> pending = candidateRepository.listPendingL3PromotionCandidates(projectId);
        for (KnowledgeCandidateRecord candidate : pending) {
            String decayReason = null;
            if (knowledgeBoardRepository.hasSimilarConfirmedProjectKnowledge(
                    projectId,
                    candidate.title(),
                    candidate.statement())) {
                decayReason = "duplicate_confirmed_knowledge";
            } else if (candidate.updatedAt() != null && candidate.updatedAt().isBefore(staleBefore)) {
                decayReason = "stale_l3_candidate";
            }
            if (decayReason != null) {
                candidateRepository.decayPendingL3PromotionCandidate(projectId, candidate.id(), decayReason, runId);
                outcome = outcome.withDecayed(outcome.decayedCount() + 1);
            }
        }
        return outcome;
    }

    private HitRegistration recordPromotionHit(
            String projectId,
            String sessionId,
            String runId,
            String answerId,
            long sourceMemoryEntryId,
            double score) {
        String hitIdentity = promotionHitIdentity(runId, answerId);
        if (hitIdentity.isBlank()) {
            return new HitRegistration(currentPromotionHitCount(projectId, sourceMemoryEntryId), false);
        }
        int inserted = jdbcTemplate.query("""
                insert into l3_memory_promotion_hit(
                    project_id, source_memory_entry_id, hit_identity, run_id, answer_id, score
                )
                values (?, ?, ?, ?, ?, ?)
                on conflict (project_id, source_memory_entry_id, hit_identity) do nothing
                returning 1
                """,
                resultSet -> resultSet.next() ? resultSet.getInt(1) : 0,
                projectId,
                sourceMemoryEntryId,
                hitIdentity,
                runId,
                answerId,
                score);
        if (inserted == 0) {
            return new HitRegistration(currentPromotionHitCount(projectId, sourceMemoryEntryId), false);
        }
        Integer count = jdbcTemplate.queryForObject("""
                insert into l3_memory_promotion_state(
                    project_id, source_memory_entry_id, hit_count, last_score,
                    last_session_id, last_run_id, last_answer_id, promotion_reason
                )
                values (?, ?, 1, ?, ?, ?, ?, ?)
                on conflict (project_id, source_memory_entry_id)
                do update set hit_count = l3_memory_promotion_state.hit_count + 1,
                              last_score = excluded.last_score,
                              last_session_id = excluded.last_session_id,
                              last_run_id = excluded.last_run_id,
                              last_answer_id = excluded.last_answer_id,
                              promotion_reason = excluded.promotion_reason
                returning hit_count
                """,
                Integer.class,
                projectId,
                sourceMemoryEntryId,
                score,
                sessionId,
                runId,
                answerId,
                PROMOTION_REASON);
        return new HitRegistration(count == null ? 0 : count, true);
    }

    private int currentPromotionHitCount(String projectId, long sourceMemoryEntryId) {
        Integer count = jdbcTemplate.queryForObject("""
                select coalesce(max(hit_count), 0)
                from l3_memory_promotion_state
                where project_id = ?
                  and source_memory_entry_id = ?
                """, Integer.class, projectId, sourceMemoryEntryId);
        return count == null ? 0 : count;
    }

    private String promotionHitIdentity(String runId, String answerId) {
        if (answerId != null && !answerId.isBlank()) {
            return "answer:" + answerId.trim();
        }
        if (runId != null && !runId.isBlank()) {
            return "run:" + runId.trim();
        }
        return "";
    }

    private void linkPromotionStateToCandidate(String projectId, long sourceMemoryEntryId, String candidateId) {
        jdbcTemplate.update("""
                update l3_memory_promotion_state
                set candidate_id = ?
                where project_id = ?
                  and source_memory_entry_id = ?
                """, candidateId, projectId, sourceMemoryEntryId);
    }

    private String candidateTitle(MemoryEntry entry) {
        String topic = clean(entry.topic());
        if (topic.isBlank()) {
            return "L3 recalled research memory";
        }
        return topic.length() <= 500 ? topic : topic.substring(0, 500).trim();
    }

    private String candidateStatement(MemoryEntry entry) {
        String summary = clean(entry.summary());
        if (!summary.isBlank()) {
            return summary;
        }
        if (entry.keyFindings() != null && !entry.keyFindings().isEmpty()) {
            return String.join(System.lineSeparator(), entry.keyFindings());
        }
        return candidateTitle(entry);
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public record PromotionOutcome(int createdCount, int updatedCount, int decayedCount) {
        static PromotionOutcome empty() {
            return new PromotionOutcome(0, 0, 0);
        }

        PromotionOutcome withCreated(int createdCount) {
            return new PromotionOutcome(createdCount, updatedCount, decayedCount);
        }

        PromotionOutcome withUpdated(int updatedCount) {
            return new PromotionOutcome(createdCount, updatedCount, decayedCount);
        }

        PromotionOutcome withDecayed(int decayedCount) {
            return new PromotionOutcome(createdCount, updatedCount, decayedCount);
        }
    }

    private record HitRegistration(int hitCount, boolean recorded) {
    }
}
