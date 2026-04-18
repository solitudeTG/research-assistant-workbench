package com.researchassistant.memory;

import com.researchassistant.rag.RetrievalTraceRepository;
import com.researchassistant.rag.RetrievalTraceView;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SessionWorkspaceService {

    private final WorkingMemoryService workingMemoryService;
    private final GlobalKnowledgeService globalKnowledgeService;
    private final RetrievalTraceRepository retrievalTraceRepository;

    public SessionWorkspaceService(WorkingMemoryService workingMemoryService,
                                   GlobalKnowledgeService globalKnowledgeService,
                                   RetrievalTraceRepository retrievalTraceRepository) {
        this.workingMemoryService = workingMemoryService;
        this.globalKnowledgeService = globalKnowledgeService;
        this.retrievalTraceRepository = retrievalTraceRepository;
    }

    public List<Map<String, Object>> listSessions() {
        return workingMemoryService.listSessions().stream()
                .map(memory -> Map.<String, Object>of(
                        "sessionId", memory.sessionId(),
                        "sessionKey", memory.sessionKey(),
                        "currentTask", defaultString(memory.currentTask()),
                        "rollingSummary", defaultString(memory.rollingSummary()),
                        "messageCount", memory.messageCount(),
                        "lastDepositAt", memory.lastDepositAt() == null ? "" : memory.lastDepositAt().toString()
                ))
                .toList();
    }

    public Map<String, Object> sessionDetail(String sessionKey) {
        WorkingMemory memory = workingMemoryService.load(sessionKey);
        return Map.of(
                "sessionId", memory.sessionId(),
                "sessionKey", memory.sessionKey(),
                "currentTask", defaultString(memory.currentTask()),
                "rollingSummary", defaultString(memory.rollingSummary()),
                "salientFacts", memory.salientFacts(),
                "compressedRounds", memory.compressedRounds(),
                "lastDepositedMessageId", memory.lastDepositedMessageId(),
                "lastDepositAt", memory.lastDepositAt() == null ? "" : memory.lastDepositAt().toString(),
                "messageCount", memory.messageCount(),
                "globalKnowledge", globalKnowledgeService.snapshot()
        );
    }

    public List<Map<String, Object>> sessionMessages(String sessionKey) {
        return workingMemoryService.listMessages(sessionKey).stream()
                .map(message -> Map.<String, Object>of(
                        "messageId", message.id(),
                        "role", message.role(),
                        "content", message.content(),
                        "answerMode", message.answerMode() == null ? "" : message.answerMode(),
                        "createdAt", message.createdAt()
                ))
                .toList();
    }

    public List<RetrievalTraceView> sessionTraces(String sessionKey) {
        WorkingMemory memory = workingMemoryService.load(sessionKey);
        return retrievalTraceRepository.findBySessionId(memory.sessionId());
    }

    public Map<String, Object> flushAndClose(String sessionKey) {
        WorkingMemory memory = workingMemoryService.flushOnClose(sessionKey);
        return Map.of(
                "sessionKey", memory.sessionKey(),
                "lastDepositedMessageId", memory.lastDepositedMessageId(),
                "lastDepositAt", memory.lastDepositAt() == null ? "" : memory.lastDepositAt().toString()
        );
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
