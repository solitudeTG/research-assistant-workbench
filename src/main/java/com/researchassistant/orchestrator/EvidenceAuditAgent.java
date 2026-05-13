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

        List<String> unsupportedClaims = unsupportedClaims(packet);
        if (!unsupportedClaims.isEmpty()) {
            return new AuditVerdict(
                    "requires_revision",
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                    unsupportedClaims,
                    List.of("One or more claims are not directly supported by paper or web evidence."),
                    unsupportedClaims.stream()
                            .map(claim -> "Support or revise unsupported claim: " + claim)
                            .toList()
            );
        }

        if (packet.claims().isEmpty() && hasDraftText && !hasDirectSupport(draftAnswer.trim(), packet)) {
            return new AuditVerdict(
                    "requires_revision",
                    AnswerMode.LOCAL_WEAK_EVIDENCE.name(),
                    List.of(),
                    List.of("Draft answer is not directly supported by paper or web evidence."),
                    List.of("Support or revise unsupported draft answer.")
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

    private List<String> unsupportedClaims(ResearchPacket packet) {
        if (packet.claims().isEmpty()) {
            return List.of();
        }
        return packet.claims().stream()
                .filter(claim -> !hasDirectSupport(claim, packet))
                .toList();
    }

    private boolean hasDirectSupport(String text, ResearchPacket packet) {
        List<String> supportingEvidence = java.util.stream.Stream.concat(
                        packet.paperEvidence().stream(),
                        packet.webEvidence().stream()
                )
                .toList();
        return supportingEvidence.stream().anyMatch(evidence -> evidence.contains(text));
    }
}
