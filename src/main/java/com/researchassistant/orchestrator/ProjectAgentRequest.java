package com.researchassistant.orchestrator;

import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.knowledge.KnowledgeEntryRecord;
import com.researchassistant.memory.GlobalKnowledgeSnapshot;
import com.researchassistant.memory.WorkingMemory;
import java.util.List;

public record ProjectAgentRequest(
        String projectId,
        String sessionId,
        String runId,
        String messageId,
        String answerId,
        String question,
        WorkingMemory memory,
        GlobalKnowledgeSnapshot globalKnowledge,
        List<KnowledgeEntryRecord> projectKnowledge,
        ProjectEvidenceScope evidenceScope,
        boolean allowWebSupplement
) {
    public ProjectAgentRequest {
        projectKnowledge = projectKnowledge == null ? List.of() : List.copyOf(projectKnowledge);
    }

    public ProjectAgentRequest(
            String projectId,
            String sessionId,
            String runId,
            String messageId,
            String answerId,
            String question,
            WorkingMemory memory,
            GlobalKnowledgeSnapshot globalKnowledge,
            ProjectEvidenceScope evidenceScope,
            boolean allowWebSupplement) {
        this(
                projectId,
                sessionId,
                runId,
                messageId,
                answerId,
                question,
                memory,
                globalKnowledge,
                List.of(),
                evidenceScope,
                allowWebSupplement
        );
    }
}
