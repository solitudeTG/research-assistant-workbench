package com.researchassistant.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkingMemoryService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public WorkingMemoryService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public WorkingMemory appendExchange(String sessionKey, String userQuestion, String assistantAnswer, String answerMode) {
        WorkingMemory current = chatSessionRepository.findOrCreate(sessionKey);

        chatMessageRepository.append(current.sessionId(), "USER", userQuestion, null);
        chatMessageRepository.append(current.sessionId(), "ASSISTANT", assistantAnswer, answerMode);

        List<String> latest = new ArrayList<>(chatMessageRepository.latestContents(current.sessionId(), 4));
        Collections.reverse(latest);
        String summary = String.join(" | ", latest);

        chatSessionRepository.updateSummary(current.sessionId(), userQuestion, summary);
        return new WorkingMemory(
                current.sessionId(),
                sessionKey,
                userQuestion,
                summary,
                chatMessageRepository.count(current.sessionId())
        );
    }

    public WorkingMemory load(String sessionKey) {
        return chatSessionRepository.findOrCreate(sessionKey);
    }
}
