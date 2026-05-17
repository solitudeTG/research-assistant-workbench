package com.researchassistant.orchestrator;

import java.util.Optional;

@FunctionalInterface
public interface MultiAgentWorkflowIntentAdvisor {

    Optional<SemanticWorkflowAdvice> advise(
            String question,
            boolean allowWebSupplement,
            MultiAgentWorkflowDecision fallbackDecision
    );
}
