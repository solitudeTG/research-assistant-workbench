package com.researchassistant.orchestrator;

import com.researchassistant.evidence.ProjectEvidenceScope;
import com.researchassistant.memory.GlobalKnowledgeSnapshot;
import com.researchassistant.memory.WorkingMemory;

public record ProjectAgentRequest(
        String projectId,
        String sessionId,
        String runId,
        String messageId,
        String answerId,
        String question,
        WorkingMemory memory,
        GlobalKnowledgeSnapshot globalKnowledge,
        ProjectEvidenceScope evidenceScope,
        boolean allowWebSupplement
) {
}
