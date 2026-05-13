package com.researchassistant.orchestrator;

import com.researchassistant.evidence.AnswerMode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class EvidenceAuditAgent {

    public AuditVerdict audit(String question, String draftAnswer, ResearchPacket packet) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(packet, "packet");

        boolean hasEvidence = !packet.paperEvidence().isEmpty() || !packet.webEvidence().isEmpty();
        boolean hasDraftText = draftAnswer != null && !draftAnswer.isBlank();
        if (!hasEvidence && (!packet.claims().isEmpty() || hasDraftText)) {
            return new AuditVerdict(
                    "requires_revision",
                    AnswerMode.REFUSAL.name(),
                    packet.claims(),
                    List.of("No paper or web evidence supports the drafted output."),
                    List.of("Remove or qualify claims until paper or web evidence supports them.")
            );
        }

        if (!packet.evidenceGaps().isEmpty()) {
            return new AuditVerdict(
                    "pass_with_cautions",
                    packet.recommendedAnswerMode(),
                    List.of(),
                    List.of(),
                    packet.evidenceGaps().stream()
                            .map(gap -> "Address evidence gap: " + gap)
                            .toList()
            );
        }

        return new AuditVerdict(
                "pass",
                packet.recommendedAnswerMode(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
