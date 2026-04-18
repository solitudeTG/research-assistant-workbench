package com.researchassistant.memory;

import org.springframework.stereotype.Service;

@Service
public class ExplicitMemoryService {

    private final GlobalKnowledgeService globalKnowledgeService;
    private final MemoryDepositService memoryDepositService;

    public ExplicitMemoryService(GlobalKnowledgeService globalKnowledgeService,
                                 MemoryDepositService memoryDepositService) {
        this.globalKnowledgeService = globalKnowledgeService;
        this.memoryDepositService = memoryDepositService;
    }

    public boolean isExplicitMemoryRequest(String question) {
        String normalized = question == null ? "" : question.trim();
        return normalized.startsWith("记住") || normalized.startsWith("请记住") || normalized.toLowerCase().startsWith("remember this");
    }

    public String store(WorkingMemory workingMemory, String question) {
        String normalized = question.replaceFirst("^(请)?记住[:：]?\\s*", "").trim();
        if (normalized.contains("偏好") || normalized.contains("我喜欢") || normalized.contains("我不喜欢") || normalized.contains("叫我")) {
            globalKnowledgeService.append(GlobalKnowledgeNoteType.USER, normalized);
            return "已记录到 USER.md，对后续回答偏好会持续生效。";
        }
        if (normalized.contains("风格") || normalized.contains("回答时") || normalized.contains("请始终")) {
            globalKnowledgeService.append(GlobalKnowledgeNoteType.SOUL, normalized);
            return "已记录到 SOUL.md，后续回答会遵循这条行为约束。";
        }
        if (normalized.contains("研究") || normalized.contains("阶段") || normalized.contains("下一步")) {
            globalKnowledgeService.append(GlobalKnowledgeNoteType.RESEARCH_STATE, normalized);
            return "已记录到 Research_state.md，后续研究编排会参考这条状态。";
        }
        MemoryEntry entry = memoryDepositService.storeExplicitMemory(workingMemory, normalized);
        return "已沉淀到每日记忆，主题为：" + entry.topic();
    }
}
