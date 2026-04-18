package com.researchassistant.memory;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkingMemory(
        long sessionId,
        String sessionKey,
        String currentTask,
        String rollingSummary,
        List<String> salientFacts,
        List<String> compressedRounds,
        long lastDepositedMessageId,
        OffsetDateTime lastDepositAt,
        int messageCount
) {
}
