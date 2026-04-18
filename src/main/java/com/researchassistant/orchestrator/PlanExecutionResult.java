package com.researchassistant.orchestrator;

import java.util.List;

public record PlanExecutionResult(
        List<String> steps,
        String answer
) {
}
