package com.researchassistant.orchestrator;

import com.researchassistant.memory.MemoryRecallResult;
import com.researchassistant.memory.WorkingMemory;
import com.researchassistant.rag.RagResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlanExecuteFacade implements PlanExecuteFacade {

    private final ChatClient chatClient;

    public DefaultPlanExecuteFacade(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public boolean shouldPlan(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return normalized.contains("plan")
                || normalized.contains("roadmap")
                || normalized.contains("step by step")
                || normalized.contains("研究计划")
                || normalized.contains("路线")
                || normalized.contains("方案")
                || normalized.contains("报告")
                || normalized.contains("综述");
    }

    @Override
    public PlanExecutionResult execute(String question,
                                       WorkingMemory workingMemory,
                                       MemoryRecallResult memoryRecallResult,
                                       RagResult ragResult) {
        List<String> steps = heuristicSteps(question, ragResult);
        String answer = chatClient.prompt()
                .system("You are a research planning assistant. Produce a concise plan and execution notes grounded in provided evidence when available.")
                .user("Question: " + question
                        + "\n\nWorking memory:\n" + safe(workingMemory.rollingSummary())
                        + "\n\nMemory context:\n" + (memoryRecallResult == null ? "" : memoryRecallResult.contextBlock())
                        + "\n\nPaper evidence:\n" + evidenceBlock(ragResult)
                        + "\n\nPlan steps:\n" + String.join("\n", steps))
                .call()
                .content();
        return new PlanExecutionResult(steps, answer);
    }

    private List<String> heuristicSteps(String question, RagResult ragResult) {
        List<String> steps = new ArrayList<>();
        steps.add("Clarify the research objective and scope from the user request.");
        if (ragResult != null && !ragResult.chunks().isEmpty()) {
            steps.add("Extract the strongest grounded evidence from indexed papers and verify coverage.");
        }
        steps.add("Organize findings into method, evidence, risks, and next actions.");
        steps.add("Produce a concrete execution plan with checkpoints and follow-up questions.");
        return steps;
    }

    private String evidenceBlock(RagResult ragResult) {
        if (ragResult == null || ragResult.chunks().isEmpty()) {
            return "(none)";
        }
        return ragResult.chunks().stream()
                .map(chunk -> "- [" + chunk.documentId() + "/" + chunk.chunkIndex() + "] " + chunk.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(none)");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }
}
