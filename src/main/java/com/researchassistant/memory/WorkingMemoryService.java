package com.researchassistant.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkingMemoryService {

    private static final int SUMMARY_WINDOW = 6;
    private static final int AUTO_FLUSH_MESSAGE_THRESHOLD = 6;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemoryDepositService memoryDepositService;

    public WorkingMemoryService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            MemoryDepositService memoryDepositService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.memoryDepositService = memoryDepositService;
    }

    public WorkingMemory appendExchange(String sessionKey, String userQuestion, String assistantAnswer, String answerMode) {
        WorkingMemory current = chatSessionRepository.findOrCreate(sessionKey);

        chatMessageRepository.append(current.sessionId(), "USER", userQuestion, null);
        chatMessageRepository.append(current.sessionId(), "ASSISTANT", assistantAnswer, answerMode);

        WorkingMemory refreshed = rebuildWorkingMemory(sessionKey, userQuestion);
        if (shouldFlush(refreshed)) {
            memoryDepositService.flushIncremental(refreshed, "COMPACTION");
            return chatSessionRepository.findOrCreate(sessionKey);
        }
        return refreshed;
    }

    public WorkingMemory load(String sessionKey) {
        return chatSessionRepository.findOrCreate(sessionKey);
    }

    public WorkingMemory flushOnClose(String sessionKey) {
        WorkingMemory current = chatSessionRepository.findOrCreate(sessionKey);
        memoryDepositService.flushIncremental(current, "CLOSE_COMPENSATION");
        return chatSessionRepository.findOrCreate(sessionKey);
    }

    public List<WorkingMemory> listSessions() {
        return chatSessionRepository.listSessions();
    }

    public List<ChatMessageRecord> listMessages(String sessionKey) {
        WorkingMemory memory = chatSessionRepository.findOrCreate(sessionKey);
        return chatMessageRepository.findBySessionId(memory.sessionId());
    }

    private WorkingMemory rebuildWorkingMemory(String sessionKey, String currentTask) {
        WorkingMemory current = chatSessionRepository.findOrCreate(sessionKey);
        List<ChatMessageRecord> latestMessages = new ArrayList<>(chatMessageRepository.latestMessages(current.sessionId(), SUMMARY_WINDOW));
        Collections.reverse(latestMessages);

        String summary = latestMessages.stream()
                .map(ChatMessageRecord::content)
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
        List<String> salientFacts = deriveSalientFacts(latestMessages);
        List<String> compressedRounds = deriveCompressedRounds(latestMessages);

        chatSessionRepository.saveWorkingMemory(
                current.sessionId(),
                currentTask,
                summary,
                salientFacts,
                compressedRounds
        );

        return new WorkingMemory(
                current.sessionId(),
                sessionKey,
                currentTask,
                summary,
                salientFacts,
                compressedRounds,
                current.lastDepositedMessageId(),
                current.lastDepositAt(),
                chatMessageRepository.count(current.sessionId())
        );
    }

    private boolean shouldFlush(WorkingMemory memory) {
        return memory.messageCount() >= AUTO_FLUSH_MESSAGE_THRESHOLD
                && (memory.lastDepositAt() == null || memory.messageCount() % 4 == 0);
    }

    private List<String> deriveSalientFacts(List<ChatMessageRecord> latestMessages) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        for (ChatMessageRecord message : latestMessages) {
            if (!"ASSISTANT".equalsIgnoreCase(message.role())) {
                continue;
            }
            String trimmed = message.content().trim();
            if (!trimmed.isBlank()) {
                facts.add(trimmed.length() > 180 ? trimmed.substring(0, 180).trim() + "..." : trimmed);
            }
            if (facts.size() >= 4) {
                break;
            }
        }
        return facts.stream().toList();
    }

    private List<String> deriveCompressedRounds(List<ChatMessageRecord> latestMessages) {
        List<String> rounds = new ArrayList<>();
        String pendingQuestion = null;
        for (ChatMessageRecord message : latestMessages) {
            if ("USER".equalsIgnoreCase(message.role())) {
                pendingQuestion = message.content();
                continue;
            }
            if ("ASSISTANT".equalsIgnoreCase(message.role()) && pendingQuestion != null) {
                rounds.add("Q: " + pendingQuestion + " | A: " + message.content());
                pendingQuestion = null;
            }
        }
        if (rounds.size() > 3) {
            return rounds.subList(Math.max(0, rounds.size() - 3), rounds.size());
        }
        return rounds;
    }
}
