package com.researchassistant.feedback;

import com.researchassistant.chat.dto.FeedbackRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/messages/{messageId}/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(@PathVariable long messageId,
                                                              @RequestBody FeedbackRequest request) {
        feedbackService.recordMessageFeedback(messageId, request.chunkIds(), request.score(), request.note());
        return ResponseEntity.ok(Map.of(
                "messageId", messageId,
                "score", request.score(),
                "chunkCount", request.chunkIds() == null ? 0 : request.chunkIds().size(),
                "status", "RECORDED"
        ));
    }

    @PostMapping("/projects/{projectId}/answers/{answerId}/feedback")
    public ResponseEntity<ProjectAnswerFeedbackResult> submitProjectAnswerFeedback(
            @PathVariable String projectId,
            @PathVariable String answerId,
            @RequestBody ProjectAnswerFeedbackRequest request) {
        return ResponseEntity.ok(feedbackService.recordProjectAnswerFeedback(projectId, answerId, request));
    }

    @ExceptionHandler(InvalidFeedbackRatingException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFeedbackRating(InvalidFeedbackRatingException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "INVALID_FEEDBACK_RATING",
                "message", exception.getMessage()
        ));
    }
}
