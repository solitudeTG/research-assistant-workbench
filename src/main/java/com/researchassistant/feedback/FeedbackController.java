package com.researchassistant.feedback;

import com.researchassistant.chat.dto.FeedbackRequest;
import com.researchassistant.orchestrator.FeedbackPort;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class FeedbackController {

    private final FeedbackPort feedbackPort;

    public FeedbackController(FeedbackPort feedbackPort) {
        this.feedbackPort = feedbackPort;
    }

    @PostMapping("/{messageId}/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(@PathVariable long messageId,
                                                              @RequestBody FeedbackRequest request) {
        feedbackPort.recordMessageFeedback(messageId, request.chunkIds(), request.score(), request.note());
        return ResponseEntity.ok(Map.of(
                "messageId", messageId,
                "score", request.score(),
                "chunkCount", request.chunkIds() == null ? 0 : request.chunkIds().size(),
                "status", "RECORDED"
        ));
    }
}
