package com.researchassistant.orchestrator;

import java.util.List;

public interface FeedbackPort {

    void recordMessageFeedback(long messageId, List<Long> chunkIds, int score, String note);
}
