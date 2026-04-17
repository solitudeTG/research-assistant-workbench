package com.researchassistant.chat;

import com.researchassistant.chat.dto.ChatRequest;
import com.researchassistant.chat.dto.ChatResponse;
import com.researchassistant.orchestrator.SupervisorService;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private final SupervisorService supervisorService;
    private final Executor streamingExecutor;

    public ChatStreamController(
            SupervisorService supervisorService,
            @Qualifier("streamingExecutor") Executor streamingExecutor) {
        this.supervisorService = supervisorService;
        this.streamingExecutor = streamingExecutor;
    }

    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestParam String sessionKey,
            @RequestParam String question,
            @RequestParam(required = false) Long documentId) {
        SseEmitter emitter = new SseEmitter(30_000L);

        streamingExecutor.execute(() -> {
            try {
                ChatResponse response = supervisorService.answer(new ChatRequest(
                        sessionKey,
                        question,
                        documentId == null ? Collections.emptyList() : List.of(documentId)
                ));
                for (String token : Arrays.asList(response.answer().split(" "))) {
                    emitter.send(SseEmitter.event().name("message").data(token));
                }
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });

        return emitter;
    }
}
