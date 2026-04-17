package com.researchassistant.memory;

public record WorkingMemory(
        long sessionId,
        String sessionKey,
        String currentTask,
        String rollingSummary,
        int messageCount
) {
}
