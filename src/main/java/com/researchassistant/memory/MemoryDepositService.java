package com.researchassistant.memory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MemoryDepositService {

    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("[。！？!?]|(?<=\\.)\\s+");

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemoryEntryRepository memoryEntryRepository;
    private final GlobalKnowledgeService globalKnowledgeService;

    public MemoryDepositService(ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository,
                                MemoryEntryRepository memoryEntryRepository,
                                GlobalKnowledgeService globalKnowledgeService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.memoryEntryRepository = memoryEntryRepository;
        this.globalKnowledgeService = globalKnowledgeService;
    }

    public void flushIncremental(WorkingMemory workingMemory, String sourceKind) {
        List<ChatMessageRecord> delta = chatMessageRepository.findSinceId(
                workingMemory.sessionId(),
                workingMemory.lastDepositedMessageId()
        );
        if (delta.isEmpty()) {
            return;
        }

        MemoryEntryDraft draft = buildDraft(workingMemory, delta, sourceKind);
        MemoryEntry saved = mergeOrAppend(draft);
        globalKnowledgeService.appendDailyMemory(saved);
        chatSessionRepository.updateDepositWatermark(
                workingMemory.sessionId(),
                draft.sourceMessageEndId(),
                OffsetDateTime.now()
        );
    }

    public MemoryEntry storeExplicitMemory(WorkingMemory workingMemory, String content) {
        String trimmed = content == null ? "" : content.trim();
        MemoryEntryDraft draft = new MemoryEntryDraft(
                workingMemory.sessionId(),
                "EXPLICIT",
                topicFrom(trimmed, workingMemory.currentTask()),
                trimmed,
                List.of(trimmed),
                List.of(),
                keywordsFrom(trimmed),
                workingMemory.lastDepositedMessageId(),
                Math.max(workingMemory.lastDepositedMessageId(), 0)
        );
        MemoryEntry saved = memoryEntryRepository.append(draft);
        globalKnowledgeService.appendDailyMemory(saved);
        return saved;
    }

    private MemoryEntry mergeOrAppend(MemoryEntryDraft draft) {
        if (draft.sessionId() != null) {
            java.util.Optional<MemoryEntry> latest = memoryEntryRepository.findLatestForSession(draft.sessionId());
            if (latest.isPresent() && shouldMerge(latest.get(), draft)) {
                return memoryEntryRepository.mergeIntoLatest(latest.get().id(), draft);
            }
        }
        return memoryEntryRepository.append(draft);
    }

    private boolean shouldMerge(MemoryEntry latest, MemoryEntryDraft draft) {
        return latest.topic().equalsIgnoreCase(draft.topic()) || latest.keywords().stream().anyMatch(draft.keywords()::contains);
    }

    private MemoryEntryDraft buildDraft(WorkingMemory workingMemory, List<ChatMessageRecord> delta, String sourceKind) {
        List<String> assistantMessages = delta.stream()
                .filter(message -> "ASSISTANT".equalsIgnoreCase(message.role()))
                .map(ChatMessageRecord::content)
                .toList();
        List<String> userQuestions = delta.stream()
                .filter(message -> "USER".equalsIgnoreCase(message.role()))
                .map(ChatMessageRecord::content)
                .toList();

        String summary = workingMemory.rollingSummary() == null || workingMemory.rollingSummary().isBlank()
                ? String.join(" | ", assistantMessages.stream().limit(3).toList())
                : workingMemory.rollingSummary();
        String topic = topicFrom(summary, workingMemory.currentTask());

        return new MemoryEntryDraft(
                workingMemory.sessionId(),
                sourceKind,
                topic,
                summary,
                extractKeyFindings(assistantMessages),
                extractOpenQuestions(userQuestions),
                keywordsFrom(topic + " " + summary),
                delta.get(0).id(),
                delta.get(delta.size() - 1).id()
        );
    }

    private List<String> extractKeyFindings(List<String> assistantMessages) {
        List<String> findings = new ArrayList<>();
        for (String message : assistantMessages) {
            for (String sentence : SENTENCE_SPLITTER.split(message)) {
                String trimmed = sentence.trim();
                if (!trimmed.isBlank() && trimmed.length() > 12) {
                    findings.add(trimmed);
                }
                if (findings.size() >= 4) {
                    return findings;
                }
            }
        }
        return findings;
    }

    private List<String> extractOpenQuestions(List<String> userQuestions) {
        return userQuestions.stream()
                .filter(question -> question.contains("?") || question.contains("？"))
                .limit(4)
                .toList();
    }

    private String topicFrom(String seed, String fallback) {
        String candidate = fallback;
        if (candidate == null || candidate.isBlank()) {
            candidate = seed;
        }
        candidate = candidate == null ? "Research Memory" : candidate.replaceAll("\\s+", " ").trim();
        if (candidate.length() > 120) {
            return candidate.substring(0, 120).trim();
        }
        return candidate.isBlank() ? "Research Memory" : candidate;
    }

    private List<String> keywordsFrom(String text) {
        LinkedHashSet<String> keywords = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+")
                .splitAsStream(text == null ? "" : text.toLowerCase(Locale.ROOT))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return keywords.stream().limit(6).toList();
    }
}
