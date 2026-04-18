package com.researchassistant.orchestrator;

import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.rag.RagResult;

public interface PlanExecuteFacade {

    boolean shouldPlan(String question);

    PlanExecutionResult execute(String question,
                                WorkingMemory workingMemory,
                                MemoryRecallResult memoryRecallResult,
                                RagResult ragResult);
}
