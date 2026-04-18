package com.researchassistant.orchestrator;

import com.researchassistant.memory.MemoryRecallResult;

public interface MemoryRecallPort {

    MemoryRecallResult recall(long sessionId, String query, int limit);
}
